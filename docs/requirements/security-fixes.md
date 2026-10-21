# MicroAuto 安全审计与修复需求

- 审计对象：`MicroAuto` v0.7.0（`main`，提交 `81b0555`）
- 审计日期：2026-10-21
- 审计范围：`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/microauto/**`、`app/src/main/assets/prelude.js`、`app/src/main/res/xml/**`、`app/build.gradle.kts` 的签名配置
- 审计方式：静态代码审查 + 使用 Rhino 1.7.15 独立 JVM 探针验证反射行为；**没有修改任何 app 代码，没有运行 Gradle 构建，没有连接设备，没有 commit/tag/push**

## 结论摘要

本次审计确认 **1 个 P0、3 个 P1、7 个 P2**，共 11 条发现。

最关键的三个结论：

1. **P0：脚本沙箱可被完全逃逸。** `native` 通过 `Context.javaToJS` 暴露为 Java 对象，脚本调用 `native.getClass()` 即可获得 `java.lang.Class`，再经 `forName` / `getMethod` / `invoke` 或私有字段反射访问任意 Java API。`initSafeStandardObjects()` 只隐藏了顶层 `java` / `Packages`，无法阻止实例反射。这与 `docs/tutorial.md:516` 对用户承诺的安全边界直接冲突。
2. **P1：备份恢复缺少完整性校验，可能一次清空全部脚本和版本历史。** `ScriptBackupCodec.import()` 只要求 `index.json` 能解析，不检查索引中的脚本文件/版本文件是否齐全；`ScriptBackup.restore()` 在校验完成前就把当前 `scripts/` 移到 `scripts-before-restore`，成功换入后立即删除旧目录。一个只含 `index.json` 的 zip，或索引指向缺失文件的 zip，可以造成不可逆的数据丢失。
3. **P1：恢复备份后已有定时不会被重新确认。** 定时配置存在 `SharedPreferences`，备份只包含 `scripts/`；恢复后原有 schedule 仍按 `scriptId` 读取恢复后的新内容并在下次闹钟自动执行。攻击者提供一份带有已知脚本 ID 的备份，即可替换已定时脚本的内容，而用户只看到“覆盖脚本”的泛化提示。

## 1. 威胁模型与假设

### 1.1 资产

- **屏幕内容**：无障碍服务可读取当前活动窗口的节点树，可能包含聊天内容、账号、密码、验证码、支付信息。
- **脚本与版本**：脚本源码可能硬编码 Cookie、Token、手机号、账号密码；版本历史同样保存这些内容。
- **运行日志**：`filesDir/latest.log` 保存最近日志；`dump()` 输出可包含整个界面的文本。
- **定时配置**：`SharedPreferences("schedule")` 保存脚本 ID、执行时间和闹钟 ID。
- **无障碍能力本身**：读屏、点击、滑动、返回/主页/最近任务、启动任意已安装应用。
- **应用私有存储**：`filesDir/scripts`、`shared_prefs`、`databases`、备份临时目录。

### 1.2 信任边界

1. **用户在 UI 上的主动操作**（编辑、点击运行、选择导入文件、确认恢复）视为可信意图；但用户选择的**文件内容**（`.js`、`.zip`）不可信。
2. **导入的第三方脚本**是不可信代码。用户可能从聊天群、论坛、GitHub 下载脚本；脚本不应因为“被导入”就获得超出产品承诺的能力。
3. **其他应用**不可信。它们可以向 exported 组件发送 Intent/广播，但不能读取 MicroAuto 的私有目录。
4. **SAF 文档提供方**不可信。它可能返回任意长度、任意内容的流，甚至无限流。
5. **系统组件**（AlarmManager、BOOT_COMPLETED、无障碍框架）按 Android 安全模型视为可信；`AutomationService` 由 `BIND_ACCESSIBILITY_SERVICE` 签名权限保护。

### 1.3 攻击者能力与前提

- 能诱导用户导入恶意 `.js` 或恢复恶意 `.zip`（社会工程、群文件、伪装成“签到脚本/配置备份”）。
- 能向 `MainActivity` 等 exported 组件发送 Intent，受 Android protected broadcast 限制。
- 不能 root、不能读取应用私有目录、不能绕过 SAF 的文件选择。
- 应用**未声明 `INTERNET` 权限**，因此脚本不能直接通过 Java 网络 API 外联；但沙箱逃逸后仍可通过启动其他应用、发送隐式 Intent、写入应用可访问的文件等方式外传数据。
- 定时脚本只在屏幕点亮且已解锁时执行（`ScheduleTriggerPolicy`），但执行时不需要用户再次确认脚本内容。

### 1.4 设计取舍与明确不视为漏洞的行为

以下行为是产品设计的一部分，本报告不把它们本身当作漏洞：

- 脚本可以读取当前屏幕节点、点击/滑动、启动应用；这是 MicroAuto 的核心功能。它是高权限工具，用户应只运行可信脚本。
- 脚本和日志以明文保存在应用私有目录；`allowBackup="false"`，不参与云备份/设备迁移。若需要防取证或防 root，需要额外加密，超出本报告范围。
- 用户自己编写并主动运行的破坏性脚本（例如删除自己账号内容）属于用户自担风险。
- Debug 构建可被 `run-as` 调试、debug keystore 公开，这是 Android 调试构建的常规行为；但 **release 构建回退到公开 debug key** 是单独的安全问题（SEC-010）。
- 脚本没有签名/来源校验；是否允许“只运行可信脚本”属于产品策略。本报告要求的是：**导入/恢复操作不能静默扩大执行面**，以及**脚本不能突破到 Java/文件/进程层**。

### 1.5 审计方法与限制

- 逐文件阅读了 Manifest、脚本运行时、桥接层、仓库/备份、定时、通知、主要 UI。
- 用 Rhino 1.7.15 jar 在 JVM 上验证了 `initSafeStandardObjects()` 下已暴露 Java 对象的反射链；验证结果见 SEC-001。
- 未运行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:connectedDebugAndroidTest`，避免与后续修复 agent 的 Gradle/模拟器任务冲突；因此本文档中的动态结论仅限 Rhino JVM 探针，Android 设备行为需要修复后按第 6 节验证。
- 行号基于上述提交；修复时若代码移动，以符号名（类/方法）为准。

## 2. 发现汇总表

| ID | 等级 | 文件:行号 | 问题 | 影响 | 修复建议 | 验收标准 |
| --- | --- | --- | --- | --- | --- | --- |
| SEC-001 | P0 | `app/src/main/java/com/microauto/script/ScriptEngine.kt:31-35`<br>`app/src/main/java/com/microauto/script/ScriptScope.kt:7` | `Context.javaToJS` 把 `AutomationBridge` 暴露为 Java 对象；`native.getClass()` 可取得 `Class` 并反射到任意 Java API | 导入的恶意脚本可执行任意 Java 代码、读应用私有文件/SharedPreferences、启动进程/其他应用，完全突破“仅脚本 API”的边界 | 移除 `javaToJS`，改为只暴露白名单 `Scriptable` 门面；必须保留 Java 包装时使用自定义 `WrapFactory` + 成员白名单，显式封禁 `getClass`/`class`/反射/`wait` 等 Object 成员；保留 `initSafeStandardObjects` 并加回归测试 | `typeof native.getClass === "undefined"`（或访问抛错）；`native.class` 不可用；`native.getClass().forName("java.lang.Runtime")` 无法执行；`UiObject`/`Bounds`/数组等返回值同样无 `Class` 泄漏；`PreludeSmokeTest` 全绿 |
| SEC-002 | P1 | `app/src/main/java/com/microauto/script/ScriptBackupCodec.kt:35-70`<br>`app/src/main/java/com/microauto/script/ScriptBackup.kt:17-45`<br>`app/src/main/java/com/microauto/script/ScriptIndexCodec.kt:87-103` | 恢复前只校验 `index.json` 可解析（单条损坏记录计入 `skipped` 也会被接受），不校验索引条目对应的 `.js`/版本文件是否齐全、条目数量/大小是否合理；换入新目录后立即删除旧目录 | 只含 `index.json` 的 zip 或索引指向缺失文件的 zip 可清空全部脚本/版本；恢复过程中进程被杀也可能丢失当前数据 | 两阶段恢复：解压到临时目录后校验完整清单，全部通过再替换；校验每个索引/版本文件存在、ID 唯一、条目数/索引大小上限；旧目录保留到新仓库加载成功；加恢复互斥锁 | 只含 `index.json`、索引条目缺文件、重复 ID、超量条目的 zip 被拒绝且当前数据不变；有效备份可恢复；恢复中杀进程后下次启动可从 `scripts-before-restore` 恢复 |
| SEC-003 | P1 | `app/src/main/java/com/microauto/script/ScriptBackup.kt:17-45`<br>`app/src/main/java/com/microauto/schedule/ScheduleReceiver.kt:22-37` | 备份只包含 `scripts/`，恢复不取消/不重新确认 `SharedPreferences` 中的定时；恢复后已有 schedule 会按同一 `scriptId` 读取新内容并自动执行 | 攻击者提供带已知脚本 ID 的备份即可替换已定时脚本内容，下次闹钟无人工确认地执行；用户只看到“覆盖脚本”的提示 | 恢复时取消所有定时或标记受影响的定时并要求重新确认；在 schedule 中保存内容哈希，执行前校验；恢复后弹窗列出受影响的定时 | 恢复后没有任何定时自动执行“新内容”而未确认；受影响的定时被禁用并在 UI 中列出；测试覆盖“已有定时 + 恢复同 ID 脚本” |
| SEC-004 | P1 | `app/src/main/java/com/microauto/script/RunNotifications.kt:27-38`<br>`app/src/main/java/com/microauto/api/AutomationBridge.kt:119-143`<br>`app/src/main/java/com/microauto/script/RunLog.kt:28-38` | 运行完成通知的正文取 `RunLog` 最后一行；`RunLog` 包含 `dump()` 的完整界面文本和脚本日志；`dump()` 未按 `isPassword` 过滤 | 密码、验证码、聊天内容、Token 可能出现在通知栏/锁屏；日志文件长期保留这些内容 | 通知正文改为通用状态+脚本名，绝不带日志尾部；设置 `setVisibility(VISIBILITY_SECRET)`；`dump()` 对密码节点打码；日志改为可关闭/退出清理 | 完成通知不包含任何日志文本；`dump()` 对 `isPassword` 节点输出 `text=<hidden>`；关闭日志后 `latest.log` 不写入内容；相关测试通过 |
| SEC-005 | P2 | `app/src/main/java/com/microauto/script/ScriptBackupCodec.kt:66-70`<br>`app/src/main/java/com/microauto/script/ScriptIndexCodec.kt:13-21`<br>`app/src/main/java/com/microauto/script/ScriptRepository.kt:99-103` | 导入的 `id`/`scriptId` 只校验非空，直接拼进 `File(dir, "$id.js")`、`File(versionsDir, "$scriptId/...")`；`..` 可逃出 `scripts/` | 恶意备份可读取/删除/覆盖其他脚本或版本，并在 `shared_prefs`/`databases` 等目录创建 `.js` 文件；`.js` 后缀限制使其不能直接覆盖 XML/数据库，但已破坏仓库边界 | 对 ID 做格式白名单（UUID 或 `[A-Za-z0-9_-]{1,64}`）；所有文件访问做 canonical path 包含校验；导入时校验 ID 唯一、拒绝 `..`/分隔符/NUL；备份条目名也做白名单 | 含 `../`、`/`、`\`、NUL、重复 ID 的备份被拒绝；`read/delete/publishVersion/restoreVersion` 对越界路径返回失败；新增路径越界单元测试 |
| SEC-006 | P2 | `app/src/main/java/com/microauto/ui/MainActivity.kt:750-767`<br>`app/src/main/java/com/microauto/ui/EditorActivity.kt:464-469`<br>`app/src/main/java/com/microauto/script/ScriptService.kt:32-40` | 导入先 `readText()` 全量读入再检查 1 MB；1 MB 可含约 50 万行，导入后自动打开编辑器并在主线程 `joinToString` 生成全部行号；运行把完整脚本文本放进 Intent extra，编辑器侧未捕获 `TransactionTooLargeException` | 超大/无限 SAF 流可 OOM；恶意“换行脚本”导入即 ANR；大脚本点运行可能崩溃，定时大脚本静默启动失败 | 用有上限的流式读取（先限字节，再解码）；限制行数/行长度；行号惰性渲染；服务改为接收 `scriptId` 从仓库读内容或写临时文件；编辑器捕获启动异常并提示 | 100 MB 文档被正常拒绝且不 OOM；50 万行脚本不 ANR；2 MB 脚本运行不崩溃；新增 bounded reader/大文件测试 |
| SEC-007 | P2 | `app/src/main/java/com/microauto/ui/EditorActivity.kt:641-644`<br>`app/src/main/java/com/microauto/ui/EditorActivity.kt:141-146`<br>`app/src/main/java/com/microauto/script/ScriptRepository.kt:413-421` | `saveScript()` 先更新 `lastSavedContent` 再调用 `save()`，忽略返回的 Boolean；工具栏无条件提示“已保存”；读写/索引写入都在主线程 | 保存失败（索引缺失、恢复覆盖、IO 错误）后退出不会提示未保存，造成静默数据丢失；大脚本/慢磁盘可能 ANR | 保存改为 IO 协程并等待结果；只有成功才更新 `lastSavedContent`/提示；失败保留 dirty 状态并显示错误；启动初始化也移出主线程 | 模拟 `save()` 返回 false 时编辑器仍为 dirty 并显示错误；StrictMode 下编辑/保存无主线程磁盘写；1 MB 保存不 ANR |
| SEC-008 | P2 | `app/src/main/java/com/microauto/script/ScriptService.kt:79-98`<br>`app/src/main/java/com/microauto/script/ScriptService.kt:105-124`<br>`app/src/main/java/com/microauto/script/ScriptService.kt:163-177` | 旧运行的协程在 `finally` 中先 `active=false` 再 `finishRun()`；新运行可在间隙通过检查并启动；旧 `finishRun()` 随后释放共享 `wakeLock`、`stopForeground`、`stopSelf` | 新运行被旧运行“收尾”取消，前台通知/唤醒锁被移除，`RunState` 与日志错乱；定时与手动运行重叠时更易触发 | 每次运行生成 token/owner；`finishRun(token)` 只允许 owner 收尾；使用 `stopSelf(startId)`；唤醒锁归属单次运行；状态迁移在主线程串行化；启动前取消旧 job 并等待 | 连续 start/stop/start 压力测试中第二次运行不被取消；唤醒锁只由当前运行释放；`RunState`/通知与实际一致；无旧运行收尾新运行 |
| SEC-009 | P2 | `app/src/main/AndroidManifest.xml:72-80` | `BootReceiver` 设置 `exported="true"`，带 `BOOT_COMPLETED`/`TIME_SET`/`TIMEZONE_CHANGED` filter；这些是 protected broadcast，第三方应用不能伪造，但导出本身不必要 | 直接利用风险低；若未来新增自定义 action，导出会扩大攻击面；当前仍是可收紧的 manifest 配置 | 改为 `android:exported="false"`；系统广播仍可送达；保留 `AutomationService` 的 `exported="true"`（系统绑定所必需，受 `BIND_ACCESSIBILITY_SERVICE` 保护） | API 35 上开机/改时间/改时区仍能重注册定时；其他 UID 显式广播无法到达；manifest lint 无新增告警 |
| SEC-010 | P2 | `app/build.gradle.kts:41-56`<br>`app/build.gradle.kts:71-72` | release 构建在没有 release keystore 时回退到仓库内公开的 debug keystore（alias/密码也公开） | 误构建的 release APK 可被任何人用同一 debug key 签名并覆盖安装/分发，破坏更新真实性；条件性风险，但一旦发生影响高 | release 构建缺少 release keystore 时直接失败；debug 回退只允许 debug variant 或显式本地开关；CI 校验 APK 证书指纹；发布流程强制使用 secrets | 无 release keystore 时 `assembleRelease` 失败；release APK 证书不等于 debug 证书；CI 输出 `apksigner verify --print-certs` 校验结果 |
| SEC-011 | P2 | `app/src/main/java/com/microauto/api/UiSelector.kt:33-38`<br>`app/src/main/java/com/microauto/core/Selector.kt:18-22`<br>`app/src/main/java/com/microauto/script/ScriptRunner.kt:55` | 脚本可传入任意 Java 正则；`Pattern.matcher(...).find()` 在无障碍文本上回溯；Rhino 指令观察器和 `ScriptRunner.cancel()` 都无法中断正在执行的 Java 正则 | 恶意/错误脚本可用灾难性回溯挂起运行线程，超过 10 分钟 deadline，前台服务/唤醒锁残留，单脚本串行模型阻塞后续运行 | 限制匹配文本长度（如 4 KB/节点）和正则长度；拒绝明显嵌套量词或改用 RE2/J；在独立 watchdog 中执行匹配；匹配前检查 deadline | 对 1 MB 文本 + `(a+)+$` 在 1 秒内返回/抛错；运行超时仍能结束；新增正则 DoS 回归测试 |

## 3. 详细发现

### SEC-001（P0）Rhino 沙箱可被 `native.getClass()` 反射逃逸

**位置**

- `app/src/main/java/com/microauto/script/ScriptEngine.kt:31-35`
- `app/src/main/java/com/microauto/script/ScriptScope.kt:7`
- `app/src/main/java/com/microauto/script/ScriptScopeTest.kt:10-24`（现有测试只覆盖顶层 `Packages`/`java`）
- 安全承诺：`docs/tutorial.md:516`

**问题**

`ScriptEngine.execute()` 先创建安全作用域，然后把 `AutomationBridge` 通过 `RhinoContext.javaToJS(bridge, scope)` 放进全局 `native`：

```kotlin
// ScriptEngine.kt:31-33
val scope = ScriptScope.create(rhino)
val bridge = AutomationBridge(service, context.applicationContext)
ScriptableObject.putProperty(scope, "native", RhinoContext.javaToJS(bridge, scope))
```

`initSafeStandardObjects()` 会安装 ClassShutter，使脚本无法解析顶层 `Packages` / `java`（现有测试也验证了这一点）。但它不会改变已暴露 Java 实例的反射行为。`NativeJavaObject` 会暴露 `Object` 的 `getClass`、`class`、`wait`、`notify`、`equals` 等成员；拿到 `java.lang.Class` 后即可继续反射。

用 Rhino 1.7.15 独立 JVM 探针（与 app 使用同一 jar）验证：

```js
typeof Packages                    // "undefined"
typeof java                        // "undefined"
typeof native.getClass             // "function"
native.getClass()                  // class com.microauto.api.AutomationBridge
native.class                       // 同样是 Class 对象

// 1) 加载任意类并调用静态方法
native.getClass().forName("java.lang.Runtime")
    .getMethod("getRuntime").invoke(null).exec("id");
// 返回 Process 对象

// 2) 读私有字段（app 类）
var f = native.getClass().getDeclaredField("service");
f.setAccessible(true);
f.get(native);                     // 可读取私有字段（JVM 探针已验证同类 app 类）

// 3) 通过 ClassLoader 加载任意类
native.getClass().getClassLoader().loadClass("java.io.File");
```

`for...in native` 还会枚举 `getClass`、`class`、`wait`、`notify`、`notifyAll`、`equals`、`hashCode`、`toString` 等成员；因此只删除单个属性名不是稳健修复，必须做成员白名单。

**影响**

- 任何被导入/定时执行的脚本都可脱离 `native` API，直接使用完整 Java 运行时（反射、文件、进程、Intent、PackageManager、SharedPreferences、ClassLoader）。
- 可读取应用私有目录中的全部脚本、版本、`latest.log`、`SharedPreferences`；可覆盖脚本、写文件、执行进程、启动其他应用。
- 应用未声明 `INTERNET`，直接联网受限；但可通过启动其他应用/隐式 Intent 外传数据，且未来若增加网络权限，影响会进一步放大。
- 这与教程对用户的承诺（“不能直接访问 `java` / `Packages`，需要系统能力时通过 `AutomationBridge` 暴露”）直接冲突。**导入第三方脚本的信任边界完全失效。**

**修复建议**

1. **首选：不要用 `javaToJS` 暴露原生对象。** 在 Kotlin 侧实现一个只包含白名单方法的 `ScriptableObject`（或 `BaseFunction`）门面，把 `findOne`、`findAll`、`tap`、`swipe`、`launchApp` 等逐个转发到内部 `AutomationBridge`。对 `UiSelector`/`UiObject` 使用不透明句柄（例如 `Long` id + native Map），由 `prelude.js` 包装成 JS 对象；不要让 Java 对象直接进入 JS。
2. **如果必须保留 Java 包装：**
   - 安装自定义 `WrapFactory`，对所有 Java 返回值使用一个 `NativeJavaObject` 子类；该子类的 `get`/`has`/`getIds` 只允许每个类显式声明的 API 方法名。
   - 显式返回值 `NOT_FOUND` 的名字至少包括：`getClass`、`class`、`forName`、`getClassLoader`、`getDeclaredField(s)`、`getField(s)`、`getMethod(s)`、`getConstructor(s)`、`setAccessible`、`newInstance`、`wait`、`notify`、`notifyAll`、`clone`、`finalize`。
   - 不要只过滤 `get`：`for...in`/`getIds` 也不能枚举这些名字；返回值（`UiObject`、`Bounds`、数组元素）也必须走同一个包装器。
   - 保留 `initSafeStandardObjects()` 并显式设置 `ClassShutter` 拒绝所有类；不要启用 `Context.FEATURE_ENHANCED_JAVA_ACCESS`，不要使用 `initStandardObjects()`。
3. 在 `prelude.js` 里保持纯 JS 包装层（当前 API 风格可以保留），但底层只接收原始类型/字符串/数组/不透明句柄。
4. **回归测试**：新增 `ScriptSandboxTest`，用真实 Rhino 执行以下断言：
   - `typeof Packages === "undefined"`、`typeof java === "undefined"`；
   - `typeof native.getClass === "undefined"`（或访问抛 `TypeError`）；
   - `native.class`、`native["get" + "Class"]` 不可用；
   - `native.getClass().forName("java.lang.Runtime")` 失败；
   - 从 `native.findOne(...)` 返回的 `UiObject`、`UiObject.bounds()` 返回的 `Bounds`、`findAll()` 数组元素都无法访问 `getClass`/`class`；
   - `for (var k in native)` 不出现反射成员。
5. 同步更新 `docs/tutorial.md` 的安全说明，明确“脚本只能使用白名单 API”。

**验收标准**

- 上述所有恶意片段在真实 Rhino 下返回 `undefined`/抛 `TypeError`，无法构造 `Class`、`ClassLoader`、`Runtime`、`ProcessBuilder` 或读取私有字段。
- `PreludeSmokeTest`、`ScriptScopeTest` 以及新增 `ScriptSandboxTest` 全部通过。
- 代码审查确认没有任何 `javaToJS` 直接暴露 `AutomationBridge` 或其它持有 `Context`/`Service` 的对象。

### SEC-002（P1）备份恢复缺少完整性校验，可清空全部脚本和版本

**位置**

- `app/src/main/java/com/microauto/script/ScriptBackupCodec.kt:35-70`（`import`）
- `app/src/main/java/com/microauto/script/ScriptBackupCodec.kt:41-64`（逐条解压，只有总字节上限）
- `app/src/main/java/com/microauto/script/ScriptBackup.kt:17-45`（`restore`）
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:44-58`（`ensureInitialized` 只在 `index.json` 不存在时补内置脚本）

**问题**

`ScriptBackupCodec.import()` 的校验只有三步：

1. 每个 zip 条目必须落在临时目录内（zip-slip 校验，`ScriptBackupCodec.kt:48-50`）；
2. 解压总量不超过 50 MB（`ScriptBackupCodec.kt:56-58`）；
3. 顶层 `index.json` 存在且能解析、不是 corrupt（`ScriptBackupCodec.kt:66-69`）。

注意：`ScriptIndexCodec.parse()` 会把单条解码失败的记录计入 `skipped` 而不是 `corrupt`（`ScriptIndexCodec.kt:87-103`），而 `ScriptBackupCodec.import()` 只检查 `!parsed.corrupt`，因此“索引里有损坏条目”的备份也会被接受，损坏条目在恢复后静默消失。

它**不检查**：

- 索引中每个 `id` 是否存在对应的 `<id>.js`；
- 每个版本记录是否存在 `versions/<scriptId>/<versionId>.js`；
- 索引/版本记录是否重复、ID 是否合法、条目数量是否合理；
- 恢复后的仓库是否能正常加载。

`ScriptBackup.restore()` 在 `import()` 返回后立即执行破坏性替换：

```kotlin
// ScriptBackup.kt:27-35
if (previous.exists()) previous.deleteRecursively()
if (current.exists()) {
    require(current.renameTo(previous)) { "无法备份当前脚本" }
}
if (!temp.renameTo(current)) { ... }
previous.deleteRecursively()
ScriptRepository.clearCache()
return ScriptRepository(context).ensureInitialized().size
```

只要 `index.json` 合法，哪怕 zip 中没有任何 `.js`，恢复也会成功；`previous` 随即被删除。`ensureInitialized()` 看到 `index.json` 已存在，不会补内置脚本，于是用户得到空脚本列表，原有脚本和版本全部消失。

**影响**

- 一个只含 `index.json`（`[]` 或空对象数组）的 zip，或索引指向缺失文件的 zip，即可造成不可逆的数据丢失。
- 索引列出的脚本没有对应文件时，UI 仍显示条目；打开返回空内容，定时任务会把该脚本判定为“不存在”并移除定时，进一步丢失配置。
- 恢复过程中进程被杀/崩溃在 `rename` 之间，`scripts` 可能缺失；虽然代码在异常时尝试回滚，但 `previous` 已删除或未成功恢复时无法恢复。
- 同一 `scripts-restore`/`scripts-before-restore` 固定路径没有互斥，两个恢复/导出操作并发会互相覆盖。

**修复建议**

1. **两阶段恢复**：先解压到唯一临时目录（带随机后缀），完成全部校验后再替换；不要在 `import()` 返回后立刻删除旧目录。
2. 增加 `BackupManifest`（版本号、脚本数、版本数、每个文件的大小/哈希），或至少在导入后做以下校验：
   - 顶层 `index.json` 大小上限（例如 2 MB），条目数上限（例如 10,000）；
   - 每个 `ScriptMeta.id` 必须匹配 UUID/安全字符集，且唯一；
   - 每个 id 必须有对应 `<id>.js`，每个 `ScriptVersion` 必须有对应版本文件；
   - 拒绝 `..`/分隔符/NUL/绝对路径形式的条目名；只允许 `index.json`、`folders.json`、`versions.json`、`<id>.js`、`versions/<id>/<id>.js`；
   - 条目数上限（例如 10,000）和单条目大小上限，防止“无数空文件”耗尽 inode/存储；
   - 恢复前统计旧/新脚本数量，若新备份为 0 或明显少于当前，二次确认。
3. 旧目录 `previous` 保留到 `ScriptRepository(context).ensureInitialized()` 成功且能列出预期数量后再删除；若加载失败，回滚并保留 `scripts-before-restore`。
4. 对 `restore`/`export` 加进程级 `Mutex`/锁，避免并发。
5. 应用启动时检测“`scripts` 不存在但 `scripts-before-restore` 存在”的残留状态并提示恢复。

**验收标准**

- 只含 `index.json` 的 zip、索引条目缺 `.js` 的 zip、重复 ID 的 zip、超过条目上限/索引大小上限的 zip 被拒绝，且当前 `scripts/` 完全不变。
- 索引中存在被 `skipped` 的损坏记录时，导入也应失败（或在 UI 明确列出并让用户确认），不能静默丢弃。
- 有效备份（脚本、文件夹、版本齐全）可恢复，数量与内容一致。
- 恢复过程中强杀进程后，下次启动能从 `scripts-before-restore` 恢复或给出明确恢复入口。
- 新增 `ScriptBackupValidationTest` 覆盖以上用例；现有 `ScriptBackupCodecTest` 的 zip-slip/缺 index 用例继续通过。

### SEC-003（P1）恢复备份后已有定时自动执行新脚本内容

**位置**

- `app/src/main/java/com/microauto/script/ScriptBackup.kt:17-45`（恢复只替换 `scripts/`）
- `app/src/main/java/com/microauto/schedule/SchedulePrefs.kt:58-59`（按 `scriptId` 查询）
- `app/src/main/java/com/microauto/schedule/ScheduleReceiver.kt:22-37`（按 id 读脚本）
- `app/src/main/java/com/microauto/schedule/ScheduleReceiver.kt:47-68`（决定运行）
- `app/src/main/java/com/microauto/ui/MainActivity.kt:804-827`（恢复确认文案）

**问题**

定时配置保存在 `SharedPreferences("schedule")`，备份只导出 `filesDir/scripts`。`ScriptBackup.restore()` 不会取消、禁用或重新确认任何定时；`ScheduleReceiver` 在闹钟触发时只按 `scriptId` 查 schedule，再 `repository.read(scriptId)` 读取当前文件内容并执行。

因此，只要恢复后的 `index.json` 中存在与某个已启用 schedule 相同的 `scriptId`，该 schedule 在下次触发时就会执行恢复进来的新内容，而不会要求用户打开/审阅。恢复确认文案只说“会覆盖当前所有脚本、文件夹和版本历史”，没有说明“已有定时会继续执行新内容”。

**攻击/故障路径**

1. 攻击者拿到一份该设备导出的备份（或知道某个已定时脚本的 UUID），修改其中 `<id>.js` 的内容但保留 `index.json` 中的 id/name。
2. 诱导用户“恢复配置/修复脚本”。
3. 用户确认覆盖；现有 schedule 仍启用。
4. 下次闹钟触发，`ScriptService` 执行被替换的脚本，获得无障碍自动化能力，无需用户再次点击运行。

如果用户没有同设备备份，攻击者需要知道目标脚本 UUID；因此前置条件比 SEC-001 窄，但一旦满足就是**持久化 + 自动执行**，影响高。

**修复建议**

1. 恢复成功后**取消全部定时**（`ScriptScheduler.cancelAll(context)` + 清理 `SchedulePrefs`），要求用户重新为脚本开启定时；这是最保守、最易验证的方案。
2. 或者保留定时但做内容绑定：schedule 中保存脚本内容哈希；`ScheduleReceiver` 执行前比较当前文件哈希，不一致则禁用并通知用户。
3. 恢复前/后展示差异：列出“将被替换且当前已启用定时的脚本”，要求单独确认；只对未变化的脚本保留定时。
4. 在恢复确认对话框中明确说明定时行为（“已有定时会暂停/继续”）。

**验收标准**

- 恢复后不存在“未重新确认就自动执行新内容”的定时；测试用“已有启用的 schedule + 恢复同 ID 不同内容”验证。
- 若采用哈希方案，内容变化时下次触发会被拦截、schedule 被禁用并通知。
- UI 在恢复前能列出受影响的定时脚本；用户可取消恢复。

### SEC-004（P1）运行通知/日志泄露屏幕文本和脚本日志

**位置**

- `app/src/main/java/com/microauto/script/RunNotifications.kt:27-38`
- `app/src/main/java/com/microauto/script/RunNotifications.kt:59-77`
- `app/src/main/java/com/microauto/script/RunLog.kt:28-38`
- `app/src/main/java/com/microauto/api/AutomationBridge.kt:119-143`
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:439-444`（默认脚本调用 `dump()`）

**问题**

`notifyResult()` 把 `RunLog.text` 的最后一条非空行作为通知正文：

```kotlin
val tail = RunLog.text.value.trim()
    .lineSequence()
    .lastOrNull { it.isNotBlank() }
    .orEmpty()
...
text = tail.ifBlank { scriptName }
```

`RunLog` 同时接收脚本的 `log()` 和 `dump()` 输出。`dump()` 会遍历当前窗口节点树并输出 `text=`/`desc=`，默认内置脚本就调用 `dump()`。`dumpNode()` 没有检查 `AccessibilityNodeInfo.isPassword`，如果目标应用/系统返回了密码字段文本，就会原样记录。

通知没有设置 `setVisibility(...)`，默认行为由系统锁屏设置决定；在通知栏（解锁后）内容直接可见。日志还会持久化到 `filesDir/latest.log`（最多约 256 KB），下次启动继续显示。

**影响**

- 验证码、密码、聊天内容、订单信息、Token 等可能出现在运行完成通知中；若用户允许锁屏显示敏感内容，则锁屏可见。
- 日志文件长期保留屏幕文本；应用私有目录虽受沙箱保护，但 `adb`/root/取证/误分享日志时可能泄露。
- 定时任务的完成通知在用户不在场时也会生成，扩大了暴露窗口。

**修复建议**

1. 通知正文只使用通用状态（“运行完成/失败”）和脚本名，**不要带日志尾部**；必要时只显示“查看日志”入口。
2. 对通知设置 `setVisibility(NotificationCompat.VISIBILITY_SECRET)`（锁屏不显示内容），并考虑 `setLocalOnly(true)`。
3. `dump()` 对 `AccessibilityNodeInfo.isPassword == true` 的节点输出 `text=<hidden>`；`UiObject.text()` 是否也屏蔽需要产品决策（脚本可能确实需要读取某些字段，但密码字段应默认不可读）。
4. 日志改为可关闭（设置项），或在应用退出/下次启动时清理；至少不要把 `dump()` 默认写入持久日志。
5. 如果保留日志，增加简单的敏感信息打码（密码/验证码模式）并明确告知用户日志可能含屏幕内容。

**验收标准**

- 运行完成通知正文不包含任何 `RunLog` 文本；锁屏通知不展示内容。
- `dump()` 遇到 `isPassword` 节点输出隐藏标记；测试用一个 `NodeLike`/假节点验证。
- 关闭日志后 `latest.log` 不增长；恢复/清除日志路径有测试。

### SEC-005（P2）备份索引 ID 未校验导致路径穿越

**位置**

- `app/src/main/java/com/microauto/script/ScriptBackupCodec.kt:66-70`
- `app/src/main/java/com/microauto/script/ScriptIndexCodec.kt:13-21`、`61-68`
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:99-103`（`read`）
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:143-155`（`delete`）
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:157-181`（`publishVersion`）
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:188-193`（`readVersion`）
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:195-216`（`restoreVersion` 写入）

**问题**

`ScriptIndexCodec.parseScripts()` 对 `id` 只调用 `requiredString()`（非空），`parseVersions()` 对 `scriptId`/`id` 同样只校验非空。之后仓库直接用字符串拼路径：

```kotlin
// ScriptRepository.kt:99-103
val file = File(dir, "$id.js")
...
// ScriptRepository.kt:190
val file = File(versionsDir, "${version.scriptId}/${version.id}.js")
// ScriptRepository.kt:168
val versionDir = File(versionsDir, scriptId).apply { mkdirs() }
```

`ScriptNaming.sanitize()` 只用于显示名称（`create`/`rename`/文件夹），不用于导入的 ID。恶意备份可以把 `id` 设为 `../../shared_prefs/x`、`../scripts/<victim>` 等；`File(dir, "$id.js")` 会解析到 `scripts/` 之外。由于后缀固定为 `.js`，不能直接覆盖 `shared_prefs/*.xml` 或 `databases/*`，但可以：

- 读取/删除/覆盖应用私有目录内其它 `.js` 文件（包括其他脚本）；
- 在 `shared_prefs`/`databases`/`files` 下创建 `.js` 文件；
- `publishVersion` 的 `mkdirs()` 可在 `versions/` 外创建目录；
- `restoreVersion` 可把内容写到越界路径（目标仍以 `.js` 结尾）。

结合 SEC-003，如果越界写入命中某个已定时脚本 ID，可能进一步影响自动执行。

**修复建议**

1. 在 `ScriptIndexCodec` 的 decoder 中强制 ID 格式：`[A-Za-z0-9_-]{1,64}` 或 UUID；拒绝 `..`、`/`、`\`、NUL、空白、点开头等。
2. 仓库层不信任任何传入 ID：所有文件访问统一走 `resolveWithin(root, relative)`，比较 `canonicalPath` 是否以 `root.canonicalPath + File.separator` 开头；失败返回 null/false，不抛到 UI 外。
3. `ScriptBackupCodec.import()` 只允许白名单条目名：`index.json`、`folders.json`、`versions.json`、`<uuid>.js`、`versions/<uuid>/<uuid>.js`；其它条目拒绝或忽略并计数。
4. 导入时检查重复 ID、重复版本 ID，避免两个条目竞争同一路径。
5. 为 `read/delete/publishVersion/readVersion/restoreVersion` 增加路径越界单元测试。

**验收标准**

- 索引/版本中 `../x`、`/x`、`a/b`、`a\\b`、含 NUL 的 ID 在导入时被拒绝。
- 即使绕过导入直接构造 `ScriptMeta`，仓库的 `read/delete/publishVersion/restoreVersion` 也不会访问 `scripts/`/`versions/` 之外的路径。
- 白名单外 zip 条目被拒绝；合法备份仍可恢复。

### SEC-006（P2）大文件/大行数脚本导致 OOM、ANR 或启动崩溃

**位置**

- `app/src/main/java/com/microauto/ui/MainActivity.kt:750-767`（导入）
- `app/src/main/java/com/microauto/ui/EditorActivity.kt:464-469`（行号）
- `app/src/main/java/com/microauto/ui/EditorActivity.kt:182-183`（`onCreate` 读取）
- `app/src/main/java/com/microauto/ui/EditorActivity.kt:578-594`（运行按钮）
- `app/src/main/java/com/microauto/script/ScriptService.kt:32-40`（脚本内容放入 Intent）

**问题**

1. `importFromUri()` 先用 `bufferedReader().use { it.readText() }` 把整个文件读入内存，之后才检查 `content.length > 1_000_000`。恶意/异常 SAF 提供方返回无限流或数 GB 文本时，应用会先 OOM。
2. 1 MB 字符可以包含数十万到上百万行（例如内容大量使用 `\n`）。导入成功后 `importScript()` 直接 `openEditor(meta.id)`；`EditorActivity.updateLineNumbers()` 用 `(1..count).joinToString("\n")` 在主线程生成所有行号，50 万行会产生数 MB 字符串并造成明显卡顿/ANR。`JsSyntaxHighlighter` 对 >50k 字符跳过高亮（`JsSyntaxHighlighter.kt:49-50`），但行号没有上限。
3. `EditorActivity.btnRun` 调用 `ScriptService.start()`，把完整脚本内容作为 `EXTRA_SCRIPT` 放进 Intent；`ScriptService.start()` 用 `ContextCompat.startForegroundService`。当文本接近/超过 Binder 事务上限时，会抛 `TransactionTooLargeException`。`EditorActivity` 没有 `runCatching`，会直接崩溃；`MainActivity`/`ScheduleReceiver` 虽有捕获，但大脚本定时任务会启动失败。
4. `ScriptService`/`ScriptRunner` 的 10 分钟超时无法覆盖 Intent 编组失败（发生在启动前）。

**影响**

- 恶意导入文件可在导入/打开编辑器阶段造成 OOM/ANR，用户无需点击运行。
- 大脚本无法运行或导致崩溃；定时任务静默失败。
- 由于编辑器自动打开，攻击者只需诱导用户选择一个文件即可触发。

**修复建议**

1. 导入改为**有界流式读取**：先限制字节数（例如 256 KB 或 1 MB 字节，而非字符），用固定缓冲区读取并在超限时立即停止；不要先 `readText()`。限制行数（例如 20,000 行）和单行长度。
2. 行号渲染惰性化：只渲染可见区域，或对超过 N 行的文件禁用行号/提示文件过大；不要在每次文本变化时 `joinToString` 全部行号。
3. 运行 API 改为传 `scriptId`（服务内部用 `ScriptRepository.read(id)` 读取），避免大文本进 Intent；如果必须传文本，先检查大小并捕获 `TransactionTooLargeException`，在编辑器和服务的调用点都给出中文错误。
4. 大文件在 `Dispatchers.IO` 解析/保存；编辑器打开前先做大小/行数检查。
5. 增加测试：无限流/100 MB 文档、50 万行文档、2 MB 脚本运行。

**验收标准**

- 超过字节上限的 SAF 流在读取过程中被拒绝，进程内存不出现与文件大小同级的峰值。
- 50 万行脚本导入后不会因行号生成而 ANR；UI 有明确“文件过大”提示或降级渲染。
- 2 MB 脚本点击运行不会崩溃；定时启动失败会显示可理解的通知/日志。

### SEC-007（P2）主线程文件 IO 与保存失败被静默忽略

**位置**

- `app/src/main/java/com/microauto/ui/EditorActivity.kt:641-644`（`saveScript`）
- `app/src/main/java/com/microauto/ui/EditorActivity.kt:141-146`（工具栏“已保存”）
- `app/src/main/java/com/microauto/ui/EditorActivity.kt:182-183`（`onCreate` 读取）
- `app/src/main/java/com/microauto/MicroAutoApp.kt:13`（Application 初始化）
- `app/src/main/java/com/microauto/ui/MainActivity.kt:115`（MainActivity 初始化）
- `app/src/main/java/com/microauto/script/ScriptRepository.kt:413-421`（`writeAtomically`）

**问题**

`saveScript()` 是同步主线程写：

```kotlin
private fun saveScript() {
    lastSavedContent = binding.scriptEditor.text.toString()
    repository.save(scriptId, lastSavedContent)
}
```

- 它**先**更新 `lastSavedContent`，**再**调用 `save()`，并且完全忽略 `save()` 返回的 `Boolean`。
- `repository.save()` 只在索引中找到 id 时返回 true；如果索引被恢复/删除、或索引写入失败，它可能返回 false 或抛异常。此时 `lastSavedContent` 已被更新，`requestFinish()` 会认为没有未保存修改，直接退出；用户丢失编辑内容。
- 工具栏保存路径无条件 `toast("已保存")`，与真实结果无关。
- `ScriptRepository.writeAtomically()` 的 `writeText`/`renameTo` 在调用线程执行；`EditorActivity` 的保存、`onCreate` 的读取、`MicroAutoApp`/`MainActivity` 的 `ensureInitialized()` 都在主线程。大脚本/慢磁盘会造成卡顿甚至 ANR。

**影响**

- 静默数据丢失：保存失败但 UI 表示成功，退出时不提示。
- 主线程 IO 造成 ANR，尤其在 1 MB 脚本、慢速存储或首次初始化（写入多个内置脚本）时。
- 异常（磁盘满、只读、索引损坏）可能直接崩溃，而不是可恢复的错误提示。

**修复建议**

1. `saveScript()` 改为挂起函数，在 `Dispatchers.IO` 执行；`save()` 返回 false/抛异常时显示错误并保持 dirty，不更新 `lastSavedContent`。
2. 只有写入成功后才更新 `lastSavedContent` 和提示“已保存”；工具栏/发布/运行/退出流程统一走这个结果。
3. `EditorActivity.onCreate` 的读取、`Application`/`MainActivity` 的 `ensureInitialized()` 移到后台初始化（例如 `lifecycleScope` + loading 状态），避免主线程磁盘 IO。
4. `ScriptRepository` 提供 `suspend` 版本或显式 `withContext(Dispatchers.IO)` 的调用约定；UI 层不得直接调用阻塞 IO 方法。
5. 用 StrictMode（debug）检查主线程磁盘读写。

**验收标准**

- 模拟 `save()` 失败时，编辑器仍显示未保存状态并提示错误；退出时弹出保存确认。
- 保存 1 MB 脚本时主线程无磁盘 IO/无 ANR；StrictMode 无 `DiskReadViolation`/`DiskWriteViolation`。
- 首次启动初始化在后台完成，UI 不卡顿。

### SEC-008（P2）ScriptService 生命周期竞态：旧运行收尾会取消新运行

**位置**

- `app/src/main/java/com/microauto/script/ScriptService.kt:79-98`（新运行检查/启动）
- `app/src/main/java/com/microauto/script/ScriptService.kt:105-124`（`finishRun`/`stopRun`）
- `app/src/main/java/com/microauto/script/ScriptService.kt:163-177`（共享 `wakeLock`）
- `app/src/main/java/com/microauto/script/ScriptService.kt:179-186`（`onDestroy`）
- `app/src/main/java/com/microauto/script/ScriptRunner.kt:21-30`、`61-66`（`running` 标志）

**问题**

旧运行的协程在 `finally` 中先执行 `active = false`，再调用 `finishRun(success)`：

```kotlin
// ScriptService.kt:89-97
job = scope.launch {
    var success = false
    try {
        success = ScriptRunner.run(this@ScriptService, script)
    } finally {
        active = false
        finishRun(success)
    }
}
```

`ScriptRunner.run()` 的 `finally` 也已经把 `running` 置为 false。于是在 `active=false` 到 `finishRun()` 之间的窗口里，主线程收到新的 `ACTION_RUN` 时：

1. `active || ScriptRunner.isRunning()` 检查通过；
2. 新运行设置 `active=true`、启动前台通知、调用 `acquireWakeLock()` 把共享 `wakeLock` 字段替换成新锁；
3. 旧运行的 `finishRun()` 执行 `releaseWakeLock()`，释放的是**新运行的锁**；
4. 旧运行继续 `stopForeground(STOP_FOREGROUND_REMOVE)`、`stopSelf()`，把服务停掉；
5. 新运行的协程被 `onDestroy()` 取消，`RunState`/日志/通知错乱。

`stopRun()`（用户点停止）也可能与新运行交错，造成同类问题。

**影响**

- 用户连续点击运行/停止，或定时闹钟与手动运行几乎同时发生时，新运行被旧运行取消；表现为“启动后立刻结束”、通知消失、日志显示旧运行结果。
- 新运行失去唤醒锁，屏幕可能休眠，自动化中断。
- `RunState.running` 与实际不一致，UI 按钮状态错误。
- 单脚本串行模型被破坏时，后续定时任务会被跳过或互相覆盖日志。

**修复建议**

1. 为每次运行生成唯一 token（或使用 `job` 引用 + generation 计数）；`finishRun(token)` 只在 token 仍是当前 owner 时执行收尾。
2. 使用 `stopSelf(startId)` 而不是无参 `stopSelf()`；保存本次 `startId`，避免旧运行停掉新 `startId`。
3. 唤醒锁改为每次运行独立对象，`release` 只释放自己持有的锁；不要用共享字段。
4. 把 `active`/`job`/`wakeLock`/`runningName` 的状态迁移放到主线程串行执行（`onStartCommand` 已在主线程；协程收尾用 `withContext(Dispatchers.Main)` 或 `Handler`）。
5. 启动新运行前先取消/等待旧 job，或明确拒绝并提示“正在收尾，请稍后”。
6. 增加压力测试：快速 start/stop/start、定时与手动同时 start。

**验收标准**

- 连续 start/stop/start 压力测试中，第二次运行不会被第一次的 `finishRun` 取消；通知、`RunState`、唤醒锁均对应第二次运行。
- 旧运行收尾不会释放新运行的 wake lock，不会 `stopSelf` 新运行。
- 定时与手动运行同时触发时，只有一个运行，另一个被明确跳过并记录原因。

### SEC-009（P2）BootReceiver 不必要的 exported

**位置**

- `app/src/main/AndroidManifest.xml:72-80`

**问题**

`BootReceiver` 声明：

```xml
<receiver
    android:name=".schedule.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
    </intent-filter>
</receiver>
```

这些 action 是 Android protected broadcast，普通第三方应用不能发送，因此**当前不能被外部应用直接伪造触发**。但 `exported="true"` 不是接收系统广播的必要条件；系统可以向非导出 receiver 发送广播。导出会增加未来维护时的攻击面：一旦有人加入自定义 action 或把逻辑扩展到其它广播，就可能被外部触发。

**影响**

- 当前直接利用风险低；属于 manifest 加固项。
- 若未来新增非 protected action 的处理，外部应用可触发定时重排/恢复逻辑，造成调度异常或资源消耗。

**修复建议**

- 将 `android:exported` 改为 `false`。
- 保留 `AutomationService` 的 `android:exported="true"`：系统绑定无障碍服务需要它，且已有 `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` 保护；不要误改为 false。
- 如果未来需要外部触发，显式加 signature 级自定义权限，而不是依赖 action 名称。

**验收标准**

- API 35 上开机、改时间、改时区后定时仍被重注册（可用仪器化测试或手动验证）。
- 其他 UID 显式广播到 `BootReceiver` 无法送达；manifest lint 无新增 `ExportedReceiver` 告警。

### SEC-010（P2）release 签名回退到公开 debug keystore

**位置**

- `app/build.gradle.kts:41-56`（release signingConfig）
- `app/build.gradle.kts:71-72`（回退）
- `AGENTS.md` / `signing/debug.keystore`（debug key 公开、alias/密码固定）

**问题**

```kotlin
release {
    isMinifyEnabled = false
    signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        ?: signingConfigs.getByName("debug")
}
```

如果没有设置 `RELEASE_KEYSTORE_PATH`/`keystore.properties`，release 构建会静默使用仓库内的 debug keystore。debug keystore、alias、密码都在仓库中公开，任何人都可以用同一证书签名同包名的 APK。对通过 GitHub Releases/侧载分发的 release 包，这允许攻击者构造“同签名升级包”，覆盖安装或诱导用户安装。

这是条件性风险：CI 正常配置 release secrets 时不会触发；本地/误配置的 release 构建会触发。

**影响**

- 更新真实性和分发完整性被破坏；用户无法通过签名区分官方包和伪造包。
- 如果该 APK 被提交到应用商店，通常会被拒绝，但侧载/GitHub Release 场景仍有风险。

**修复建议**

1. release 构建缺少 release keystore 时**直接失败**（`error("release keystore not configured")`），不要回退 debug。
2. 如果确实需要本地无签名 release 调试，使用单独的 `staging`/`benchmark` variant，并在版本名/包名上明确标记，不发布。
3. CI 在发布前执行 `apksigner verify --print-certs`，断言证书 SHA-256 等于预期 release 证书；失败则中止 Release job。
4. 在 `README`/`AGENTS.md` 发布说明中写明“release 必须使用 release keystore，禁止 debug fallback”。

**验收标准**

- 在没有 release keystore 的干净环境执行 `:app:assembleRelease` 失败并给出明确错误。
- 正常 CI release 的 APK 证书为 release 证书，且 CI 有证书指纹校验步骤。
- 仓库中不再存在 release → debug 的静默回退路径。

### SEC-011（P2）脚本可控正则可在 Java 层绕过 10 分钟超时

**位置**

- `app/src/main/java/com/microauto/api/UiSelector.kt:33-38`（`Pattern.compile`）
- `app/src/main/java/com/microauto/core/Selector.kt:18-22`（`matcher.find()`）
- `app/src/main/java/com/microauto/script/ScriptRunner.kt:55`（安装 guard）
- `app/src/main/java/com/microauto/script/ScriptEngine.kt:30`（指令观察器）
- `app/src/main/java/com/microauto/script/ScriptExecutionGuard.kt:15-19`（检查点）

**问题**

`textMatches` 允许脚本传入任意正则：

```kotlin
fun textMatches(source: String, flags: String = "") =
    UiSelector(selector.copy(textPattern = compile(source, flags)))
```

`Selector.matches()` 对每个节点文本执行 `textPattern.matcher(nodeText).find()`。这是纯 Java 正则匹配：

- Rhino 的 `observeInstructionCount` 只在 JS 解释器执行指令时检查 deadline，**不会进入 Java 正则内部**；
- `ScriptRunner.cancel()` 调用 `Thread.interrupt()`，但 `java.util.regex` 的匹配循环不响应中断；
- 因此灾难性回溯（例如 `(a+)+$` 对长 `aaaa...` 文本）可以让运行线程挂起远超 10 分钟；前台服务、唤醒锁、`running` 状态会一直保留，后续脚本全部被“已有脚本在运行”跳过。

脚本可以自己制造长文本（例如在目标应用的输入框 `setText` 长字符串，再用选择器匹配该节点），也可以针对本身就含长文本的页面。

**影响**

- 恶意/错误脚本可造成自动化服务 DoS：单次运行永久占用，直到用户强杀应用。
- 前台服务/唤醒锁长时间残留，耗电；定时任务持续失败/跳过。
- 错误的正则也可能被普通用户无意触发，表现为“脚本卡死”。

**修复建议**

1. 在匹配前限制节点文本长度（例如 4 KB）和正则表达式长度（例如 1 KB）；超限直接返回 false/抛业务异常。
2. 更稳健：使用 RE2/J（线性时间正则）作为脚本正则引擎；或对明显嵌套量词做静态拒绝（只能缓解，不能彻底解决）。
3. 把单次正则匹配放到带 watchdog 的独立线程/进程；超时后放弃该节点并终止运行（注意 Java 线程无法安全强杀，RE2/J 更合适）。
4. 在 `ScriptExecutionGuard` 中除了 JS 指令观察器，给 `findOne`/`findAll` 的每个节点匹配之间加 deadline 检查（当前每节点有调用，但单次匹配仍可能卡住）。
5. 文档中明确“正则匹配受输入长度限制”，并在 API 文档中建议避免嵌套量词。

**验收标准**

- 对 1 MB 文本执行 `(a+)+$` 在 1 秒内返回/抛错，不阻塞运行线程。
- 普通脚本的 `textMatches` 行为不变；`PreludeSmokeTest` 通过。
- 超时/停止仍能在合理时间内结束运行；新增正则 DoS 测试。

## 4. 其他低优先级观察（未计入 11 条发现）

- `app/src/main/java/com/microauto/accessibility/AccessibilityUtils.kt:12-16`：`endsWith(AutomationService::class.java.name)` 可能把其它应用中同名的 service 误判为已开启；随后 `ScriptRunner` 等待 5 秒后报“无障碍服务未开启”。建议改为精确匹配 `packageName/className`，避免假阳性。
- `app/src/main/java/com/microauto/accessibility/AndroidNode.kt:28-36`、`AutomationBridge.kt:34-47`、`NodeActions.kt:15-21`：`AccessibilityNodeInfo` 没有 `recycle()`。长运行脚本可能耗尽节点池/增加 GC 压力；在 API 33+ 上 `recycle` 已废弃，但 minSdk 26 仍应评估兼容释放策略。
- `app/src/main/java/com/microauto/accessibility/GestureController.kt:54`：`latch.await(duration + 1500, ...)` 在 `duration` 接近 `Long.MAX_VALUE` 时溢出为负数，`await` 立即返回 false；同时 `StrokeDescription` 对 duration 有上限，异常会传播到脚本。建议对 duration 做范围校验（如 1..60,000 ms）。
- `app/src/main/java/com/microauto/script/RunLog.kt:28-38`：每次 `append` 都做 `(_text.value + line).takeLast(MAX_CHARS)`，脚本一次输出很大的字符串时会产生大临时对象；建议限制单条日志长度（例如 4 KB）并分块写入。
- `app/src/main/java/com/microauto/schedule/ScheduleCodec.kt:30-32` 只校验 `alarmId > 0`，`ScriptScheduler.kt:130` 使用 `10000 + alarmId`；极端损坏的偏好可造成 Int 溢出/requestCode 冲突。当前数据仅应用私有，优先级低。
- `app/src/main/java/com/microauto/script/ScriptService.kt:86`：若用户拒绝 `POST_NOTIFICATIONS`，前台服务通知可能不可见，用户无法从通知栏停止；UI 仍有停止按钮，但应在首次运行时解释。

## 5. 已确认的良好实践

以下控制当前实现正确，修复时不要回退：

- `AndroidManifest.xml:20-22`：`allowBackup="false"`，并配置了 `backup_rules.xml` / `data_extraction_rules.xml` 排除根目录。
- `ScriptService`、`ScheduleReceiver`、`EditorActivity`、`VersionsActivity` 均为 `exported="false"`；`AutomationService` 的 `exported="true"` 是系统绑定所必需，且有 `BIND_ACCESSIBILITY_SERVICE` 签名权限保护。
- 所有 `PendingIntent` 都使用了 `FLAG_IMMUTABLE`：`ScriptScheduler.kt:132/147/157/163`、`ScriptService.kt:144/151`、`RunNotifications.kt:66`。
- 组件间通信：`MainActivity` 是唯一 exported activity（launcher 必需），且不读取外部传入的 `scriptId` 等 extras；`EditorActivity`、`VersionsActivity`、`ScriptService`、`ScheduleReceiver` 均非导出。`AutomationBridge.launchApp()` 只使用 `getLaunchIntentForPackage()` 并添加 `FLAG_ACTIVITY_NEW_TASK`，没有把脚本参数拼进隐式 Intent；未发现 `Intent.parseUri`/`setComponent` 重定向或可被外部利用的隐式 Intent。脚本可控的 `launchApp(pkg)` 属于产品能力，不是组件间重定向漏洞。
- Manifest 未声明 `INTERNET`，代码中没有 `WebView`、`Runtime.exec`、`DexClassLoader` 等网络/动态加载逻辑（当前问题在于脚本可通过反射自行获得这些能力）。
- `SharedPreferences` 只保存定时配置、编辑器偏好等非脚本文本；脚本内容位于 `filesDir/scripts`，且 `allowBackup="false"`。敏感信息主要风险在脚本源码和运行日志（SEC-004）。
- `ScriptNaming.sanitize()` 会移除路径分隔符、控制字符并限制长度；脚本文件名使用 UUID（`ScriptRepository.create`），不直接使用用户输入作为文件名。
- `ScriptBackupCodec.import()` 有 canonical path 的 zip-slip 校验（`ScriptBackupCodec.kt:48-50`）和 50 MB 解压总量上限（`ScriptBackupCodec.kt:56-58`），方向正确，需补条目数/清单校验。
- `ScriptRunner` 执行前检查屏幕点亮且未锁定（`ScriptRunner.kt:47-54`）；定时策略在锁屏时重试/跳过（`ScheduleTriggerPolicy`）。
- `ScriptExecutionGuard` + Rhino 指令观察器能终止纯 JS 死循环（`RhinoExecutionTest` 已覆盖；但不能覆盖 Java 正则/反射逃逸，见 SEC-001/SEC-011）。

## 6. 建议的修复顺序

### 阶段 0（立即，阻断 P0）

1. **SEC-001**：移除 `javaToJS` 或实现成员白名单包装；新增 `ScriptSandboxTest`。
2. 在修复前，建议在 README/教程中临时加入“不要导入不可信脚本/备份”的警告；但这不能替代代码修复。

### 阶段 1（下一个发布前，P1）

3. **SEC-002 + SEC-005** 一起做：备份验证和 ID/路径校验共用一套 `BackupValidator` / `resolveWithin`，避免重复逻辑。
4. **SEC-003**：恢复后取消/重确认定时；与 SEC-002 的恢复事务一起实现，确保不会出现“已换脚本但定时仍指向旧内容”的中间状态。
5. **SEC-004**：通知去日志化 + `dump()` 密码打码 + 日志策略。

### 阶段 2（稳定性/加固，P2）

6. **SEC-006 + SEC-007 + SEC-008** 一起做：这三项都涉及编辑器/服务的 IO 与生命周期，建议统一引入 `Dispatchers.IO`、脚本 ID 传递、运行 token。
7. **SEC-009**：Manifest 收紧，配合开机/改时间仪器化测试。
8. **SEC-010**：构建脚本 fail-fast + CI 证书校验。
9. **SEC-011**：正则输入限制；如需引入 RE2/J，先做依赖/包体评估。

### 修复后的统一验证

- 按 `AGENTS.md` 至少运行：
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:lintDebug`
  - `./gradlew :app:assembleDebug`
- 涉及 `ScriptService`/`BootReceiver`/通知时，运行：
  - `./gradlew :app:connectedDebugAndroidTest`（需要模拟器/真机）
- 新增/调整测试建议：
  - `ScriptSandboxTest`（真实 Rhino，恶意片段回归）
  - `ScriptBackupValidationTest`（完整性、条目数、路径穿越、重复 ID）
  - `ScriptRepositoryPathTest`（仓库层 canonical 校验）
  - `ScriptServiceLifecycleTest`（仪器化：start/stop/start）
  - `EditorLargeFileTest`（大文件/大行数/Intent 大小）
  - `ScheduleRestoreTest`（已有定时 + 恢复同 ID 内容）
- 建议在 debug 构建启用 `StrictMode` 的 `detectDiskReads/detectDiskWrites`，捕获 SEC-007 回归。

## 7. 验证记录与限制

- 本文档基于 `main` 提交 `81b0555`（v0.7.0）的静态代码。
- SEC-001 的反射链使用 Rhino 1.7.15 jar 在独立 JVM 探针中验证；验证点包括：
  - `typeof Packages` / `typeof java` 为 `undefined`；
  - `native.getClass()` / `native.class` 返回 `Class`；
  - `Class.forName("java.lang.Runtime").getMethod("getRuntime").invoke(null).exec("id")` 返回 `Process`；
  - `getDeclaredField(...).setAccessible(true).get(...)` 可读私有字段；
  - `for...in native` 会枚举 `getClass`/`class`/`wait` 等成员。
- 未运行 Gradle 单元测试/Lint/仪器化测试，未在 Android 设备上复现；原因是本次任务要求避免与后续 agent 的构建/模拟器任务冲突。修复 agent 必须在真实 Android 环境验证 SEC-001（尤其是 Android 的 hidden API/反射限制）、SEC-008（Service 生命周期）和 SEC-009（protected broadcast）。
- 未修改任何 app 代码、未改动 `docs/requirements/feature-ui.md`、未 commit/tag/push。
