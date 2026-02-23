# TapPilot

一款基于视觉语言模型（VLM）的 Android GUI 自动化 agent 应用。通过自然语言描述任务，让 AI 自动操控你的手机完成各种操作。

初衷是想要一个极简的移动端 GUI agent，简单配置下远程的模型提供商 API 就能用。

## 功能特性

- **自然语言控制** - 用中文或英文描述你想完成的任务，如"打开网易云音乐播放周杰伦的歌"
- **视觉理解** - 基于屏幕截图分析，VLM 能精准识别 UI 元素位置并返回坐标
- **响应式执行** - 逐步执行模式，每一步都基于当前屏幕状态决策，而非预先规划所有步骤
- **自定义 LLM 提供商** - 支持 OpenAI 兼容的 API 端点，可接入本地模型（Ollama、LM Studio）或云服务（DeepSeek、Groq 等）

## 支持的操作

| 操作 | 说明 |
|------|------|
| `CLICK` | 点击指定坐标 (x, y) |
| `TYPE` | 在当前焦点输入文本 |
| `SCROLL` | 滚动屏幕（上/下/左/右） |
| `PRESS_BACK` | 按返回键 |
| `LAUNCH_APP` | 启动指定应用 |
| `WAIT` | 等待指定毫秒数 |

## 系统要求

- Android 7.0 (API 24) 及以上
- 需要开启无障碍服务权限
- 需要屏幕录制权限（用于截图分析）

## 快速开始

### 1. 构建安装

```bash
# 克隆项目
git clone https://github.com/yourname/TapPilot.git
cd TapPilot

# 构建 APK
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 配置 LLM 提供商

打开应用 → 设置 → 选择或添加自定义提供商

推荐使用字节火山引擎平台中的 Doubao-Seed-1.6-vision 或者 Doubao-Seed-2.0-pro，新用户注册会赠送免费额度试用，在后台开通模型并创建 API key，在 app 里填写配置即可。火山的 base URL 为 https://ark.cn-beijing.volces.com/api/v3

**自定义提供商配置示例（本地 Ollama）：**
- 名称：`Ollama Local`
- Base URL：`http://192.168.1.100:11434/v1`
- 模型：`qwen2.5-vl:7b`
- API Key：（留空）
- 支持视觉：开启

### 3. 授权权限

1. 开启无障碍服务：设置 → 无障碍 → TapPilot → 开启
2. 首次执行任务时会请求屏幕录制权限

### 4. 执行任务

在主界面输入任务描述，点击"执行任务"即可。

## 设置选项

| 选项 | 说明 | 默认值 |
|------|------|--------|
| 执行速度 | 操作之间的延迟（毫秒） | 500ms |
| 最大执行步数 | 单次任务的最大操作数 | 20 步 |
| 启用截图分析 | 是否发送截图给 VLM | 开启 |

## 技术架构

```
app/
├── data/
│   ├── local/          # 本地存储（PreferencesManager）
│   └── remote/         # LLM API 调用
│       └── provider/   # 多提供商支持
├── domain/
│   ├── model/          # 数据模型（ActionStep 等）
│   └── usecase/        # 业务逻辑
├── presentation/
│   ├── main/           # 主界面
│   └── settings/       # 设置界面
├── service/
│   ├── AutomationAccessibilityService  # 无障碍服务
│   └── ScreenCaptureService            # 屏幕录制服务
└── util/
    ├── ActionExecutor  # 操作执行器
    ├── ActionParser    # JSON 解析器
    └── ScreenshotCapture  # 截图工具
```

## 工作原理

1. **截图** - 捕获当前屏幕
2. **分析** - 将截图和任务描述发送给 VLM
3. **决策** - VLM 返回下一步操作（包含精确坐标）
4. **执行** - 通过无障碍服务执行点击/输入等操作
5. **循环** - 重复以上步骤直到任务完成或达到最大步数

VLM 会收到已执行的操作历史，避免重复无效操作。当任务完成或陷入困境时，返回空数组 `[]` 结束执行。

## 依赖项

- Kotlin 1.9.0
- Retrofit 2.9.0 + OkHttp 4.11.0
- Kotlin Coroutines 1.7.3
- AndroidX Lifecycle 2.6.2
- Timber 5.0.1
- Gson 2.10.1

## 许可证

MIT License

## 致谢

本项目主体由 Qoder 所写。
