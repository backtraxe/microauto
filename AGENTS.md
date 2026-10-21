# AGENTS.md

## 项目概览

MicroAuto 是一个非 root、纯本机运行的 Android 界面自动化工具。用户用 JavaScript 编写脚本，脚本通过无障碍服务查找控件、点击、滑动、启动应用，并支持每天定时执行。仓库只有一个 Gradle module：`:app`。

- 语言：Kotlin，经典 View + ViewBinding + Material 3（**不是 Compose**）
- JS 引擎：Rhino 1.7.15（解释模式）
- minSdk 26 / compileSdk 36 / targetSdk 36，JDK 17，Gradle 8.11.1，AGP 8.10.1，Kotlin 2.1.20
- 版本号自动生成：`versionName` 取最近 Git tag（不在 tag 上时追加 `-dev`），`versionCode` 取提交数
- 用户文档与界面文案均为中文；提交信息也使用中文

## 构建与验证

需要 JDK 17 和 Android SDK（platform 36、build-tools 36.0.0）。`local.properties` 已指向本机 SDK。

```bash
./gradlew :app:testDebugUnitTest   # JVM 单元测试
./gradlew :app:lintDebug           # Android Lint
./gradlew :app:assembleDebug       # 构建 debug APK
./gradlew :app:connectedDebugAndroidTest   # 仪器化测试，需要模拟器/真机
```

- debug APK 输出：`app/build/outputs/apk/debug/MicroAuto-<versionName>.apk`（文件名在 `app/build.gradle.kts` 中定制）。
- macOS Apple Silicon 一键跑模拟器测试：`export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools && ./scripts/run-emulator-tests.sh`。
- CI 使用 `scripts/ci-instrumentation.sh` 跑仪器化测试并收集 logcat/dropbox；该脚本面向 CI/已连接设备。
- CI（`.github/workflows/android.yml`）：push/PR 跑单元测试、Lint、debug 构建和 API 35 模拟器仪器化测试；推送 `v*` tag 时构建 release、校验签名并创建 GitHub Release。
- 提交前至少跑 `:app:testDebugUnitTest` 和 `:app:assembleDebug`；改动涉及 Android 框架行为时再跑仪器化测试。

## 代码结构

```
app/src/main/java/com/microauto/
  accessibility/  无障碍服务、节点操作、手势、亮屏/锁屏检测
  api/            脚本可见的原生桥接（AutomationBridge / UiSelector / UiObject）
  core/           选择器、节点抽象、脚本命名、定时计算（纯 Kotlin，可 JVM 单测）
  schedule/       AlarmManager 定时、开机/时区变化恢复
  script/         Rhino 运行时、脚本仓库、前台服务、日志
  ui/             脚本列表、编辑页（撤销/查找替换）、版本历史、适配器、语法高亮
app/src/main/assets/prelude.js   Auto.js 风格的脚本 API 层
app/src/test/                    JVM 单元测试
app/src/androidTest/            仪器化测试
```

职责边界（修改时请保持）：

- `core/` **不能依赖 Android 框架**，否则无法在 JVM 上单测。新增纯逻辑优先放这里。
- `accessibility/` 只负责与系统交互，不关心脚本语法。
- `script/` 负责执行生命周期，不直接操作节点。
- `api/` 是脚本与原生能力的唯一桥梁；`AutomationBridge` 的 public 方法会暴露给 Rhino。
- `ui/` 只做展示与用户操作。

## 运行时机制

- 执行链路：`ScriptService`（前台服务，`specialUse` 类型）→ 后台线程 → `ScriptRunner` → `ScriptEngine`（Rhino）→ 先执行 `assets/prelude.js`，再执行用户脚本。`ScriptEngine` 通过 `native` 对象注入 `AutomationBridge`。
- Rhino 配置：`optimizationLevel = -1`（解释模式），`VERSION_1_8`，作用域使用 `initSafeStandardObjects()`，脚本不能直接访问 `java` / `Packages`。`ScriptSandbox` 通过自定义 `WrapFactory` + 成员白名单包装原生对象，隐藏 `getClass`、`class`、反射、ClassLoader 和 `wait`/`notify` 等 Object 成员。`prelude.js` 保持 ES5 语法，避免 Android 上的兼容问题。
- 超时/停止：`ScriptEngine` 设置指令观察器（阈值 10,000），`ScriptExecutionGuard` 在纯 JS 死循环中也能检查 deadline 和取消标志；`ScriptRunner.cancel()` 同时置取消标志并中断线程。
- 单脚本串行：`ScriptService` 与 `ScriptRunner` 都有运行中检查，同一时刻只允许一个脚本；`RunState`/`RunLog` 用 `StateFlow` 供 UI 订阅，`RunState.activeScriptId` 标记当前脚本供首页行内运行状态使用。运行通知（含结果通知和前台服务常驻通知）只带脚本名和通用状态，并设置 `VISIBILITY_SECRET`，不读取日志正文。
- 超时：脚本总时限 10 分钟，由指令观察器和 `AutomationBridge` 的桥接调用检查共同保证；`sleep` 会截断到剩余时间。页面必须亮屏且已解锁，否则 `ScriptRunner` 直接拒绝执行。
- 无障碍：`AutomationService.instance` 持有 `rootInActiveWindow`；选择器 BFS 遍历，最多 2000 个节点、返回 100 个结果，轮询间隔 100ms。点击策略为 `ACTION_CLICK` → 最多 5 层可点击祖先 → 坐标手势。`NodeDump` 生成节点树，`isPassword` 节点的文本和描述都输出为 `<hidden>`。
- 手势：`GestureController` 把 `dispatchGesture` post 到主线程，并用 `CountDownLatch` 等待回调；脚本线程不可阻塞主线程。
- 存储：`filesDir/scripts/` 下 `index.json`（脚本元数据）、`folders.json`、`versions.json`、`<uuid>.js`、`versions/<scriptId>/<versionId>.js`。索引和脚本文件通过 `.tmp` + rename 原子写入；`ScriptRepository` 用进程级内存缓存减少磁盘读取，写操作后更新缓存，`clearCache()` 用于测试/恢复。运行日志在 `filesDir/latest.log`（内存最多 20k 字符）。
- 备份：`ScriptBackup` 将整个 `scripts/` 打包为 zip（`ScriptBackupCodec` 负责纯文件编解码、条目白名单、zip-slip 校验、ID/清单完整性校验和 50 MB 上限）；恢复分两阶段：先校验临时目录，再替换；旧目录保留到新仓库加载成功，失败会回滚，成功后取消所有定时。`ScriptPaths` 负责 ID 格式与 canonical path 包含校验。`MicroAutoApp` 启动时先调用 `ScriptBackup.recoverInterruptedRestore`，处理恢复过程中被强杀留下的 `scripts-before-restore`。
- 定时：每个脚本可单独定时，配置以 JSON 存在 `SharedPreferences` 名 `schedule`（旧版单条配置会自动迁移）。`ScriptScheduler` 为每条定时分配 `alarmId`，daily requestCode 为 `10000 + alarmId`，retry 为 `1000000 + alarmId`；无精确闹钟权限时降级为非精确闹钟。`BootReceiver` 在开机、改时间、改时区后重注册；息屏/锁屏时 `ScheduleTriggerPolicy` 会在 1 小时内每 2 分钟重试，超窗后通知跳过。

## 修改脚本 API 的固定流程

新增或调整 JS 可见 API 时，需要同步修改：

1. `app/src/main/java/com/microauto/api/AutomationBridge.kt`（原生实现）或 `UiSelector.kt` / `UiObject.kt`
2. `app/src/main/assets/prelude.js`（全局函数或选择器方法）
3. `app/src/test/java/com/microauto/script/PreludeSmokeTest.kt`（`FakeNative` 按需补齐方法，并加断言）
4. `docs/tutorial.md` 第 13/14 节（二次开发步骤与 API 速查）
5. 若是新的常用内建函数，考虑加入 `core/JsTokenizer.kt` 的 builtin 列表以支持语法高亮

`PreludeSmokeTest` 会用真实 Rhino 执行 `prelude.js`，是验证 JS 层行为的主要手段。

## 编码约定

- Kotlin 官方风格：4 空格缩进、多行参数尾随逗号、简单函数用表达式体。
- 数据模型用不可变 `data class`，更新用 `copy()`；列表项用 `sealed class`（如 `LibraryItem`）。
- 非致命失败沿用 `runCatching { ... }.getOrDefault(...)` 风格，不吞掉需要上报的异常。
- 用户可见文案、Toast、日志使用中文；标识符和少量注释使用英文。仓库整体注释很少，不要为显而易见的代码补注释。
- 大部分界面文案直接写在 Kotlin 中；`strings.xml` 只放应用级名称/无障碍描述。新增界面文案保持这一现状即可。
- UI 使用 ViewBinding（`ActivityXxxBinding`）和 Material 组件；`android.nonTransitiveRClass=true`，引用库资源时用全限定名（如 `com.google.android.material.R.attr.*`）。
- 边到边统一调用 `ui/EdgeToEdge.kt`；显隐用 `androidx.core.view.isVisible`。
- 没有 DI 框架：直接 `ScriptRepository(context)`；进程级状态才用 `object` 单例。
- 日期时间显示统一走 `core/DateFormats`（`yyyy/MM/dd HH:mm:ss`，24 小时制）；定时时间输入仍用 `HH:mm`。
- 运行时权限/系统设置入口遵循现有模式：首页「运行环境」面板统一提供无障碍、通知、精确闹钟、电池优化入口，编辑页定时 sheet 也提供精确闹钟入口。

## 测试约定

- JVM 测试：JUnit 4，放在 `app/src/test`，只测试 `core/` 等纯逻辑；文件名 `*Test.kt`。
- 仪器化测试：`AndroidJUnit4` + `ApplicationProvider`，每个用例先调用 `TestData.reset()` 清空 `filesDir/scripts`、旧 `script.js` 和 `schedule` 偏好。
- UI 测试使用 Espresso；已有 `MainActivityTest`、`EditorActivityTest`、`VersionsActivityTest`。
- 新增纯逻辑时补 JVM 测试；涉及 `ScriptRepository`、定时偏好、Activity 行为时补 `androidTest`。

## 文档同步

- `README.md`：功能列表、构建/测试/签名/发布说明。新增用户可见功能时更新。
- `docs/tutorial.md`：面向用户的教程与 API 速查，是脚本 API 的权威说明。
- `docs/superpowers/specs/2026-10-20-microauto-design.md`：早期设计稿，部分类名与现状不一致，以代码和本文件为准。
- 修改构建命令、目录结构或约定时，同步更新本 `AGENTS.md`。

## 签名与发布

- debug 使用仓库内固定 `signing/debug.keystore`（alias `androiddebugkey`，密码 `android`）。**不要替换或删除**，本地与 CI 的 debug 签名需要一致才能覆盖安装。
- release 从根目录 `keystore.properties` 或环境变量 `RELEASE_KEYSTORE_PATH` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` 读取签名信息；找不到 release keystore 时回退到 debug 签名。
- `keystore.properties`、`*.jks`、`*.keystore` 已 gitignore（debug keystore 除外）。不要读取、打印或提交这些密钥文件。
- 发版：直接打 `v*` tag 推送，由 CI 构建并创建 Release；`versionCode`/`versionName` 自动生成，无需手改。
- `app/proguard-rules.pro` 保留 Rhino 反射所需类，不要删除。

## 常见坑

- `prelude.js` 是脚本 API 的唯一入口。只改 `AutomationBridge` 而不改 prelude，脚本侧看不到新能力。
- Rhino 运行在后台线程，但 `Toast`、`dispatchGesture` 必须回主线程；不要从脚本线程直接操作 View。
- 选择器匹配是 AND 关系，`findOne` 默认 10 秒超时并返回 `null`（与 Auto.js 的无限等待不同）。
- `automation service` 依赖用户在系统设置中手动开启；测试和运行前先确认 `AccessibilityUtils.isEnabled(context)`。
- 定时触发受 Doze 和厂商保活策略影响；每个脚本独立定时，删除脚本或关闭定时时要同时取消对应的 daily/retry 闹钟并清理 pending 状态。
- 构建工具版本固定为 build-tools 36.0.0；升级 SDK/AGP 时同步更新 CI 的 `sdkmanager` 安装列表和 README 的环境要求。
