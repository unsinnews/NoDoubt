# 重构验证记录

记录日期：2026-09-20。分支：codex/rebuild-nodoubt。以下区分已执行的结果与当前待验范围。

## 构建与单元测试

累计 **150 项单元测试通过，0 失败、0 错误**。结果来自各模块的 JUnit XML：

| 模块 | 通过数量 | 主要覆盖 |
| --- | ---: | --- |
| core:ai | 33 | 四协议请求、流解析、临时思考覆盖、实际请求快照、预算校验及 extraBody 辅助偏好优先级 |
| core:config | 34 | 参数合并、模型独立思考、导入提示、迁移、远程合并、凭据回滚 |
| feature:channels | 18 | 草稿、模型标识变更、默认思考清理及业务 schema 保留 |
| feature:solver | 35 | 多题、双模式、停止与重试、初始状态、独立阅读状态、单题单模式思考覆盖与草稿校验 |
| core:designsystem | 9 | 公式分隔符、六套调色板的对比度 |
| platform | 21 | 截图请求与 reader 来源代号、迟到帧、快速换图、授权完成时机、位置恢复与系统栏几何边界 |

完整基础验证见 build/qa/final-mobile-verification.log；后续模型思考、答案状态与独立阅读修正分别在 thinking-path-verification.log、answer-flow-verification.log、reading-state-verification.log 中通过受影响模块复验。

新版完整构建见 build/qa/solver-polish-verification.log，记录 **BUILD SUCCESSFUL in 19m 8s**：应用 APK、测试 APK、单元测试、Lint 和 checkSourceSize 均通过。最后的触控尺寸与状态去重调整在 solver-touch-verification.log 中复验，记录 **BUILD SUCCESSFUL in 3m 36s**。Lint XML 为 **0 errors, 21 warnings**，剩余警告为 SDK/依赖版本、沿用的启动图标和 KTX 建议。生产 Kotlin 文件未超过 400 行限制。compact-final-verification.log、thinking-level-final-verification.log 与 thinking-ime-final-verification.log 保留为早期版本记录；新版设备结果以下述 solver-polish-instrumentation-confirmed.log 为准。

新增 CaptureFrameGateTest 的 4 项单元测试已包含在 platform 的 21 项中：旧 reader 缓冲帧不能完成重截；旋转时同一请求号的旧来源失效；取消后的迟到帧不能完成或取消新请求；静止画面重绑来源后拒绝上一轮已消费帧，并只消费新来源一次。它们验证来源与请求的隔离规则；后续软件渲染模拟器也验证了静止屏幕重绑可及时产出画面，见下方证据。

## Android 设备测试

设备为任务独立的 Android 15 / API 35 模拟器。早期检查使用 1080×2400、420 dpi；后续思考交互检查使用 540×1200、210 dpi，逻辑尺寸一致。Gradle 停止后直接使用 adb instrumentation 运行，避免离线 UTP 依赖缺失。最新 build/qa/solver-polish-instrumentation-confirmed.log 记录 **8 项设备测试全部通过**（162.029 秒），显式运行 AnswerThinkingFlowTest、ApplicationFlowTest 和 CapturePermissionContractTest。结果覆盖：

1. 渠道草稿跨 Activity 重建保留，离开未保存服务有确认，渠道页没有全局思考。
2. 模型思考只有默认/修改；显式设置可保存，恢复默认仅删除思考字段并保留温度。
3. 本地 SSE 完成识题、两题四份解答、凭据加密重载和默认导出脱敏；初始答案页没有“已停止”。默认参数下两种模式都展示返回的思考，折叠与切换模式后保持状态，所有题目可纵向阅读。
4. 外观页离开再进入，富文本实际绘制像素，排除只有布局而无正文的回归。
5. 授权 Intent 携带独立新任务、隐藏最近任务和不自动截图的标志。
6. 授权 Activity 使用独立任务归属、非导出，并保留系统授权结果回调所需的历史记录。
7. 真实手势滑动、草稿取消后恢复、明确确认才重试本题当前模式、普通重试沿用临时层级；「默认」恢复当前模型保存的 low 并保留 temperature，临时覆盖清为 null。页头选择当前模型不请求，换到第二候选清除临时层级且只重答第一题极速；响应阻塞时底栏停止使当前极速进入 STOPPED，随后收起触发退出回调。另一模式、第二题和导出配置均不变。
8. 手动预算打开、输入及取消不请求且保留原答案；Anthropic 预算与最大输出同为 2048 时预检失败，也不发送请求。修正为 1024 并确认后仅重试当前解答，实际请求包含 enabled、budget_tokens=1024 与 max_tokens=2048。

测试中曾发现模式切换后思考重新展开；已将阅读位置与折叠状态提升为按模式/题目/请求保存，保持原测试预期后复验通过。

新版 AnswerThinkingFlowTest 的两项用例已执行通过，主要请求计数与交互边界如下：

- 打开、关闭锚定浮层不发送请求；滑动只改草稿，入口继续显示实际请求层级。真实手势滑到最后一档后取消，重新打开恢复原有的低；再通过无障碍滑杆动作选择高，确认后才发送 high。
- 「默认」在解答页表示沿用该模型原有思考参数，确认前不请求，确认后恢复模型保存的 low 和普通温度参数；没有额外的模型或还原按钮。模型原本未配置思考时，入口简写为「默认」，无障碍描述仍为「服务端默认」。模型配置页的「默认 / 修改」是持久配置入口，其默认不额外发送思考字段，两处语义按各自场景验证。
- 第一项包含初始 5 条请求，依次确认高、普通重试、默认恢复后累计 8 条；再次确认高、换模型和发起待停止的请求后累计 11 条。打开菜单、同选当前模型、停止和收起均不额外请求。每步同时核对第一题深度、第二题及导出配置未改动。
- 手动预算在关闭菜单后进入卡片内短编辑；打开、输入和取消均不请求、不改变原答案。2048 预算与 2048 输出上限冲突时保留原答案并显示错误；改为 1024 后才发送 enabled、budget_tokens=1024、max_tokens=2048 的请求。

最新一轮第一次执行的 solver-polish-instrumentation.log 发生冷启动 ANR，记录为 Process crashed，不计为通过。预热 MainActivity 后保留原断言重跑，得到上述 8 项通过结果。更早测试夹具曾在 UI 线程调用 MockWebServer.url 触发 NetworkOnMainThreadException，已将 URL 计算移到测试线程；StrictMode 与请求/作用域断言保持不变。早期 thinking-ime-instrumentation.log 的 8 项结果仅作为旧版本记录。

## 已执行的悬浮窗口检查

以下使用受控的本地 OpenAI Chat Completions 夹具；题目与答案是固定测试内容，不代表真实 OCR 准确率。

- 首页开启悬浮球只请求截图授权；授权后未自动发起识题。
- 切到 Android 桌面点击悬浮球，MediaProjection → 图片请求 → 多题与双模式解答贯通。
- 收到的截图不含悬浮球、答案窗或遮罩；证据为 build/qa/capture-received.jpg。
- 答案全宽贴底，初始约三分之二；把手上拖可扩展，底栏保留；题目灰底和解答白底层级清楚。
- 点击外部遮罩关闭答案并恢复球。慢速流验证中，识题及两种回答的三条连接全部断开，关闭后没有追加解题请求；脱敏证据为 build/qa/exit-cancellation.log。
- 连续执行两次重截，每次重新识题并打开答案，收到的截图仍无旧悬浮内容。

界面证据：build/qa/final-home.png、answer-hierarchy.png、answer-expanded.png、retake-answer.png。部分手工项目包含未执行的分支，因此 manual-verification.md 的综合检查项仍保持未勾选。

### 新版紧凑答题页与思考滑杆

以下是在软件渲染模拟器、真实悬浮答案窗执行的新版手工检查，文件位于 build/qa/：

- polish-answer-settled.png 显示页头模型入口、思考胶囊、题目与答案分层以及底部重截／状态／收起；首题答案和公式完整，未被裁切。
- polish-slider-default.png、polish-slider-preview.png、polish-slider-confirmed.png 记录蓝紫渐变滑杆的打开、预览和确认。拖动期间请求数 70 → 70，见 polish-slider-request-check.json；确认后只增加 1 条回答请求，fixture-events.log 中该请求的 effort 为 low。
- polish-slider-max-preview.png 文件名虽含 max，实际画面是「很高」即 xhigh 的未确认草稿；它只作为预览证据，不能记为 max 已发送或供应商接受该层级。
- 随后直接点击滑杆右端，polish-slider-endpoint.png 显示预选「最高」即 max；实际请求入口仍为低，夹具未收到新请求。点击弹层外后草稿丢弃，也未产生请求。这验证右端选择与取消，不代表 max 已发送。

新版手动预算与 IME 也已在真实悬浮窗口复验：

- 切到 budget-model，打开单行预算编辑，通过真实 Gboard 按键依次触摸 1、0、2、4 输入 1024。polish-budget-typed.png 显示预算输入、确认／取消和底栏均在键盘上方。
- 确认后 polish-budget-confirmed.png 显示实际请求入口为「预算 1024」，键盘已收起。fixture-events.log 中 805052d8 是此次唯一新增请求，thinking.type 为 enabled、budget_tokens 为 1024。手工日志未记录 max_tokens；该字段的 2048 断言来自上文设备自动测试。
- 再次打开预算与键盘，状态见 polish-before-retake-ime.png；点击键盘上方「重截」后 OCR 14 → 15，polish-retake-ime-state.log 记录 mInputShown=false。polish-retake-ime-received.jpg 已检查为干净的 Clock Alarm 画面，不含键盘、悬浮球或答案窗。
- 上述操作期间外部 Activity 始终为 Clock 的 task 179，没有跳回不惑主页。

在 540×1200、210 dpi 的软件渲染模拟器将 font_scale 设为 1.3 后，已检查 polish-font-130.png 与 polish-font-130-slider.png：标题、题目、实际思考胶囊、滑杆弹层的默认／确认操作及底栏均无文字截断或控件重叠，正文自然换行并可滚动。此项只覆盖所示首题与弹层，不代表全应用、更大字体或横屏已全部验收；检查后已恢复 font_scale=1.0。下方旧 IME 记录保留其版本与像素证据边界。

### 已完成的 IME 手工检查与截图证据边界

以下是在紧凑菜单改造前、真实 OverlayWindow 中执行的手工检查，不能替代新版菜单验收：

- 已通过真实 Gboard 软键盘触摸输入 1024，确认遮罩不再拦截键盘按键。
- 点击外部遮罩关闭后，IME 采样为 mInputShown=false。
- build/qa/ime-padding-verified.png 显示“重截”栏已抬到键盘上方，同名 XML 保留输入区与底栏坐标。
- 键盘显示时点击“重截”，OCR 请求计数从 7 增至 8，IME 采样为 mInputShown=false。这只证明重截动作和键盘退出，尚不计为该轮截图像素干净的通过证据。

host GPU 模拟器存在截图与界面层次不一致：build/qa/capture-timer-source.xml 已是 Clock 的 Timer 页面，但同名 PNG 仍显示旧答案和键盘；同时 WindowManager 采样显示答案 overlay 已隐藏。因此这组像素不能用于认定应用读取了旧图，也不能用来证明已取得干净的新截图。

之后使用 swiftshader_indirect、关闭 Vulkan、2 核的软件渲染模拟器复验。首页和系统截图授权提示均显示“不惑”（slider-baseline-home.png、slider-baseline-permission.png）。授权后 OCR 保持 8；在 Clock 闹钟页点击球后增为 9，fresh-alarm-received.jpg 是干净的闹钟页。切到 Timer 后，fresh-timer-confirmed-source 的 XML 与 PNG 一致；再次截图 OCR 增为 11，fresh-timer-confirmed-received.jpg 已是计时器画面，不含悬浮球、旧答案或键盘。静止 Timer 页面点击“重截”后 OCR 增为 12，fresh-static-retake-received.jpg 仍及时得到干净的 Timer 画面，未超时。上述文件均位于 build/qa/。这一轮验证的是新版截图实现；后续滑杆、确认交互与新版 IME 结果见上一节。

### 授权恢复与悬浮球位置

在 Android 15 模拟器补充执行，状态、窗口坐标和请求计数见 build/qa/overlay-device-evidence.jsonl：

- 无位置记录时，1080×2400 屏幕上的球范围为 [922,1737]–[1048,1863]，球心 y=1800，符合右侧、距顶部 75% 的要求。
- 拖动保存后，关闭并重开助手、强制结束进程后再开启，均恢复为 [348,1151]–[474,1277]；拖动未发起 OCR。旋转为 2400×1080 后保持屏内相对位置，恢复竖屏后坐标一致。
- 实际息屏/亮屏后，此模拟器仍保留 MediaProjection 授权。随后以应用自身 UID 停止 CaptureService，模拟授权失效（确认投屏为 null），在外部时钟应用点击悬浮球重新授权。
- 取消授权返回原时钟应用并恢复球；再次授权成功也返回同一个时钟任务，球重新显示，OCR 请求计数仍为 4。第二次点击球才增加至 5 并显示解答，原时钟应用始终留在其下方。
- 证据图片：position-default.png、position-landscape.png、reauthorize-cancelled.png、reauthorize-idle.png、reauthorize-second-click.png，均位于 build/qa/。

这覆盖了授权失效后的返回行为，不等同于证明所有真机在息屏时均以相同方式撤销授权。API 23–29 系统栏边界另有几何测试；API 28 刘海横屏仍需设备确认。

## 环境与未覆盖范围

- 在 Gradle 与模拟器同时运行、主机仅余约 0.7 GB 内存时曾出现冷启动 ANR；采样显示明显换页及渲染/JIT延迟，未取得可归因的 Java 阻塞栈。随后分开构建与设备运行，上述设备测试通过。证据见 build/qa/anr-assessment.md，不能据此声称覆盖真机启动性能。
- 四种协议的真实供应商 API、真实密钥、厂商权限差异、其他 Android 版本、系统文件选择器的全部分支仍未验证。
- API 23–29 浮层的 IME inset 分发、小窗编辑临时增高后恢复用户高度，以及旋转中的键盘避让仍需设备确认。
- 模拟器与本地夹具不代替物理设备、真实服务和完整的大字体/横屏验收；未执行的项见 manual-verification.md。

## 产物

- 应用：app/build/outputs/apk/debug/app-debug.apk
- 设备测试：app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
- 旧分支归档：仓库同级 NoDoubt-archives/before-rebuild-20260919/all-refs.bundle；本地和 origin 的旧分支已按授权清理，保留 master。
