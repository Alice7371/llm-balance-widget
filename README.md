# llm-balance-widget

Android home-screen widgets that show how much balance or quota you have left across
LLM API providers — DeepSeek, Kimi/Moonshot, Xiaomi MiMo, OpenCode Go, GLM/Zhipu
(bigmodel.cn) — plus any custom endpoint that returns its balance as JSON.

One glance at your home screen tells you whether you're about to run out of credits
or hit a rate-limit window, without opening a browser and logging into five consoles.

## Providers

| Provider | What the widget row shows | Credential |
|---|---|---|
| DeepSeek | Total balance in CNY (topped-up / granted breakdown) | API key (`sk-...`) |
| Kimi / Moonshot | Available balance in CNY (cash / voucher breakdown) | API key (`sk-...`) |
| Xiaomi MiMo | Console balance in CNY | Console cookie (`api-platform_serviceToken=...; userId=...`) |
| OpenCode Go | Subscription quota — `5h X% \| 7d Y%`, monthly windows | API key |
| GLM (Zhipu bigmodel.cn) | Coding-plan quota — `5h X% \| 7d Y%`, plan tier, MCP usage | API key |
| Custom (you define) | Any https URL returning JSON with a balance field | API key |

For custom providers the app recursively looks for a numeric balance in common
response shapes (`balance`, `total_balance`, `available_balance`, `data.balance`,
`balance_infos[0]`, ...) — so most OpenAI-compatible consoles work without any
parsing configuration.

## Widgets

**All Balances** (2×2, resizable) — the main widget. One row per enabled provider,
up to 8 slots. Each row shows the provider name, the balance/quota value, and a
color-coded progress bar:

- Balance rows (CNY): full bar = 10 CNY, hue fades green → red as balance empties.
- Quota rows (OpenCode Go, GLM): bar shows *remaining* quota, green when plenty is
  left, red when you're close to the limit.

Auto-refreshes every 30 minutes, and immediately whenever you change credentials,
ordering, or enabled providers in the app. Rows without a saved credential show
"No key".

**MyApp Clock** (4×2, resizable) — a live clock widget with seconds, date, and a
refresh button. It runs a foreground service (with a persistent notification,
as Android requires) to tick every second.

## Setup

1. Install the debug APK (or build your own, see below) and open the app.
2. Pick a provider from the dropdown, paste its credential, tap **Save**.
3. Use the checkboxes to enable/disable providers and ▲▼ to reorder them.
   The widget follows this order.
4. Long-press your home screen → **Widgets** → add **All Balances**.
5. Optional: add custom providers (name + balance URL + API key). Remove them
   anytime with the ✕ button.

## Building

Requirements: JDK 17, Android SDK 34 (Android Studio handles this for you).

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and press Run. `minSdk` is 24 (Android 7.0+),
`targetSdk` 34.

## Project layout

```
app/src/main/java/com/example/myapp/
├── MainActivity.kt                  # settings: credentials, order, custom providers
├── DeepSeekApi.kt                   # all HTTP + JSON parsing per provider
├── ProviderId.kt                    # built-in provider registry + custom provider model
├── CombinedBalanceWidgetProvider.kt # the balance widget
├── WidgetProvider.kt                # the clock widget
└── WidgetUpdateService.kt           # 1-second foreground ticker for the clock
```

Adding a provider: append an enum entry in `ProviderId.kt`, add a
`fetchXxxBalance` + parser in `DeepSeekApi.kt`, and a pref-key constant in
`MainActivity`. The widget picks it up automatically (including appends to a
previously saved order).

## Privacy

- Credentials are stored in the app's private `SharedPreferences` on your device
  only. Requests go straight from your device to each provider's API — there is no
  server, no analytics, no third-party relay.
- Storage is plain, not encrypted (`EncryptedSharedPreferences` not yet used), so
  on a rooted device the keys would be readable. Keep the app uninstalled-or-locked
  accordingly if that matters to you.
