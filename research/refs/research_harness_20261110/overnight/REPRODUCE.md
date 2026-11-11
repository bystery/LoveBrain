# REPRODUCE — 有效代码入口与复现命令

工作根：`D:/lovebrain-arena`（镜像 `bystery/arena@research-p1-sync-20261111`，当前 SHA 见 RESUME_STATE.json）
前置：`LB_API_KEY` 环境变量（USTC endpoint `https://api.llm.ustc.edu.cn/v1`，model `deepseek-flash-2`，temp 0.7）。

## 1. 轮位级原型实验（RESEARCH-P1 标准入口）

```bash
# plan（零网络）：解析位置+候选身份哈希
python -I tools/benchmark_v1/prototype_runner.py --mode plan \
  --positions smoke_24 --arms rh_b04_anchored_layout,rh_b06_tiny_cascade,rh_b09_extractive_slim \
  --out runs/<name>

# mock（离线管道验证，不花额度）
python -I tools/benchmark_v1/prototype_runner.py --mode mock \
  --positions smoke_24 --arms <arms> --out runs/<name>_mock

# live（真实调用，需 AUTH；并发=1，cap 超限停批）
python -I tools/benchmark_v1/prototype_runner.py --mode live \
  --positions smoke_24 --arms <arms> --out runs/<name> \
  --authorization refs/research_harness_20261110/phase1/AUTH_*.json
```

- 位置集：`smoke_24` 或逗号分隔 `position_id`（见 `phase1/benchmark/DEV_FAST_SET.json`，sha256 `99e8fe68…`）
- 新原型接入：在 `tools/benchmark_v1/prototype_candidates/<id>/` 建 `mechanism/mechanism.py`（v2 契约 init/run_turn）+ `assets/system_prompt/` 四件套 + `params.json`，并把 `<id>` 加进 `normalize_output.py` 的 `LIGHT_PARSE_ARMS` 与 `MECHANISM_REGISTRY.md`

## 2. 评分（程序层口径）

```bash
python -I - <<'PY'
import sys; sys.path.insert(0,'arena'); sys.path.insert(0,'tools'); sys.path.insert(0,'tools/benchmark_v1')
import universal_scorer as us
rows, summary = us.score_run('runs/<name>', arms=(<arm ids>), out_prefix='runs/<name>/<prefix>')
PY
```

## 3. 端点缓存探测（RH-B-11）

```bash
python -I tools/benchmark_v1/probe_prefix_cache.py runs/<probe_dir>
```

## 4. 已完成批次（证据位置）

| 批次 | out_dir | 结果 |
| --- | --- | --- |
| smoke_24 × b04/b06/b09 | `runs/rp1_smoke_20261111/`（85 attempts） | `phase1/experiments/2026-11-11_smoke_24/`（摘要已推送）+ `harness/FIRST_PROTOTYPE_RESULTS.md` |
| b11 缓存对照（6 位×2 臂） | `runs/rp1_overnight_b11_20261111/`（12） | LIVE_LOOP_LOG LOOP-2（NULL 结果） |
| b06b 冒烟 | `runs/rp1_overnight_b06b_20261111/` | 运行中 |

## 5. 关键纪律提醒

- attempt 计数：`count_attempts(out_dir)`（gateway_events.jsonl 的 attempt_started 总数，含重试）
- 禁区：`benchmark/b_line/`、`D:/lovebrain-arena_holdout_vault/`、E401–E520（E401–E423 已曝光不得当新题）
- 原始逐调用答卷（runs/、logs/）不入 GitHub；只推摘要证据
