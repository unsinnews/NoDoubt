# NoDoubt development guide

NoDoubt is an Android screenshot-solving application. Read README.md and docs/rebuild-plan.md for the current product scope.

## Product layout

- Keep the two main destinations: 首页 and 我的. Model configuration lives at 我的 → 模型服务, with 解题模型 and 配置文件 below it.
- Preserve the screenshot flow: a bubble opens a full-width, bottom-anchored answer sheet at two-thirds screen height. Its top handle adjusts height while its bottom remains anchored.
- Show all questions in one vertical reading column per mode. The segmented control and horizontal swipes switch 极速解题 / 深度思考. Keep 重截 and status in the bottom bar; show the current-mode stop action while running and 收起 otherwise.
- Separate the gray question area from the answer body. In both modes, any returned thinking content appears in its own region: collapsed by default to a two-line window that scrolls with the newest text, expanded only when the user opens it, regardless of whether request thinking parameters were configured.
- Use a restrained, iOS-like phone layout. User-facing theme names are 海蓝 (default), 暖橙 and 石墨, with light, dark and system appearance.
- Configuration and the four protocol adapters are application infrastructure. Keep external project branding and export-format choices out of the product flow; the configuration screen exports the application's own JSON.

## Build

- JDK 17, Android SDK 35, minSdk 23.
- ./gradlew :app:assembleDebug
- ./gradlew :core:ai:test :core:config:testDebugUnitTest :feature:solver:testDebugUnitTest :feature:channels:testDebugUnitTest :core:designsystem:testDebugUnitTest checkSourceSize
- ./gradlew :app:lintDebug
- Windows: gradlew.bat. Do not commit local.properties or build outputs.

## Boundaries

- core:model owns shared config and AI event types.
- core:config owns schema, credentials, migrations, merge and file/remote IO.
- core:ai owns independent adapters for Chat Completions, Responses, Anthropic Messages, Gemini.
- core:designsystem owns tokens, Compose controls and rich text.
- feature:channels owns configuration workflows and drafts.
- feature:solver owns recognition parsing, cancellation, candidate fallback, answers and solver UI.
- platform owns MediaProjection and overlay lifecycle/WindowManager.
- app only assembles navigation, settings and services.

## Invariants

- Never infer model abilities from model IDs or hide models using reasoning/input metadata.
- Request defaults follow pi (../pi packages/ai): Chat Completions store:false and stream usage, Responses store:false, short prompt caching, Anthropic max_tokens 16384, 2 retries, compat detected from the base URL only. Sampling (temperature/top_p/top_k) and thinking are never sent unless configured. Preserve VALUE false/0, INHERIT and OMIT.
- UI known parameters follow ParameterCatalog. Unknown vendor fields use typed advanced input.
- Persisted thinking controls are configured only in each model's ordinary detail page under 思考, using 默认 / 修改. The answer card may temporarily override thinking for its current question and mode: 默认 restores the saved thinking configuration of the current model by clearing the temporary override; do not add a separate model/reset choice. Explicit effort/budget changes replace the strength and mode while preserving summary/display preferences, includeThoughts and thinkingBudgets mappings, including dotted paths and extraBody. Use the same ParameterCatalog choices in both editors; display minimal as 最低 and xhigh as 很高, including Anthropic xhigh. Keep the model, status and thinking capsule in one compact answer header. Use a compact anchored popover with a blue-to-purple rounded depth slider, sparse static light dots and a white circular thumb: dragging and the default choice only change the draft; explicit confirmation retries the current question and mode. Dismissing discards the draft. Manual budget uses an inline one-row editor with confirm/cancel. Opening, dragging or dismissing must not request anything. Do not change persisted settings; preserve the override on ordinary retry and clear it on model changes or a new screenshot. Display the encoded request's thinking settings, never an inferred service-side strength. Always render thinking returned by the service in either answer mode. Filter channel-level thinking controls, including nested objects, dotted paths and extraBody, before inheritance. Older channel thinking settings are ignored with an import warning.
- Persist API keys and sensitive headers as encrypted credentials and references. Never log them. Only an explicitly enabled export may place resolved credentials in the user-selected JSON file.
- Imports never execute commands. Validate and preview before applying.
- Remote refresh preserves local overrides. Invalid persisted configuration must remain recoverable.
- Closing or dismissing the answer sheet must immediately cancel all OCR, FAST and DEEP requests. Recapture must first cancel the previous round, hide the app's overlay windows, then capture; prevent duplicate taps from starting overlapping captures and invalidate old callbacks. Never auto-replay OCR after any output.
- Waiting, recognition and queued answers must keep their actual states. Publish STOPPED only for an actual user interruption; internal cancellation for a new screenshot must not publish a transient stopped state.
- Maintain default parallel fast/deep answers and explicit user stop/retry.
- Use android-ui-ux-style-prompt.zh-CN.md. All themes apply to overlays too.
- Keep service/model/parameter changes in a draft until the service Save action; usage bindings and app preferences save immediately. Retain drafts through nested editing pages and rotation within the current process. Only the active page handles Back.
- ParameterCatalog suggestions do not enable values. Model maxTokens is the default output limit, as in pi; contextWindow is metadata.
- Production Kotlin files target 150–300 lines, hard check at 400. Do not split into meaningless forwarding helpers.
- Tests use fixture servers and fake gateways; do not use real model credentials without task authorization.
- Do not commit/push unless requested.

## Verification reporting

- Use docs/manual-verification.md for device and real-service checks. Unchecked items are pending, not passing tests.
- A successful JVM fixture test does not establish device permission behavior, theme rendering, SAF integration or real-provider interoperability.
- Long-pressing the bubble opens a menu: 恢复上次解答 reopens the in-memory sheet exactly as it was closed (stopped state, mode, scroll) without sending or replaying any request; 关闭悬浮球并退出 stops capture, the bubble and app screens and drops the session.
- The answer sheet session is in memory. 我的 → 解题记录 persists each question with its finished FAST/DEEP answers (newest 500, private app file, no thinking, no screenshots); a later finished answer for the same question updates its record, failures and stops keep the last finished one. Single delete is immediate with undo; multi-select delete and 清空 confirm first.
