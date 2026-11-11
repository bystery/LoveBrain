# REBUILD_AND_CONTROLLED_DATA — 发布包可重建输入与受控数据说明（R3#7）

本文件说明 arena_v1 发布包中三类"清单/夹具"文件如何**从受控输入重建**，
以及哪些受控数据**刻意不进包**。任何一项无法按本文重建即视为发布包不完整。

## 1. split_manifest（划分清单）

- 现物：`benchmark/releases/arena_v1/split_manifest.json`
- 重建入口：`python -B -X utf8 tools/gen_split_manifest.py`（cwd=仓库根）
- 输入：`benchmark/episodes/*.json`（含 dev/）——逐故事家族登记用途、来源、
  创建/冻结时间、是否曾参与调参、谁可见、版本散列。
- 受控边界：清单只登记**散列与元数据**，不携带真实聊天记录内容；
  用户 dataset（真实聊天原文）永不进包，散列对账以用户侧留底为准。

## 2. review_drill_spec（评审演练材料清单）

- 现物：`refs/arena_v1/review_drill_spec.json`
- 性质：人工维护的演练输入清单（12 条 material：case_ref + 双臂回复 + 状态）。
  双臂回复取自冻结的对照运行输出（mock/真实各批），登记时人工转写为
  reply 字符串——不携带任何身份信息。
- 重建入口：`python -B -X utf8 tools/review_loop.py gen refs/arena_v1/review_drill_spec.json`
  生成 `benchmark/releases/arena_v1/review_loop/`（materials/forms/mapping）。
- 演练现场文件（随包分发）：`materials/`、`mapping_secret.json`（anon↔臂映射
  与绑定散列；**不含任何凭据**，文件名沿用历史）、`ingest_result.json`、
  `entries/`（fixture/reviewer 演练表单）。`forms/` 空白表可由 gen 重建，不进包亦可。

## 3. seal_fixture（合成封存夹具）

- 现物：`refs/arena_v1/seal_fixture/{SYNTH_SEALED_001.json,SEAL_LOCK.sha256}`
- 性质：**纯合成** canary（kind=synthetic_canary），非真实题面。
- 重建：写任意 synthetic_canary JSON 后
  `python -c "import hashlib;print(hashlib.sha256(open('SYNTH_SEALED_001.json','rb').read()).hexdigest())"`
  覆写 `SEAL_LOCK.sha256`。
- 访问控制语义验证：`tools/test_seal_access_control.py`（deny→BLOCKED 正向证据、
  授权+/reset→READ_OK 散列一致）。真实 B 线不打开，其存在与否只是范围声明。

## 4. 刻意不进包的受控数据（排除断言固化于 tools/build_release_package.py）

| 数据 | 理由 |
|---|---|
| 用户 dataset / 真实聊天原文 | 用户隐私；只保留散列对账 |
| real66 匿名分组与密钥（REAL66_ANON_KEY 等） | 身份映射敏感 |
| 真实 B 线内容 | 评测保留区，仓库内本就不落盘 |
| 密钥/凭据（含形状嗅探 sk-/AKIA 等） | 安全 |
| logs/、isol_runs/、*_acl_locked_* 残石 | 运行历史与隔离残石不属发布物 |
| zip/__pycache__/pyc | 构建产物 |

## 5. release.json 冻结纪律

- `refs/arena_v1/release.json` 由 `tools/freeze_release.py` 生成（与
  build_release_package 的白名单同源），必须在**代码定稿之后**冻结，
  否则散列漂移 → G01 FAIL。
- release.json 不列自身；G01 逐文件核对 sha256；空 files 视为 FAIL。
