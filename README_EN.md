<div align="center">

# LoveBrain

Android floating-panel chat assistant that helps you come up with the next reply in an intimate conversation.

<img src="images/demo.gif" alt="LoveBrain demo" width="340"/>

</div>

## What it does

Long-press a message (or paste one) and it generates **up to 8 ready-to-send reply suggestions** in one pass:

- **4 styles**: recommended / level-headed / playful / warm
- **4 directions**: follow-up / expand / express / redirect

Pick the one that sounds most like you, tweak the tone, send it. It hands you words; it doesn't talk for you.

It also keeps a **local relationship memory** — five-dimension vectors, stage tracking, and accumulated lessons — that gets more accurate the more you use it.

## How it works

<img src="images/owerview.jpg" alt="LoveBrain pipeline: capture → retrieve → assemble prompt → stream generation → pick & rewrite → background consolidation → read back" width="760"/>

## Requirements

- Android 8.0 (API 26) or newer
- An API key for an AI provider you configure yourself

## Install

Download `app-release.apk` from the [GitHub Releases](../../releases/latest) page and install it. Grant the "display over other apps" permission when asked; accessibility permission is optional and only used to capture the message you long-press.

## Provider setup

Open the app and fill in base URL, model name, and API key. DeepSeek is the default and primary tested provider; any OpenAI-compatible endpoint should work. The key is pasted in-app and stored encrypted via the Android Keystore.

## Performance

> ⚠️ The table below is a **single run from 2026-09-03** (deepseek-v4-flash, off-peak, 50 questions across 19 scenarios, strictly serial). The reproduction script, dependency lock, and raw JSON/CSV are not yet available, so these numbers are **not currently reproducible** — treat them as an order-of-magnitude reference. See [BENCHMARK.md](BENCHMARK.md).

| Metric | Value |
|---|---|
| Time to first token | 527 ms |
| Cache hit rate | 83% |
| Total cost for 50 questions | ¥0.17 |

## Build from source

```bash
git clone https://github.com/bystery/LoveBrain.git
cd LoveBrain
./gradlew assembleDebug    # or assembleRelease
```

The APK is written to `app/build/outputs/apk/<variant>/`.

## Privacy

- There is no LoveBrain backend. No telemetry, no analytics, no ad SDKs.
- All data (knowledge base, profiles, history) stays on-device in the app's private directory; cloud backup is disabled (`allowBackup=false`).
- The only network egress is the generation request sent to the AI provider you configure.
- You can view, edit, export, and delete everything locally at any time.

## License

Dual-licensed under [AGPL-3.0 or a commercial license](LICENSE). Open-source use is free under AGPL-3.0; closed-source commercial use requires prior permission from the maintainer — see the LICENSE file.

Personal project. PRs welcome. No SLA.
