# 不惑（NoDoubt）

Android 悬浮截图解题助手：点悬浮球截图，答案抽屉里同时给出「极速解题」和「深度思考」两种解答。

## 功能

- 一次截图识别多道题，流式显示 Markdown 与公式，可停止、重试、复制
- 极速 / 深度并行作答，显示模型返回的思考过程
- 每题可临时切换模型和思考强度
- 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Gemini 四种协议
- 每种用途可配多个模型，失败时按顺序换用
- 配置可从文件、剪贴板导入或订阅地址同步，也可导出；密钥加密保存
- 海蓝、暖橙、石墨三种主题，支持浅色 / 深色

## 使用

1. **我的 → 模型服务 → 添加服务**：填写地址和 API Key，添加模型，测试连接后保存。
2. **解题模型**：为截图识题、极速回答、深度回答添加模型，长按拖动排序，第一个为默认。
3. **首页**：打开悬浮球并授权，到题目页面点悬浮球即可。

截图会发送到你配置的 API 服务，费用由服务方收取。

## 下载

在 [Releases](https://github.com/unsinnews/NoDoubt/releases) 下载 APK，支持 Android 6.0 及以上。

## 构建

需要 JDK 17 和 Android SDK 35。

```shell
./gradlew :app:assembleRelease
```

发布签名通过环境变量 `NODOUBT_KEYSTORE`、`NODOUBT_KEYSTORE_PASSWORD`、`NODOUBT_KEY_ALIAS`、`NODOUBT_KEY_PASSWORD` 指定；未设置时使用仓库内的 `app/debug.keystore`。

配置格式见 [docs/configuration.md](docs/configuration.md)，开发约定见 [CLAUDE.md](CLAUDE.md)。

## 致谢与许可

悬浮截图方案源自 [PerfectFloatWindow](https://github.com/Alonsol/PerfectFloatWindow)，请求默认值参考 [pi](https://github.com/earendil-works/pi)。仅限个人学习、研究和非商业用途，详见 [LICENSE](LICENSE)。
