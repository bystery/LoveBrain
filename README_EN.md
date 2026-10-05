# LoveBrain

Android floating-panel chat assistant that helps you come up with the next
reply in an intimate conversation.

## Requirements

- Android 8.0 (API 26) or newer
- An API key for an AI provider you configure yourself

## Install

Download `app-release.apk` from the [GitHub Releases](../../releases/latest)
page and install it. Grant the "display over other apps" permission when
asked; accessibility permission is optional and only used to capture the
message you long-press.

## Build from source

```bash
git clone https://github.com/bystery/LoveBrain.git
cd LoveBrain
./gradlew assembleDebug    # or assembleRelease
```

The APK is written to `app/build/outputs/apk/<variant>/`.

## Provider setup

Open the app and fill in base URL, model name, and API key. DeepSeek is the
default and primary tested provider; any OpenAI-compatible endpoint should
work. The key is pasted in-app and stored encrypted via the Android Keystore.

## Privacy

- There is no LoveBrain backend. No telemetry, no analytics, no ad SDKs.
- All data (knowledge base, profiles, history) stays on-device in the app's
  private directory; cloud backup is disabled (`allowBackup=false`).
- The only network egress is the generation request sent to the AI provider
  you configure.
- You can view, edit, export, and delete everything locally at any time.

## License

Dual-licensed under [AGPL-3.0 or a commercial license](LICENSE). Open-source
use is free under AGPL-3.0; closed-source commercial use requires prior
permission from the maintainer — see the LICENSE file.

Personal project. PRs welcome. No SLA.
