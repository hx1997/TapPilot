# TapPilot

[🇺🇸 English Version](README_EN.md) | [🇨🇳 中文版](README.md)

An Android GUI automation agent application based on Vision Language Models (VLM). Control your phone through natural language task descriptions, letting AI automatically operate your device to complete various tasks.

The original intention was to create a minimalist mobile GUI agent that can be used with simple configuration of remote model provider APIs.

## Features

- **Natural Language Control** - Describe tasks in Chinese or English, such as "Open NetEase Cloud Music and play Jay Chou's songs"
- **Visual Understanding** - Analyzes screen screenshots, VLM accurately identifies UI element positions and returns coordinates
- **Responsive Execution** - Step-by-step execution mode, each step makes decisions based on current screen state rather than pre-planning all steps
- **Custom LLM Provider** - Supports OpenAI-compatible API endpoints, can connect to local models (Ollama, LM Studio) or cloud services (DeepSeek, Groq, etc.)

## Supported Operations

| Operation | Description |
|-----------|-------------|
| `CLICK` | Click at specified coordinates (x, y) |
| `TYPE` | Type text in the current focused input field |
| `SCROLL` | Scroll screen (up/down/left/right) |
| `PRESS_BACK` | Press back button |
| `LAUNCH_APP` | Launch specified application |
| `WAIT` | Wait for specified milliseconds |

## System Requirements

- Android 7.0 (API 24) and above
- Accessibility service permission required
- Screen recording permission required (for screenshot analysis)

## Quick Start

### 1. Build and Install

```bash
# Clone the repository
git clone https://github.com/yourname/TapPilot.git
cd TapPilot

# Build APK
./gradlew assembleDebug

# Install to device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Configure LLM Provider

Open app → Settings → Select or add custom provider

Recommended: Use Doubao-Seed-1.6-vision or Doubao-Seed-2.0-pro from ByteDance Volcengine Platform. New users receive free trial credits. Enable the model in the backend and create an API key, then fill in the configuration in the app. Volcengine base URL: https://ark.cn-beijing.volces.com/api/v3

**Custom Provider Configuration Example (Local Ollama):**
- Name: `Ollama Local`
- Base URL: `http://192.168.1.100:11434/v1`
- Model: `qwen2.5-vl:7b`
- API Key: (leave empty)
- Support Vision: Enabled

### 3. Grant Permissions

1. Enable accessibility service: Settings → Accessibility → TapPilot → Enable
2. Screen recording permission will be requested when executing tasks for the first time

### 4. Execute Tasks

Enter task description in the main interface, then click "Execute Task".

## Settings Options

| Option | Description | Default Value |
|--------|-------------|---------------|
| Execution Speed | Delay between operations (milliseconds) | 500ms |
| Max Execution Steps | Maximum number of operations per task | 20 steps |
| Enable Screenshot Analysis | Whether to send screenshots to VLM | Enabled |

## Technical Architecture

```
app/
├── data/
│   ├── local/          # Local storage (PreferencesManager)
│   └── remote/         # LLM API calls
│       └── provider/   # Multi-provider support
├── domain/
│   ├── model/          # Data models (ActionStep, etc.)
│   └── usecase/        # Business logic
├── presentation/
│   ├── main/           # Main interface
│   └── settings/       # Settings interface
├── service/
│   ├── AutomationAccessibilityService  # Accessibility service
│   └── ScreenCaptureService            # Screen capture service
└── util/
    ├── ActionExecutor  # Action executor
    ├── ActionParser    # JSON parser
    └── ScreenshotCapture  # Screenshot tool
```

## How It Works

1. **Screenshot** - Capture current screen
2. **Analysis** - Send screenshot and task description to VLM
3. **Decision** - VLM returns next action (with precise coordinates)
4. **Execution** - Execute click/type operations through accessibility service
5. **Loop** - Repeat above steps until task completion or maximum steps reached

VLM receives the history of executed actions to avoid repetitive无效 operations. When the task is complete or stuck, it returns an empty array `[]` to end execution.

## Dependencies

- Kotlin 1.9.0
- Retrofit 2.9.0 + OkHttp 4.11.0
- Kotlin Coroutines 1.7.3
- AndroidX Lifecycle 2.6.2
- Timber 5.0.1
- Gson 2.10.1

## License

MIT License

## Acknowledgments

This project was primarily developed with the assistance of Qoder.
