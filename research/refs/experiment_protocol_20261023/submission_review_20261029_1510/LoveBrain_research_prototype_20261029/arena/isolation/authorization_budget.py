# -*- coding: utf-8 -*-
"""授权级预算权威（V14.1 收口 §2 / 复核 v3 执行书 §2A 一次修完）。

核心原则：预算权威属于整个 authorization_id，不属于 run 目录、代理实例、
release 显示名或任何进程内计数器。

复核 v3 §2A 修复的七个同族绕过：
1. 无预算代理派发        → TrustedProxy 对非 mock 适配器强制 budget
                          （本模块提供 has_authority 校验；接线见 trusted_controller）
2. 关闭后旧实例继续派发   → reserve/ensure_batch 在【同一把锁内】重读账本并
                          核对 authorization_closed（不信任实例缓存 closed）
3. 跨实例重复 attempt_id  → 唯一性在锁内按权威账本全量核对（不信实例缓存）
4. 重建对象放大额度       → max_attempts 首次记录后持久不可变绑定
                          （账本 authorization_scope 事件；重建必须逐字匹配）
5. 坏 JSON 行静默略过     → 账本不可读/损坏 = LedgerCorrupt → 停批（不清零、
                          不当作未消费）
6. 陈旧实例 ensure_batch 放行 → ensure_batch 同样锁内重读权威账
7. CLI 省略授权           → 入口强制（iso_run + TrustedProxy 双层）

不变量（复核 v3 §2A 要求）：
- 同一事务内（同一把锁内）重读并验证：授权身份、不可变范围/额度、关闭态、
  重复 attempt、账本完整性 → 先持久占额（fsync）→ 才允许派发。
- 记录失败/完成配对；消耗不退款。
- 更换 run、进程、版本、授权文件路径均不能获得新额度（账本键=authorization_id）。
- 授权关闭由可信批次收口显式调用；episode 结束不关闭整个授权（复核 v3 §2A）。
"""

import json
import os
import threading
import time
import uuid


class BudgetRefused(RuntimeError):
    """预算拒绝（超限/关闭/重复 attempt/落盘失败）。派发方必须停止。"""


class LedgerCorrupt(RuntimeError):
    """授权账本不可读/损坏。必须停批——不得当作未消费清零处理。"""


def validate_authorization_doc(aj, expected_endpoint=None, expected_model=None,
                               expected_temperature=None):
    """授权单文件严格校验（复核 v3 §2A 接线问题 1）。

    - authorized 必须是布尔 true；未知 status / 非布尔 / 缺 ID / 非法额度拒绝
    - real_endpoint 的 api_base/model/temperature 必须存在且与调用方出站配置
      逐字一致（端点/model/temperature 由授权单绑定，不是 CLI 想用什么就用什么）
    返回规范化的 authorization dict；任何不合法抛 BudgetRefused。
    """
    if not isinstance(aj, dict):
        raise BudgetRefused("授权单必须是 JSON 对象")
    aid = aj.get("authorization_id")
    if not aid or not isinstance(aid, str) or not aid.strip():
        raise BudgetRefused("授权单缺 authorization_id（或缺合法 ID）")
    if aj.get("authorized") is not True:
        raise BudgetRefused(
            "授权 %s 不处于 authorized=true（实为 %r，status=%r）——"
            "关闭/超额授权不得复活" % (aid, aj.get("authorized"),
                                      aj.get("status")))
    status = aj.get("status")
    if status not in (None, "ACTIVE"):
        raise BudgetRefused(
            "授权 %s status=%r 非未知合法态（只接受 ACTIVE/缺省）"
            % (aid, status))
    re_ = aj.get("real_endpoint")
    if not isinstance(re_, dict):
        raise BudgetRefused("授权 %s 缺 real_endpoint 对象" % aid)
    max_attempts = re_.get("max_attempts")
    if not isinstance(max_attempts, int) or isinstance(max_attempts, bool) \
            or max_attempts < 1 or max_attempts > 100000:
        raise BudgetRefused(
            "授权 %s max_attempts=%r 非法（须正整数）" % (aid, max_attempts))
    # 端点/model/temperature 绑定：授权单必须写明，且与实际出站配置一致
    ep_api = re_.get("api_base")
    ep_model = re_.get("model")
    ep_temp = re_.get("temperature")
    if not ep_api or not ep_model:
        raise BudgetRefused(
            "授权 %s real_endpoint 缺 api_base/model（出站配置须由授权单绑定）"
            % aid)
    if expected_endpoint is not None and ep_api != expected_endpoint:
        raise BudgetRefused(
            "授权 %s 端点绑定不符: 授权单 %s != 实际 %s"
            % (aid, ep_api, expected_endpoint))
    if expected_model is not None and ep_model != expected_model:
        raise BudgetRefused(
            "授权 %s model 绑定不符: 授权单 %s != 实际 %s"
            % (aid, ep_model, expected_model))
    if expected_temperature is not None and ep_temp is not None \
            and abs(float(ep_temp) - float(expected_temperature)) > 1e-9:
        raise BudgetRefused(
            "授权 %s temperature 绑定不符: 授权单 %s != 实际 %s"
            % (aid, ep_temp, expected_temperature))
    return {"authorization_id": aid, "max_attempts": max_attempts,
            "api_base": ep_api, "model": ep_model,
            "temperature": ep_temp,
            "status": status or "ACTIVE"}


if os.name == "nt":
    import msvcrt

    def _try_lock(fd):
        msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)

    def _unlock(fd):
        msvcrt.locking(fd, msvcrt.LK_UNLCK, 1)
else:
    import fcntl

    def _try_lock(fd):
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def _unlock(fd):
        fcntl.flock(fd, fcntl.LOCK_UN)


class _FileLock(object):
    """跨进程/跨线程独占锁：字节范围锁（Windows msvcrt.locking / POSIX
    flock），不删除锁文件。

    复核 v3 实测：O_CREAT|O_EXCL + os.remove 方案在 Windows 上有删除竞态
    （AV/沙箱扫描器短暂持有句柄 → delete-pending → 锁文件名被占死，
    全部 contenders 永久 FileExistsError）。字节范围锁由 OS 持有：进程
    死亡即自动释放，无删除竞态、无陈旧检测需求。锁文件残留无害。"""

    def __init__(self, path, timeout_s=60.0):
        self.path = path
        self.timeout_s = timeout_s
        self._fd = None

    def __enter__(self):
        deadline = time.time() + self.timeout_s
        fd = None
        while True:
            try:
                if fd is None:
                    # open 也可能瞬态失败（Windows 扫描器短暂持有新文件）
                    fd = os.open(self.path, os.O_CREAT | os.O_RDWR)
                _try_lock(fd)
                self._fd = fd
                return self
            except OSError:
                if fd is not None:
                    try:
                        os.close(fd)
                    except OSError:
                        pass
                    fd = None
                if time.time() > deadline:
                    raise BudgetRefused("预算锁超时: %s" % self.path)
                time.sleep(0.02)

    def __exit__(self, *exc):
        if self._fd is not None:
            try:
                _unlock(self._fd)
            except OSError:
                pass
            os.close(self._fd)
            self._fd = None
        return False


class AuthorizationBudget(object):
    """一个授权单的持久预算账（JSONL，授权层，独立于任何 run 目录）。

    所有判定（used/closed/唯一性/额度上限）以锁内重读的权威账本为准；
    实例缓存只作展示。账本损坏抛 LedgerCorrupt → 调用方必须停批。
    """

    def __init__(self, ledger_path, authorization_id, max_attempts,
                 status=None, scope=None):
        self.ledger_path = ledger_path
        self.authorization_id = authorization_id
        self.max_attempts = int(max_attempts)
        self._scope = dict(scope) if scope else None
        self.lock_path = ledger_path + ".lock"
        os.makedirs(os.path.dirname(ledger_path), exist_ok=True)
        # 构造即锁内核账（损坏即抛；范围冲突即抛——重建放大额度在此拦截）
        with _FileLock(self.lock_path):
            recs = self._read()
            self._check_scope(recs)
        self._refresh_cache(recs)
        if status == "CLOSED":
            # 授权单侧显式关闭状态：预算层同样视为关闭（不得复活）
            self.closed = True

    # ---------- 账本 IO ----------
    def _read(self):
        """读授权账本。任何不可读/坏 JSON 行 → LedgerCorrupt（停批纪律，
        复核 v3 §2A 反例 6：坏行静默略过 = 额度被当成未消费）。"""
        if not os.path.isfile(self.ledger_path):
            return []
        try:
            with open(self.ledger_path, "r", encoding="utf-8") as f:
                lines = f.readlines()
        except OSError as e:
            raise LedgerCorrupt("授权账本不可读: %s (%s)"
                                % (self.ledger_path, e))
        recs = []
        for i, line in enumerate(lines, 1):
            line = line.strip()
            if not line:
                continue
            try:
                r = json.loads(line)
            except json.JSONDecodeError as e:
                raise LedgerCorrupt(
                    "授权账本第 %d 行损坏（停批，不得当作未消费）: %s"
                    % (i, e))
            if not isinstance(r, dict) or not r.get("kind"):
                raise LedgerCorrupt("授权账本第 %d 行缺 kind 字段" % i)
            recs.append(r)
        return recs

    def _append_nolock(self, rec):
        """持锁追加（fsync）；失败抛 OSError → 调用方零派发。"""
        with open(self.ledger_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False,
                               sort_keys=True) + "\n")
            f.flush()
            os.fsync(f.fileno())

    def _append(self, rec):
        with _FileLock(self.lock_path):
            self._append_nolock(rec)

    # ---------- 权威账推导 ----------
    @staticmethod
    def _derive(recs, authorization_id):
        used = sum(1 for r in recs
                   if r.get("kind") == "attempt_started"
                   and r.get("authorization_id") == authorization_id)
        attempt_ids = {r.get("attempt_id") for r in recs
                       if r.get("kind") == "attempt_started"
                       and r.get("authorization_id") == authorization_id}
        closed_recs = [r for r in recs
                       if r.get("kind") == "authorization_closed"
                       and r.get("authorization_id") == authorization_id]
        scope_recs = [r for r in recs
                      if r.get("kind") == "authorization_scope"
                      and r.get("authorization_id") == authorization_id]
        return used, attempt_ids, bool(closed_recs), \
            (closed_recs[-1].get("reason") if closed_recs else None), scope_recs

    def _refresh_cache(self, recs):
        used, attempt_ids, closed, close_reason, scope_recs = \
            self._derive(recs, self.authorization_id)
        self.used = used
        self._attempt_ids = attempt_ids
        self.closed = closed
        self.close_reason = close_reason
        self._ledger_scope = scope_recs[-1] if scope_recs else None

    def _check_scope(self, recs):
        """额度上限不可变绑定（复核 v3 §2A 反例 5）：
        账本已有 authorization_scope 事件时，本对象声明的 max_attempts
        必须逐字匹配；不符即拒（放大额度 = 换个对象声明而已，不认）。"""
        _u, _a, closed, _cr, scope_recs = self._derive(
            recs, self.authorization_id)
        if closed:
            raise BudgetRefused(
                "授权 %s 已关闭（%s），不得复用"
                % (self.authorization_id, _cr or "closed"))
        if scope_recs:
            bound = scope_recs[-1].get("max_attempts")
            if bound != self.max_attempts:
                raise BudgetRefused(
                    "授权 %s 额度上限不可变: 账本绑定 %s，本对象声明 %s —— "
                    "重建对象放大额度被拒绝"
                    % (self.authorization_id, bound, self.max_attempts))
            bound_scope = scope_recs[-1].get("scope")
            if self._scope is not None and bound_scope != self._scope:
                raise BudgetRefused(
                    "授权 %s 范围不可变: 账本绑定 %r，本对象声明 %r"
                    % (self.authorization_id, bound_scope, self._scope))

    def _ensure_scope_recorded(self, recs):
        """锁内调用：账本尚无 scope 事件时，把本对象的 max_attempts/scope
        作为不可变绑定落账（复核 v3 §2A 反例 5 的根治——首次使用即绑定，
        之后任何"重建对象放大额度"都在 _check_scope 被拒）。"""
        if self._ledger_scope is not None:
            return
        _u, _a, closed, _cr, scope_recs = self._derive(
            recs, self.authorization_id)
        if not scope_recs:
            self._append_nolock({
                "kind": "authorization_scope", "ts": _now(),
                "authorization_id": self.authorization_id,
                "max_attempts": self.max_attempts,
                "scope": self._scope})
            self._ledger_scope = {"max_attempts": self.max_attempts,
                                  "scope": self._scope}

    # ---------- 对外接口 ----------
    def remaining(self):
        return max(0, self.max_attempts - self.used)

    def ensure_batch(self, n, purpose="episode_batch"):
        """整批预检：锁内重读权威账（陈旧实例缓存不作数，复核 v3 §2A 反例 7）。"""
        with _FileLock(self.lock_path):
            recs = self._read()
            self._check_scope(recs)
            self._refresh_cache(recs)
            self._ensure_scope_recorded(recs)
            if self.closed:
                self._append_nolock({
                    "kind": "batch_rejected", "ts": _now(),
                    "authorization_id": self.authorization_id,
                    "purpose": purpose, "reason": "authorization_closed"})
                raise BudgetRefused(
                    "授权 %s 已关闭（%s），不得复用"
                    % (self.authorization_id, self.close_reason or "closed"))
            if self.used + int(n) > self.max_attempts:
                self._append_nolock({
                    "kind": "batch_rejected", "ts": _now(),
                    "authorization_id": self.authorization_id,
                    "purpose": purpose, "batch_need": int(n),
                    "used": self.used, "max_attempts": self.max_attempts,
                    "remaining": self.remaining()})
                raise BudgetRefused(
                    "整批预检拒绝：本批需 %d 次，授权 %s 已用 %d/%d（剩 %d）。"
                    "应停止并申请覆盖整批的新授权，不得先跑到一半。"
                    % (n, self.authorization_id, self.used,
                       self.max_attempts, self.remaining()))

    def reserve(self, attempt_id, purpose="turn_dispatch"):
        """原子占额 + started 落盘。同一把锁内完成全部验证（复核 v3 §2A：
        关闭态/唯一性/额度均按权威账本，不信实例缓存）；
        任何失败抛 BudgetRefused —— 派发方必须零派发。"""
        with _FileLock(self.lock_path):
            recs = self._read()
            self._check_scope(recs)
            used, attempt_ids, closed, close_reason, _s = self._derive(
                recs, self.authorization_id)
            self._ensure_scope_recorded(recs)
            if closed:
                self._append_nolock({
                    "kind": "dispatch_refused_closed", "ts": _now(),
                    "authorization_id": self.authorization_id,
                    "attempt_id": attempt_id, "reason": close_reason})
                raise BudgetRefused(
                    "授权 %s 已关闭（%s），不得复用（关闭态在锁内核对，"
                    "旧实例缓存不作数）" % (self.authorization_id,
                                            close_reason or "closed"))
            if attempt_id in attempt_ids:
                # 唯一性按权威账本（跨实例/跨进程），不是实例缓存
                self._append_nolock({
                    "kind": "dispatch_refused_duplicate", "ts": _now(),
                    "authorization_id": self.authorization_id,
                    "attempt_id": attempt_id})
                raise BudgetRefused(
                    "attempt_id 重复占额（权威账本已存在）: %s" % attempt_id)
            if used + 1 > self.max_attempts:
                self._append_nolock({
                    "kind": "dispatch_refused_budget", "ts": _now(),
                    "authorization_id": self.authorization_id,
                    "attempt_id": attempt_id, "used": used,
                    "max_attempts": self.max_attempts})
                raise BudgetRefused(
                    "额度拒绝：授权 %s 已用 %d/%d"
                    % (self.authorization_id, used, self.max_attempts))
            rec = {"kind": "attempt_started", "ts": _now(),
                   "authorization_id": self.authorization_id,
                   "attempt_id": attempt_id, "purpose": purpose,
                   "pid": os.getpid(), "reservation": uuid.uuid4().hex[:8]}
            # fsync 在锁内完成；失败抛出 → 调用方零派发
            self._append_nolock(rec)
            self.used = used + 1
            self._attempt_ids.add(attempt_id)
            return rec

    def record(self, kind, attempt_id, **extra):
        """完成/失败记账（消耗不退款）。kind ∈ attempt_completed/attempt_failed。"""
        rec = {"kind": kind, "ts": _now(),
               "authorization_id": self.authorization_id,
               "attempt_id": attempt_id}
        rec.update(extra)
        self._append(rec)

    def close(self, reason):
        """授权关闭。只能由可信批次收口显式调用（复核 v3 §2A：episode 结束
        不关闭整个授权——一个授权覆盖多故事/跨 run 累计）。幂等。"""
        if self.closed:
            return
        with _FileLock(self.lock_path):
            recs = self._read()
            _u, _a, closed, _cr, _s = self._derive(
                recs, self.authorization_id)
            if closed:
                self._refresh_cache(recs)
                return
            self._append_nolock({
                "kind": "authorization_closed", "ts": _now(),
                "authorization_id": self.authorization_id,
                "reason": reason})
            self.closed = True
            self.close_reason = reason

    def summary(self):
        return {"authorization_id": self.authorization_id,
                "used": self.used, "max_attempts": self.max_attempts,
                "remaining": self.remaining(), "closed": self.closed,
                "close_reason": self.close_reason,
                "ledger": self.ledger_path}


def _now():
    return time.strftime("%Y-%m-%d %H:%M:%S")
