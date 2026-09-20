# 配置核心

`ConfigRepository` 是 Android 入口，`state` 暴露当前 `AppConfig`。GUI 编辑整个不可变配置后调用 `save`；渠道和密钥一起保存使用 `saveChannel`，避免产生两版历史。`resolveDraft` 用于尚未保存的渠道测试，`resolve` 用于正式请求。

## 文件格式

```json
{
  "schemaVersion": 1,
  "providers": {
    "my-provider": {
      "name": "我的渠道",
      "api": "openai-responses",
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "$MY_API_KEY",
      "models": [{ "id": "my-model", "name": "我的模型" }]
    }
  },
  "nodoubt": {
    "id": "default",
    "name": "默认配置",
    "channels": {
      "my-provider": {
        "parameters": {
          "temperature": { "mode": "VALUE", "value": 0.4 }
        },
        "models": {
          "my-model": {
            "parameters": { "temperature": { "mode": "OMIT" } }
          }
        }
      }
    }
  }
}
```

协议只接受 `openai-completions`、`openai-responses`、`anthropic-messages`、`google-generative-ai`。模型 `api`/`baseUrl` 覆盖渠道；模型请求头按大小写不敏感的键覆盖渠道；`compat` 递归合并。普通参数 `INHERIT` 继承渠道、`VALUE` 显式发送、`OMIT` 省略，未启用参数不会发送。对象与点路径统一解析为嵌套 JSON，子字段优先于父对象；普通子字段 `INHERIT` 恢复渠道对应值，`OMIT` 只删除该字段并保留其他字段。

思考仅由具体模型配置，GUI 使用「默认 / 修改」。`ModelThinking` 在继承前去掉渠道思考参数及渠道 `extraBody` 中的思考控制字段，保留其他渠道字段。导入会提示被忽略的渠道思考；模型未设置、`INHERIT` 和 `OMIT` 都不会继承渠道思考，模型显式思考及模型 `extraBody` 保持有效。

Pi 的 `modelOverrides` 最后合入模型；未知模型会建立可编辑条目并提示核对。`reasoning`、`input`、`cost` 等目录元数据会保留，不用于筛选模型。显式 `samplingParams` 导入为启用的参数。没有附带 pi 内置模型目录，因此省略 `api` 的导入会提示核对协议。

## 秘密与变量

配置文件只保存 `secret:` 引用，密钥、变量实际值及敏感请求头使用 Android Keystore 的 AES-GCM 加密。默认导出保留引用。显式启用 `includeSecrets` 才展开秘密。

兼容 pi 的 `$VAR`、`${VAR}` 字符串插值和 `$$`、`$!` 转义。变量在 NoDoubt GUI 中手动映射；不会读取桌面环境变量。以 `!` 开头的命令不会执行，导入预览会列出待手动配置项。

纯 pi 导出移除 `nodoubt` 扩展，将本地秘密引用变为 `$NODOUBT_…` 环境变量引用。仅 OpenAI 两种受支持协议能够通过 pi `samplingParams` 表达启用参数；其他协议参数及客户端超时/重试字段会省略。调用 `exportWarnings(true)` 展示具体丢失字段。

## 持久化与导入

- 配置集位于应用私有、排除系统备份的目录。保存先校验，使用 `AtomicFile`，并保留上一个有效版本。
- 失败的配置写入会回滚同次凭据修改。坏 JSON 不会被空默认配置自动覆盖；`initializationError` 可供 UI 显示，`restorePrevious` 恢复上版。
- `createProfile`/`copyProfile` 不自动切换；删除活动配置前需先切换。
- `importPreview` 只生成内存预览。`applyImport` 显式选择合并或替换，预览后配置变化会要求重新预览。
- 远程下载限定 2 MB，具有超时、取消、ETag 和 Last-Modified 支持。刷新使用上次远程基线、本地配置、新远程配置三路合并，本地修改赢得冲突。未改动实体接受远程删除，本地编辑过的实体保留。网络或解析失败不改变当前配置。
- `ai_settings` 的主分支、中间 `default_channel_*` 和 `channels_json` 三代格式会迁移；独立 `theme_prefs` 的昵称、头像和浮球大小同样迁移。迁移前将原始 XML 加密备份，成功保存后才清理旧明文 API 设置。

## 验证

`./gradlew :core:config:testDebugUnitTest` 覆盖格式解析、pi 往返、参数继承与省略、变量模板、三代迁移、远程三路合并及凭据回滚。Keystore 与 Android 私有文件行为还需设备验证。
