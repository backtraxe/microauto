# MicroAuto 设计规格

- 日期：2026-10-20
- 状态：待评审
- 项目目录：`microauto/`

## 1. 目标

MicroAuto 是一个完全在 Android 手机上运行、无需 root、无需电脑的自动化工具。用户用 JavaScript 编写脚本，通过无障碍服务读取当前界面并执行点击、长按、滑动、文本输入，配合定时任务完成自动签到等固定流程。

成功标准：

1. 安装 APK 后，开启无障碍服务即可运行脚本。
2. 示例脚本能完成「启动 App → 找到签到入口 → 点击签到」。
3. 定时任务在设定时间触发，并留下可查看的运行日志。
4. `./gradlew :app:assembleDebug` 与 JVM 单元测试通过。

## 2. 非目标（v1）

- 图像识别、OCR、找图找色。
- 悬浮窗控制台、可视化节点选择器。
- 多脚本并发执行。
- npm、模块加载、脚本市场。
- 将脚本打包为独立 APK。
- 依赖 root、Shizuku、adb 或电脑。
- iOS、桌面端。

## 3. 约束

- 非 root，仅使用 Android 公开 API。
- 脚本与调度完全在设备本地运行，不依赖网络服务。
- 跨应用 UI 自动化只能通过无障碍服务实现。
- 执行 UI 操作时设备必须亮屏且已解锁；这是平台限制，不做绕过。
- 首版只支持单脚本串行执行。

## 4. 目标环境与工具链

| 项 | 取值 |
|---|---|
| minSdk | 26（Android 8.0） |
| compileSdk / targetSdk | 35 |
| 语言 | Kotlin |
| UI | 经典 View + ViewBinding + Material Components |
| JS 引擎 | Rhino 1.7.15（纯 Java，解释模式） |
| 构建 | JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21 |
| 测试 | JUnit 4（JVM 单元测试） |

## 5. 总体架构

单 Gradle module，包结构按职责划分：

```
app/src/main/java/com/microauto/
  MicroAutoApp.kt          应用入口，初始化通知渠道
  accessibility/
    AutomationService.kt   无障碍服务，持有 rootInActiveWindow
    NodeActions.kt         点击、文本、滚动、全局动作
    GestureController.kt   手势派发与回调
    ScreenState.kt         亮屏/锁屏检测
  core/
    NodeSnapshot.kt        节点快照抽象（纯 Kotlin）
    Selector.kt            选择条件与匹配逻辑（纯 Kotlin）
    SelectorMatcher.kt     BFS 查找
    ScheduleTime.kt        下次触发时间计算（纯 Kotlin）
  script/
    ScriptStore.kt         脚本与元数据读写
    ScriptEngine.kt        Rhino 上下文与绑定注入
    ScriptRunner.kt        单次执行生命周期
    ScriptService.kt       前台服务
    LogStore.kt            运行日志落盘与裁剪
  schedule/
    ScriptScheduler.kt     AlarmManager 注册/取消
    ScheduleReceiver.kt    闹钟触发
    BootReceiver.kt        开机、时区变化重注册
  api/
    AutomationBridge.kt    Rhino 可见的原生函数集合
    UiSelector.kt          选择器包装
    UiObject.kt            节点包装
  ui/
    MainActivity.kt        脚本列表、权限状态
    EditorActivity.kt      编辑、运行、日志、定时配置
    SettingsActivity.kt    无障碍/电池/闹钟设置入口
app/src/main/assets/prelude.js   Auto.js 风格 API 的 JS 层
app/src/test/java/com/microauto/ 选择器、调度、引擎单测
```

职责边界：

- `core` 不依赖 Android 框架，可直接在 JVM 上单测。
- `accessibility` 只负责与系统交互，不关心脚本语法。
- `script` 负责执行生命周期，不直接操作节点。
- `api` 是脚本与原生能力的唯一桥梁。
- `ui` 只做展示与用户操作。

数据流：

1. 用户在编辑器保存脚本，`ScriptStore` 写入 `filesDir/scripts/`。
2. 手动运行或定时触发启动 `ScriptService`。
3. `ScriptRunner` 创建 Rhino 上下文，注入 `AutomationBridge`，执行 `prelude.js` 后执行用户脚本。
4. 脚本调用选择器时，`AutomationService` 读取当前窗口节点树并轮询匹配。
5. 匹配到的节点由 `UiObject` 包装并执行动作。
6. 日志写入内存环形缓冲与文件，通知栏展示运行状态。

## 6. 脚本运行时

### 6.1 Rhino 配置

- `Context.enter()`，`initStandardObjects()`。
- `setOptimizationLevel(-1)`：解释模式，避免 Android 上的字节码生成问题。
- `setLanguageVersion(Context.VERSION_ES6)`；公开 API 与 `prelude.js` 使用 ES5 语法保证兼容。
- 每次运行创建独立 `Context` 与作用域，运行结束 `Context.exit()`。
- 脚本在独立线程执行，主线程只负责 UI 与通知。

### 6.2 执行生命周期

- 同一时刻只允许一个脚本运行，由进程内锁保护。
- 单次执行上限 10 分钟，超时中断线程并记录错误。
- 用户可从编辑器或通知栏停止执行。
- 停止时取消未完成的手势、释放 WakeLock、停止前台服务。

### 6.3 公开 API

`prelude.js` 暴露以下函数与对象：

| API | 语义 |
|---|---|
| `text(v)` / `textContains(v)` / `textMatches(re)` | 文本选择条件，可链式组合，多个条件为 AND |
| `id(v)` / `idContains(v)` | resource-id 条件 |
| `desc(v)` / `descContains(v)` | contentDescription 条件 |
| `className(v)` | 类名条件 |
| `selected()` / `enabled()` / `clickable()` | 状态条件 |
| `.findOne(timeout = 10000)` | 轮询直到命中，返回 `UiObject` 或 `null` |
| `.find()` | 立即返回 `UiObject` 数组 |
| `.exists()` | 立即返回布尔值 |
| `.waitFor(timeout = 10000)` | 等待出现，返回布尔值 |
| `UiObject.click()` | 点击，成功返回 `true` |
| `UiObject.longClick()` | 长按 |
| `UiObject.setText(text)` | 设置文本 |
| `UiObject.scrollForward()` | 向前滚动 |
| `UiObject.bounds()` | 返回 `{left, top, right, bottom}` |
| `UiObject.text()` | 返回节点文本 |
| `click(x, y)` / `longClick(x, y)` | 坐标点击/长按 |
| `swipe(x1, y1, x2, y2, duration = 300)` | 滑动 |
| `press(x, y, duration = 500)` | 按住 |
| `sleep(ms)` | 阻塞当前脚本线程 |
| `toast(msg)` | 主线程弹提示 |
| `log(msg)` / `logError(msg)` | 写入运行日志 |
| `exit()` | 立即结束当前脚本 |
| `launchApp(pkg)` | 按包名启动应用，返回布尔值 |
| `currentPackage()` | 当前前台包名 |
| `waitForPackage(pkg, timeout = 10000)` | 等待前台切换 |
| `back()` / `home()` / `recents()` | 无障碍全局动作 |
| `dump()` | 将当前窗口节点树写入日志，用于调试 |

选择器行为：

- 默认轮询间隔 100ms，从当前 `rootInActiveWindow` 重新遍历。
- 遍历顺序为 BFS，返回第一个匹配节点。
- `findOne()` 默认 10 秒超时，与 Auto.js 的无限等待不同，需在文档中注明。
- 选择器对象不可变，链式调用返回新对象。

### 6.4 点击策略

1. 对目标节点执行 `ACTION_CLICK`。
2. 失败时向上查找可点击祖先，最多 5 层。
3. 仍失败则按节点屏幕坐标中心派发点击手势。
4. 节点不可见或 bounds 为空时返回 `false`。

### 6.5 示例脚本

```js
launchApp("com.example.app");
waitForPackage("com.example.app", 10000);
sleep(3000);

if (text("跳过").exists()) {
  text("跳过").findOne(2000).click();
}

var entry = textMatches(/签到|每日签到/).findOne(5000);
if (!entry) {
  toast("未找到签到入口");
  exit();
}
entry.click();

var btn = textMatches(/立即签到|签到/).findOne(5000);
if (btn) {
  btn.click();
  toast("签到完成");
} else {
  toast("未找到签到按钮");
}
```

## 7. 无障碍层

### 7.1 服务配置

`res/xml/accessibility_service_config.xml`：

- `android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"`
- `android:accessibilityFeedbackType="feedbackGeneric"`
- `android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"`
- `android:canRetrieveWindowContent="true"`
- `android:canPerformGestures="true"`
- `android:notificationTimeout="100"`

### 7.2 节点操作

- 文本：`ACTION_SET_TEXT` + `Bundle`。
- 滚动：`ACTION_SCROLL_FORWARD`。
- 全局动作：`performGlobalAction(GLOBAL_ACTION_BACK/HOME/RECENTS)`。
- 手势：`dispatchGesture()`，点击为 50ms 按住，滑动按传入时长插值。

### 7.3 屏幕状态

执行前检查：

- `PowerManager.isInteractive`：屏幕是否点亮。
- `KeyguardManager.isKeyguardLocked`：是否锁屏。

任一不满足则终止本次执行，日志与通知说明原因。手动运行时用 Toast 提示用户先解锁。

## 8. 用户界面

### 8.1 单页主界面

v1 只做单脚本、单页界面，不引入脚本列表和独立设置页。

- 顶部状态：无障碍是否开启、精确闹钟是否可用、定时摘要。
- 设置入口：无障碍、省电、闹钟三个按钮。
- 等宽字体脚本编辑框。
- 操作按钮：保存、运行、停止、看界面（执行 `dump()`）。
- 每天定时：`HH:mm` 输入框、启用开关、保存定时。
- 日志面板：只读、可滚动，显示最近一次运行输出。

### 8.2 交互约定

- 首次启动申请通知权限。
- 未开启无障碍时运行会跳转系统无障碍设置。
- 运行中禁用运行/看界面按钮，启用停止按钮。

## 9. 调度与后台执行

### 9.1 调度模型

v1 使用一份全局每日定时配置：

```
{
  enabled: Boolean,
  hour: Int,          // 0-23
  minute: Int         // 0-59
}
```

### 9.2 注册与触发

- 保存后计算下一次触发时间，用 `AlarmManager` 注册。
- Android 12+ 且 `canScheduleExactAlarms()` 为真时用 `setExactAndAllowWhileIdle`，否则用 `setAndAllowWhileIdle`。
- `ScheduleReceiver` 收到广播后启动 `ScriptService`，并注册下一次闹钟。
- `BootReceiver` 监听 `BOOT_COMPLETED`、`TIME_SET`、`TIMEZONE_CHANGED`，重新注册所有启用项。
- 触发时若已有脚本在运行，记录跳过日志，不排队。

### 9.3 前台服务

- `ScriptService` 以前台服务运行，通知渠道「脚本运行」。
- 通知内容：脚本名、运行状态、停止按钮。
- Android 14+ 使用 `specialUse` 前台服务类型，并声明 subtype 属性。
- 运行期间持有 `PARTIAL_WAKE_LOCK`，结束后释放。

### 9.4 设备要求

文档明确说明：

- 需插电并保持屏幕常亮，建议开启开发者选项「充电时不锁定屏幕」。
- 需在系统设置中允许自启动、关闭电池优化。
- 侧载应用在 Android 13+ 需在应用信息中允许「受限设置」才能开启无障碍。

## 10. 存储

```
filesDir/
  script.js      // 当前脚本
  latest.log     // 最近一次运行日志
```

定时配置存在 SharedPreferences（`schedule` 文件）。日志在内存中保留最近 20,000 字符，文件超过 256KB 时裁剪到约 10,000 字符。

## 11. 权限

| 权限 | 用途 | 申请方式 |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | 无障碍服务 | 服务声明，用户在系统设置开启 |
| `FOREGROUND_SERVICE` | 运行脚本 | 普通权限 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 前台服务类型 | 普通权限 |
| `POST_NOTIFICATIONS` | 运行通知 | Android 13+ 运行时申请 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复定时 | 普通权限 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟 | Android 12+ 特殊访问授权 |
| `WAKE_LOCK` | 运行期间保持 CPU 唤醒 | 普通权限 |

不申请：悬浮窗、存储读写、位置、通讯录、`QUERY_ALL_PACKAGES`。

应用可见性通过 `<queries>` 声明 launcher intent，用于按包名启动应用。

## 12. 错误处理与日志

- JS 语法错误、运行时异常：记录堆栈，通知标记失败，Toast 提示。
- 无障碍服务未开启：阻止运行并跳转设置。
- 脚本执行中无障碍断开：立即终止并记录。
- 节点操作失败：重试一次，仍失败返回 `false`，由脚本自行处理。
- 手势被系统取消：记录原因，不自动重试。
- 执行超时：中断线程，记录「执行超过 10 分钟」。
- 日志同时写入内存缓冲与文件；`RunStateHolder` 用 `StateFlow` 暴露运行状态与增量日志，UI 直接订阅。

## 13. 测试策略

### 13.1 JVM 单元测试

- `SelectorMatcher`：文本、id、desc、组合条件、BFS 顺序。
- `ScheduleTime`：每天、指定星期、跨天、时区边界。
- `ScriptEngine` 冒烟：Rhino 加载 `prelude.js`，用假 bridge 执行脚本，断言调用序列。
- `LogStore`：裁剪策略。

### 13.2 手工验收清单

1. 安装 APK，开启无障碍，状态条显示已开启。
2. 新建脚本，运行 `toast("hello")`，看到提示与日志。
3. 运行 `dump()`，日志出现当前界面节点树。
4. 运行示例脚本，完成一次真实签到。
5. 配置 1 分钟后定时，锁屏外场景下确认触发。
6. 锁屏状态触发，确认记录「屏幕未解锁」而不是崩溃。
7. 停止运行，确认前台服务与通知消失。
8. 重启手机，确认定时任务重新注册。

### 13.3 构建验证

```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 14. 构建环境

当前 macOS 环境已有 JDK 17，但没有 Android SDK。要完成构建验证，需要：

1. 安装 Android 命令行工具（Homebrew cask 或官方 zip）。
2. 安装 `platform-tools`、`platforms;android-35`、`build-tools;35.0.0`。
3. 接受 SDK licenses。
4. 生成 Gradle wrapper 并执行构建。

若暂不安装 SDK，只能交付源码，无法保证编译与单测通过。

当前 Mac 未连接 Android 设备，端上验收由用户手动安装 APK 完成。

## 15. 风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 国产 ROM 杀后台 | 定时不触发 | 引导自启动/省电白名单；前台服务；开机重注册 |
| Doze 延迟 | 触发时间漂移 | 精确闹钟授权；失败时记录实际触发时间 |
| 应用检测无障碍 | 目标 App 拒绝操作 | 不做绕过，文档说明适用范围 |
| 节点定位脆弱 | 脚本失效 | 提供 `dump()` 调试；选择器支持多条件与正则 |
| Rhino 性能 | 复杂脚本卡顿 | 解释模式 + 简单脚本定位；后续可换 QuickJS |
| 侧载无障碍受限 | 无法开启服务 | 文档说明 Android 13+ 受限设置步骤 |
| 无真机连接 | 无法端到端验证 | 用户手动安装 APK 验收 |

## 16. 里程碑

1. **工程骨架**：Gradle 配置、空 App、可编译。
2. **无障碍层**：服务、节点遍历、`dump()`。
3. **脚本运行时**：Rhino、prelude、选择器、动作 API。
4. **编辑器与日志**：脚本 CRUD、运行/停止、日志展示。
5. **定时与前台服务**：AlarmManager、开机恢复、通知。
6. **测试与交付**：单测、示例脚本、APK、使用说明。

## 17. 待确认

1. 是否同意安装 Android SDK 以完成编译与单测验证。
2. v1 范围是否接受；是否有必须加入首版的能力（如 OCR、悬浮窗）。
