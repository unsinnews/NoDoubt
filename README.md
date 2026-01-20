# Perfect Float Window

Android 悬浮窗，绝对是目前相关悬浮窗开源库最完美的适配方案。目前已经适配华为，小米，vivo，oppo，一加，三星，魅族，索尼，LG，IQOO，努比亚，中兴，金立，360，锤子等目前是市面上主流机型包括非主流机型，兼容 4.4 以上包括 Android 11 版本。

## Demo App - AI 智能解题

本项目的 Demo 应用是一个 **AI 智能解题助手**，通过悬浮窗截屏识别题目并给出答案。

### 功能特性

- **AI 题目识别**: 使用 Vision API 进行 OCR 智能识别
- **双模式解答**:
  - 极速解题 - 快速获取答案
  - 深度思考 - 详细解题步骤
- **流式回答**: 实时显示 AI 生成的答案
- **悬浮答题窗口**: 可拖拽调整大小的底部弹窗
- **平滑动画**: Tab 切换滑块动画效果
- **截屏功能**: MediaProjection 屏幕截取
- **智能重授权**: 屏幕关闭后自动重新授权，无需返回主应用

### 使用方法

1. 打开应用，开启悬浮窗权限
2. 进入设置页面，配置 API Key 和模型
3. 点击测试按钮验证 API 配置
4. 返回主页，开启悬浮窗开关
5. 切换到需要解题的应用界面
6. 点击悬浮窗按钮截屏识别题目
7. 查看 AI 生成的答案

### API 配置

支持 OpenAI 兼容的 API 接口：

| 配置项 | 说明 | 示例 |
|-------|------|------|
| API Key | 你的 API 密钥 | sk-xxx |
| OCR Base URL | OCR 识别接口地址 | https://api.openai.com/v1 |
| OCR Model | OCR 模型 ID | gpt-4o |
| Fast Model | 极速模式模型 | gpt-4o-mini |
| Deep Model | 深度模式模型 | gpt-4o |

---

## 悬浮窗库

### 特性

1. 支持悬浮窗内容自定义
2. 内部已处理权限校验，以及设置页面跳转
3. 支持 builder 模式，方便动态配置
4. 支持悬浮窗手势滑动
5. 适配 vivo，oppo 等第三方权限管理器跳转
6. 支持应用内以及应用外全局弹窗
7. 权限开启弹窗支持用户自定义

### 快速开始

#### 1. 添加依赖

```groovy
implementation 'com.alonsol:floatserver:1.0.0'
```

#### 2. 添加权限

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

#### 3. 初始化悬浮窗

```kotlin
floatHelper = FloatClient.Builder()
    .with(this)
    .addView(view)
    // 是否需要展示默认权限提示弹窗（默认开启）
    .enableDefaultPermissionDialog(false)
    .setClickTarget(MainActivity::class.java)
    .addPermissionCallback(object : IFloatPermissionCallback {
        override fun onPermissionResult(granted: Boolean) {
            if (!granted) {
                floatHelper?.requestPermission()
            }
        }
    })
    .build()
```

### API 文档

#### 开启默认弹窗

```kotlin
enableDefaultPermissionDialog(true)
```

#### 悬浮窗权限回调

```kotlin
addPermissionCallback(object : IFloatPermissionCallback {
    override fun onPermissionResult(granted: Boolean) {
        // granted = true 权限通过
        // granted = false 权限拒绝
        if (!granted) {
            floatHelper?.requestPermission()
        }
    }
})
```

#### 申请悬浮窗权限

```kotlin
floatHelper?.requestPermission()
```

#### 设置点击跳转目标

```kotlin
floatHelper?.setClickTarget(MainActivity::class.java)
```

#### 开启悬浮窗

```kotlin
floatHelper?.show()
```

#### 关闭悬浮窗

```kotlin
floatHelper?.dismiss()
```

#### 释放资源

```kotlin
override fun onDestroy() {
    super.onDestroy()
    floatHelper?.release()
}
```

#### 更新悬浮窗内容

```kotlin
private fun initCountDown() {
    countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            tvContent.text = getLeftTime(millisUntilFinished)
        }
        override fun onFinish() {}
    }
    countDownTimer?.start()
}
```

---

## 版本历史

### v1.2.0
- 新增 AI 智能解题功能
- 新增 OCR 题目识别
- 新增流式回答显示
- 新增设置页面
- 优化悬浮窗动画效果

### v1.0.1
- 修复已知问题
- 优化兼容性

### v1.0.0
- 初始版本发布

---

## 结语

PerfectFloatWindow 做了大量的机型测试，满足绝大部分市场上机型，欢迎大家提供宝贵意见。兼容性没有问题，如果需要调整悬浮窗动画以及配置，建议修改 floatServer 中的窗口配置，后续会对外提供相关接口。

## License

MIT License
