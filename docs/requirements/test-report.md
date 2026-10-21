# MicroAuto 未提交改动验收测试报告

- 测试日期：2026-10-21
- 工作区：`/Users/alanwork/Workspace/github/microauto`（`main`，工作区含未提交改动）
- 验收范围：SEC-001（Rhino 沙箱）、SEC-002（备份完整性 / 两阶段恢复）、SEC-005（ID 与路径安全）、SEC-003（恢复后取消定时）、R5（编辑器保存状态与重命名）
- 测试角色：独立 QA（只读审查生产代码；仅新增/加强测试；未修改生产代码，未 commit / tag / push）

## 1. 环境与命令

| 项 | 值 |
| --- | --- |
| 系统 | macOS（Apple Silicon） |
| JDK | 17 |
| Gradle / AGP / Kotlin | 8.11.1 / 8.10.1 / 2.1.20 |
| Android SDK | platform 36、build-tools 36.0.0 |
| 模拟器 | `microauto_api35`（API 35，arm64-v8a），`scripts/run-emulator-tests.sh` 自行启停 |

执行的命令：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./scripts/run-emulator-tests.sh
```

## 2. 结果汇总

| 套件 | 数量 | 失败 | 错误 | 跳过 | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| JVM 单元测试（18 个类） | 127 | 0 | 0 | 0 | 通过 |
| 仪器化测试（API 35，7 个类） | 52 | 0 | 0 | 0 | 通过 |
| Android Lint | — | — | — | — | 0 errors，1 warning（既有依赖提示） |
| Debug 构建 | — | — | — | — | 成功，`app/build/outputs/apk/debug/MicroAuto-0.7.0.apk` |

明细：

- JVM：`108` 项原有 + `19` 项本次新增 = `127`，全部通过。
- 仪器化：`43` 项原有 + `9` 项本次新增 = `52`，全部通过；分布为 `SchedulePrefsTest 6`、`ScriptBackupRestoreTest 8`、`ScriptRepositoryPathTest 1`、`ScriptRepositoryTest 15`、`EditorActivityTest 14`、`MainActivityTest 7`、`VersionsActivityTest 1`。
- Lint：`0 errors, 1 warning`，唯一 warning 为 `app/build.gradle.kts:104` 的 `GradleDependency`（`androidx.core:core-ktx 1.18.0` 可升级到 `1.19.1`），与本次改动无关。
- 仪器化共执行 3 轮：第 1、2 轮各有 1 个**测试代码本身**的断言问题（见 4.4），产品代码零失败；第 3 轮 52/52 通过。其余 51 个用例连续 3 轮通过，未观察到 flaky。

## 3. 逐项验收

### SEC-001（P0）Rhino 沙箱反射逃逸

**实现审查结论：通过。**

- `ScriptEngine.execute()` 在创建安全作用域前安装 `ScriptSandbox`，`native` 不再走 `Context.javaToJS`，而是经 `rhino.wrapFactory.wrap(...)` 注入（`app/src/main/java/com/microauto/script/ScriptEngine.kt:31-38`）。
- `ScriptSandbox` 的成员白名单只包含 4 个脚本 API 类 + 基本类型；白名单取自 public 实例方法与 Kotlin bean 属性名，并显式移除 `getClass`、`class`、反射方法、`ClassLoader`、`setAccessible`、`wait/notify`、`clone/finalize`、`equals/hashCode/toString`（`app/src/main/java/com/microauto/script/ScriptSandbox.kt:23-67`）。
- `SandboxNativeJavaObject` 同时重写 `get`、`has`、`getIds`，并让 `put`/`delete` 成为空操作；`SandboxNativeJavaArray` 只允许 `length` 和下标，`getIds` 只返回下标（`ScriptSandbox.kt:130-168`）。未知类的返回值拿到空白名单，所有属性访问均返回 `NOT_FOUND`。
- 返回值（`UiObject`、`Bounds`、`findAll()` 数组元素、以及未在白名单中的内部对象）都经过同一个 `WrapFactory` 包装；数组元素由 Rhino 的 `NativeJavaArray.get(int)` 重新经过 `WrapFactory` 包装。

**测试证据（`ScriptSandboxTest`，12/12 通过）：**

- `typeof native.getClass`、`typeof native.class`、`typeof native["get"+"Class"]` 均为 `undefined`；`"getClass" in native` 为 `false`。
- `for (var k in native)` 只列出 API 名；`native.getClass = ...` 赋值无效，`delete native.findOne` 后方法仍可用（`put`/`delete` 被禁用）。
- `native.getClass().forName("java.lang.Runtime")...exec("id")` 抛异常。
- `findOne()` 返回对象、`bounds()` 返回对象、`findAll()` 数组元素及 `Array.prototype.map.call` 遍历元素均无 `getClass`/`class`。
- `native.selector()` 的返回值、未在白名单中的对象（`FakeSecret`）完全不可访问。
- 捕获 Java 异常后：`e.javaException` 存在但 `e.javaException.getClass` / `.class` 均为 `undefined`，无法到达 `Runtime`。
- 全局 `Packages`、`java`、`getClass`、`JavaAdapter`、`importClass`、`importPackage` 均为 `undefined`。
- 正常 API（选择器链、`findOne`、`click`、`text`、`bounds`、数组 `length`）仍可用；`PreludeSmokeTest` 4/4 通过。

**残余观察（低风险）：** `String(native)` / `JSON.stringify(native)` 会经 `getDefaultValue` 调用 Java `toString()`，返回 `com.microauto.api.AutomationBridge@<hash>` 这样的类名与哈希。只泄露类名，不产生 `Class` 对象或任何反射能力，不构成沙箱逃逸。若需要完全隐藏实现类名，可重写 `getDefaultValue`。

### SEC-002（P1）备份完整性校验与两阶段恢复

**实现审查结论：通过（存在 1 项崩溃恢复缺口，见 5.1）。**

- 导入先解压到唯一临时目录 `scripts-restore-<uuid>`，完成全部校验后才替换；当前目录改名为 `scripts-before-restore`，新仓库 `ensureInitialized()` 数量校验成功后才删除旧目录；异常时按阶段回滚（`app/src/main/java/com/microauto/script/ScriptBackup.kt:22-75`）。
- 校验覆盖：`index.json` 必须存在且 ≤ 2 MB、解析不损坏、无 `skipped` 记录、1–10,000 条；脚本 ID 合法且唯一；每个脚本存在 `<id>.js`；文件夹 ID 合法且唯一；版本记录必须指向已存在的脚本、版本 ID 唯一、版本文件存在（`app/src/main/java/com/microauto/script/ScriptBackupCodec.kt:87-139`）。
- zip 条目：UTF-8 名白名单（`index.json`、`folders.json`、`versions.json`、`<id>.js`、`versions/<id>/<id>.js`），拒绝 NUL、反斜杠、绝对路径、`.`/`..` 段；单条目 5 MB、总量 50 MB、条目数 10,000（`ScriptBackupCodec.kt:43-85,141-157`）。
- 失败时当前 `scripts/` 不变（临时目录在 `finally` 删除）。

**测试证据：**

- `ScriptBackupValidationTest` 18/18：只含 `index.json`、空索引、缺失 `.js`、缺失版本文件、重复脚本 ID、重复版本 ID、版本指向不存在脚本、`../`/`/`/`\`/NUL、未知条目、超量条目、超大 `index.json`、含损坏记录的索引、合法备份（含文件夹/版本）全部符合预期。
- `ScriptBackupRestoreTest` 8/8：非法备份保留现有数据；合法备份恢复后脚本内容、文件夹、版本、备注一致；中断换目录状态可恢复；陈旧临时目录被清理。
- 合法备份恢复后 `ensureInitialized()` 数量与索引一致，失败回滚；`invalidBackupKeepsCurrentScripts`、`missingScriptFileIsRejectedAndDataKept` 验证数据未变。

**说明：** Android 14+ 的 `ZipInputStream` 会在应用代码之前用 `SafeZipPathValidatorCallback` 拒绝 `../` 条目并抛 `ZipException`；应用自身的 zip-slip 校验对 API 26–33 与 JVM 仍然必要，属于纵深防御。没有备份覆盖/清空当前数据。

### SEC-005（P2）ID 与路径安全

**实现审查结论：通过。**

- `ScriptPaths` 强制 ID 为 `[A-Za-z0-9_-]{1,64}`，所有路径经 `canonicalFile` 比较，要求位于根目录 + 分隔符之下（`app/src/main/java/com/microauto/script/ScriptPaths.kt:6-29`）。
- `ScriptIndexCodec` 在解析脚本、文件夹、版本的 `id`/`scriptId` 时即做 ID 校验（`app/src/main/java/com/microauto/script/ScriptIndexCodec.kt:13-20,61-68,111-115`）。
- `read/save/delete/publishVersion/readVersion/restoreVersion/deleteVersion/removeVersionsOf` 全部改用 `ScriptPaths`；仓库中不再有 `File(dir, "$id.js")` 形式的拼接（仅 `create` 使用服务端生成的 UUID）。
- `ScriptBackupCodec` 的条目名白名单与 `ScriptPaths.isValidId` 共用同一规则。

**测试证据：**

- JVM `ScriptRepositoryPathTest` 5/5：`../`、`/`、`\`、NUL、空 ID、`.`、65 字符 ID 被拒绝；符号链接指向 `scripts/` 外时被 canonical 校验拒绝；与根目录共享前缀的兄弟目录（`scripts-evil`）不被误认为根内。
- `ScriptBackupValidationTest` 覆盖索引/条目层的 `../`、绝对路径、反斜杠、`.` 段、NUL。
- 仪器化 `ScriptRepositoryPathTest` 1/1：`read/save/delete/publishVersion/readVersion/restoreVersion/deleteVersion` 对 `../outside` 全部失败，且 `filesDir/outside.js` 内容不变。

### SEC-003（P1）恢复后取消所有定时

**实现审查结论：通过。**

- 恢复成功后调用 `ScriptScheduler.cancelAll(context)`（取消每条定时的 daily/retry 闹钟 + 旧版 requestCode）再 `SchedulePrefs.clearAll(context)`（清空 `schedules`、`next_alarm_id`、旧版单条配置、所有 `pending_deadline_*` 与旧版 pending 键）（`app/src/main/java/com/microauto/script/ScriptBackup.kt:57-58`、`app/src/main/java/com/microauto/schedule/ScriptScheduler.kt:91-98`、`app/src/main/java/com/microauto/schedule/SchedulePrefs.kt:133-148`）。
- 恢复确认文案已明确“已有定时会被取消，需要重新开启”（`app/src/main/java/com/microauto/ui/MainActivity.kt:804-812`）；README/教程已同步。

**测试证据：**

- `restoreClearsEverySchedule`：恢复前存在启用定时与 pending，恢复后 `enabled()`、`schedules()`、`pendingScriptIds()` 全为空。
- `restoreClearsLegacyScheduleKeys`：旧版 `enabled/hour/minute/script_id` 与 `pending_*` 全部被清除。

### R5（P1）编辑器保存状态与重命名

**实现审查结论：通过。**

- `saveScript()` 仅在 `repository.save()` 返回 `true` 后更新 `lastSavedContent` 与 `lastSavedAt`；失败时显示 `保存失败，脚本可能已被删除`、保持 dirty、返回 `false`（`app/src/main/java/com/microauto/ui/EditorActivity.kt:652-669`）。
- 副标题与保存菜单由 `updateSaveState()` 统一驱动：dirty 时 `未保存的修改` + 菜单可用；clean 时 `已保存 · HH:mm` + 菜单禁用（`EditorActivity.kt:671-693`）。
- 重命名复用 `dialog_rename.xml`；`isBlank()` 时设置 `TextInputLayout.error` 并 `return`，不关闭对话框；成功后读取实际名称（含 `(2)` 后缀）更新 `scriptName`、toolbar 标题并提示（`EditorActivity.kt:695-729`）。
- 返回/退出时 `requestFinish()` 仍按内容比较决定是否弹出保存确认（`EditorActivity.kt:950-965`）。

**测试证据（`EditorActivityTest`，14/14 通过）：**

- 初始 `已保存 · HH:mm` 且保存菜单禁用；输入后 `未保存的修改` 且启用；保存后恢复 `已保存` 并禁用。
- 保存失败：删除脚本后保存，副标题保持 `未保存的修改`、菜单保持启用，并显示错误 Snackbar。
- 空名称（含全空格）：显示 `名称不能为空`，对话框不关闭，toolbar 标题与仓库名称不变。
- 重命名成功：toolbar 标题与仓库名称更新；重名时自动追加 ` (2)`。
- 内容改回原值后恢复 `已保存` 且保存菜单禁用。

## 4. 新增 / 加强的测试

### 4.1 `app/src/test/java/com/microauto/script/ScriptSandboxTest.kt`（+7，共 12）

| 用例 | 目的 |
| --- | --- |
| `javaGlobalsAreUnavailable` | `Packages`、`java`、`getClass`、`JavaAdapter`、`importClass`、`importPackage` 均不可用 |
| `objectPrototypeMembersAndMutationAreBlocked` | 原型成员（`toString`/`valueOf`/`constructor`）、`in`、赋值/删除绕路 |
| `caughtJavaExceptionIsSandboxed` | 捕获 Java 异常后 `e.javaException` 的成员仍被过滤 |
| `caughtJavaExceptionCannotReachRuntime` | 经异常对象执行 `forName("java.lang.Runtime").exec` 失败 |
| `arrayElementsAndPrototypeCallsStaySandboxed` | `findAll()` 数组元素、`Array.prototype.map` 回调、数组构造器 |
| `unknownJavaReturnValuesAreOpaque` | 未在白名单中的返回类型完全不可访问 |
| `allowedNamesWorkThroughComputedAccess` | 白名单 API 通过 `native["find"+"One"]` 等计算属性仍可用 |

### 4.2 `app/src/test/java/com/microauto/script/ScriptBackupValidationTest.kt`（+10，共 18）

`acceptsBackupWithoutOptionalIndexes`、`rejectsIndexWithSkippedCorruptRecord`、`rejectsDuplicateVersionId`、`rejectsVersionForUnknownScript`、`rejectsNulInScriptId`、`rejectsZipSlipEntry`、`rejectsAbsolutePathEntry`、`rejectsBackslashAndDotSegmentEntries`、`rejectsOversizedIndexEntry`、`rejectsTooManyIndexRecords`。

### 4.3 `app/src/test/java/com/microauto/script/ScriptRepositoryPathTest.kt`（+2，共 5）

`rejectsSymlinkedFileThatEscapesRoot`（符号链接逃逸）、`rejectsSiblingDirectorySharingRootPrefix`（前缀相同的兄弟目录）。

### 4.4 `app/src/androidTest/java/com/microauto/script/ScriptBackupRestoreTest.kt`（+6，共 8）

`validBackupRestoresScriptsFoldersAndVersions`、`traversalEntryIsRejectedAndDataKept`、`missingScriptFileIsRejectedAndDataKept`、`recoversInterruptedSwapBeforeApplyingBackup`、`staleRestoreTempDirectoriesAreRemoved`、`restoreClearsLegacyScheduleKeys`；并在 `setUp` 清理 `scripts-before-restore` 与 `scripts-restore-*`，避免用例间污染。

> 说明：`traversalEntryIsRejectedAndDataKept` 前两轮失败是**测试断言**问题——Android 14+ 平台在应用代码前抛出 `ZipException`（`IOException`，不是 `RuntimeException`），而 JVM 上同一输入抛 `IllegalArgumentException`。最终用例接受任意 `Exception` 并断言现有脚本内容不变；安全行为本身正确，不是产品缺陷。

### 4.5 `app/src/androidTest/java/com/microauto/ui/EditorActivityTest.kt`（+3，共 14，另加强 2 处）

新增：`emptyRenameKeepsDialogOpenAndTitleUnchanged`、`renameToExistingNameAppendsSuffix`、`dirtyStateClearsWhenContentRevertsToSaved`。
加强：保存失败用例增加错误 Snackbar 断言；重命名用例增加 toolbar 标题断言。

## 5. 发现的问题

### 5.1（中）恢复中断后的启动恢复入口缺失，`scripts-before-restore` 中的用户数据不会被发现

**证据（静态审查）：**

- `MicroAutoApp.onCreate()` 在每次进程启动时调用 `ScriptRepository.ensureInitialized()`（`app/src/main/java/com/microauto/MicroAutoApp.kt:13`；`MainActivity.kt:115` 亦调用）。
- `ScriptRepository` 构造时即 `dir.mkdirs()` 创建 `scripts/`（`app/src/main/java/com/microauto/script/ScriptRepository.kt:38-40`）；`ensureInitialized()` 在 `index.json` 不存在时写入 5 个内置脚本（`ScriptRepository.kt:44-58`）。
- `ScriptBackup.restore()` 的“上次中断恢复”分支要求 `!current.exists() && previous.exists()`（`app/src/main/java/com/microauto/script/ScriptBackup.kt:36-38`）；进程重启后 `current` 一定已被重建，该分支不可达。
- 若 `current` 与 `previous` 同时存在，restore 会在换入新备份前直接删除 `previous`（`ScriptBackup.kt:39-41`）。

**影响：** 恢复过程中进程在两次 `renameTo` 之间被杀后，下次启动用户看到的是内置脚本，自己的脚本/版本留在 `scripts-before-restore` 中且没有任何提示；其后若再次恢复备份，旧目录会在校验通过后被删除，用户无法从 UI 找回。SEC-002 验收项“恢复过程中强杀进程后，下次启动能从 `scripts-before-restore` 恢复或给出明确恢复入口”未满足。

**复现（debug 设备，模拟崩溃后的磁盘状态；本次未实际执行，仅静态验证代码路径）：**

```bash
adb shell run-as com.microauto sh -c 'cd files && mv scripts scripts-before-restore'
adb shell am force-stop com.microauto
adb shell am start -n com.microauto/.ui.MainActivity
# 预期：恢复/提示 scripts-before-restore；实际：scripts/ 被重建为内置脚本
```

**建议：** 在 `ensureInitialized()` 创建内置脚本之前检测 `!File(filesDir, "scripts/index.json").exists() && File(filesDir, "scripts-before-restore").exists()`，先恢复或弹出恢复入口；或在 `MicroAutoApp` 初始化前处理该残留状态。测试侧可补一个启动恢复用例。

### 5.2（低）`String(native)` / `JSON.stringify(native)` 泄露实现类名

- `SandboxNativeJavaObject` 未重写 `getDefaultValue`（`app/src/main/java/com/microauto/script/ScriptSandbox.kt:130-149`），`String(native)`、`JSON.stringify(native)`、字符串拼接会调用 Java `toString()`，得到 `com.microauto.api.AutomationBridge@<hash>`。
- 仅泄露类名/哈希，不暴露 `Class`、反射或任何新能力；不影响 SEC-001 的验收标准。
- 如需收紧，可重写 `getDefaultValue` 返回固定字符串（例如 `"[native]"`）。同理适用于 Java 数组的默认字符串。

### 5.3（低，潜在）`ScriptSandbox.allowedMembers()` 的缓存未包含 `extraClasses`

- 缓存键只有 `Class`（`ScriptSandbox.kt:69,77-94`），`extraClasses` 不参与。生产入口 `SandboxWrapFactory()` 不传 extras，因此当前无影响；但若未来在运行时使用 `extraClasses`，同一类先以 extras 计算后，后续不带 extras 的调用会复用宽松结果，安全边界依赖调用顺序。
- 建议缓存键加入 extras 集合，或将 `extraClasses` 限制为仅测试使用。

### 5.4（低）`MAX_ENTRIES` 与 `MAX_RECORDS` 存在 off-by-one

- `MAX_ENTRIES = 10_000` 统计的是 zip 条目（含 `index.json`、目录项），而 `MAX_RECORDS = 10_000` 统计脚本/版本记录（`ScriptBackupCodec.kt:18-19,54,94`）。因此 10,000 个脚本的备份（至少 10,001 个条目）会被拒绝，实际可导入上限约为 9,999 个脚本。极端场景，不影响正常使用；建议将条目上限设为记录上限 + 固定头部余量。

### 5.5（提示）合法“空备份”无法恢复

- `ScriptBackupCodec.validate()` 要求至少 1 个脚本（`ScriptBackupCodec.kt:95`），用户删除全部脚本后导出的备份再次导入会被拒绝（“备份中没有脚本”）。这是防止误清空的保守设计，属于产品取舍；如需要支持，应在 UI 明确提示并提供二次确认，而不是静默放宽校验。

### 5.6（提示）`UiSelector` 的 internal getter 被白名单暴露

- Kotlin `internal val selector` 在字节码中是 public 的 `getSelector$app_debug()`，被 `allowedMembers` 收入白名单（`ScriptSandboxTest.productionAllowlistOnlyCoversScriptApi` 未断言排除）。它返回 `Selector`，因不在白名单中而完全不可访问，无安全影响；可在生成白名单时过滤含 `$` 的编译器生成成员，减少暴露面。

## 6. 未覆盖 / 需人工验收的风险

- **真机 / OEM**：定时在 Doze、厂商保活策略下的行为未验证；本次仅在 API 35 模拟器验证。
- **可访问性**：TalkBack 对保存菜单 `contentDescription`、副标题、重命名对话框的播报，以及 2 倍字体下 toolbar/tab 布局未做人工验收（R5 验收项 6、7）。
- **恢复期间进程被杀**：5.1 的缺口未在设备上实际注入杀点复现（仅代码路径分析）；`recoversInterruptedSwapBeforeApplyingBackup` 覆盖的是“再次调用 restore”的分支，不能替代启动恢复。
- **并发**：恢复与正在运行的脚本、恢复与定时闹钟同时触发的竞态未压测。恢复换入新脚本到 `cancelAll` 之间（`ScriptBackup.kt:46-58`）若进程被杀，定时配置会保留并指向新内容，理论上仍有一次自动执行窗口（极窄）。
- **范围外**：SEC-004（通知/日志泄露）、SEC-006（大文件）、SEC-007（主线程 IO）、SEC-008（服务生命周期）、SEC-009（BootReceiver exported）、SEC-010（release 签名）、SEC-011（正则 DoS）不在本次承诺范围，未验证。
- **备份兼容性**：旧版本导出的备份使用 UUID ID，符合新规则；若存在更早期使用非 UUID 自定义 ID 的备份，新版本会将其视为损坏记录并拒绝导入（符合 SEC-005 的收紧目标，但需在产品说明中告知）。

## 7. 结论与上线建议

- 承诺范围内 **SEC-001 / SEC-002 / SEC-003 / SEC-005 / R5 的验收标准均通过**；P0 沙箱逃逸已封堵，备份完整性校验与两阶段恢复、ID/路径校验、恢复后清空定时、编辑器保存状态与重命名均有代码与测试证据。
- 发现的 1 项中等级问题（5.1 崩溃恢复入口缺失）只在“恢复过程中进程被杀”的窄窗口触发，不阻断本次上线，但建议在下一个版本修复或在发布说明中明确风险；其余为低风险/提示项。
- **建议上线**（可附条件：记录 5.1 为已知限制，并安排后续修复；发布前补一次 TalkBack/2 倍字体人工验收）。

---

### 附：测试轮次记录

| 阶段 | JVM | 仪器化 | 说明 |
| --- | --- | --- | --- |
| 基线复核 | 108/108 | — | 强制重跑原始套件 |
| 新增测试后首跑 | 126，1 失败 | — | `findAll(null)` 触发 Kotlin 非空参数检查（测试代码问题），修正 |
| 修正后 | 126/126 | — | |
| 追加全局对象用例 | 127/127 | — | 最终 JVM 结果 |
| 仪器化第 1 轮 | — | 52，1 失败 | `../` 条目在 Android 14+ 抛 `ZipException`，用例期望 `IllegalArgumentException` |
| 仪器化第 2 轮 | — | 52，1 失败 | 改为期望 `RuntimeException` 仍不匹配（`ZipException` 是受检 `IOException`） |
| 仪器化第 3 轮 | — | 52/52 | 改为接受 `Exception` 并断言数据不变；最终结果 |

## 8. 修复复核（v0.8.1）

本轮已修复 5.1–5.4、5.6：

- 5.1（中）：新增 `ScriptBackup.recoverInterruptedRestore(context)`，在 `MicroAutoApp.onCreate` 中先于 `ScriptRepository` 初始化调用；当前目录不可用且 `scripts-before-restore` 有效时自动回滚，当前有效时清理残留旧目录。新增仪器化测试 `recoversInterruptedRestoreBeforeRepositoryInit`、`keepsValidCurrentAndDropsStalePrevious`。
- 5.2（低）：`SandboxNativeJavaObject.getDefaultValue` 对 String/Number/Boolean/Char 返回底层值，其余对象返回空串；`String(native)` 不再泄露实现类名，新增 `defaultConversionDoesNotLeakClassName`。
- 5.3（低）：`allowedMembers` 缓存键改为 `(Class, extraClasses)`，新增 `allowlistCacheSeparatesExtraClasses`。
- 5.4（低）：`MAX_ENTRIES` 调整为 `2 * MAX_RECORDS + 3`，不再误拒 10,000 条脚本的合法备份；`rejectsTooManyEntries` 同步更新。
- 5.6（提示）：白名单过滤含 `$` 的编译器生成方法，internal getter 不再暴露。

验证结果（修复后）：

- JVM 单元测试 **129/129**
- API 35 仪器化测试 **54/54**
- Lint 0 error（仅既有 `core-ktx` 版本提示），`assembleDebug` 通过
- 5.5（空备份策略）保持产品取舍，未改动
- 结论：原报告中的中级/低级问题已关闭，无已知阻断项

---

## 9. 第二批验收（SEC-004 / R1 / R2）

- 测试日期：2026-10-21
- 代码基线：v0.8.1 + 工作区未提交改动（SEC-004 / R1 / R2）
- 范围：SEC-004（运行通知与 `dump()` 脱敏）、R1（运行环境引导与定时健康提示）、R2（首页行内运行）
- 测试角色：独立 QA；只读审查生产代码，仅新增/加强测试；未修改生产代码，未 commit / tag / push

### 9.1 环境与命令

环境同第 1 节：macOS（Apple Silicon）、JDK 17、Gradle 8.11.1 / AGP 8.10.1 / Kotlin 2.1.20、Android SDK platform 36 / build-tools 36.0.0、AVD `microauto_api35`（API 35，arm64-v8a）。

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./scripts/run-emulator-tests.sh   # 完整跑两轮
```

### 9.2 结果汇总

| 套件 | 数量 | 失败 | 错误 | 跳过 | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| JVM 单元测试（21 个类） | 136 | 0 | 0 | 0 | 通过 |
| 仪器化测试（API 35，9 个类） | 69 | 0 | 0 | 0 | 连续两轮 69/69 通过 |
| Android Lint | — | — | — | — | 0 errors，1 warning（既有 `core-ktx` 版本提示） |
| Debug 构建 | — | — | — | — | 成功，`app/build/outputs/apk/debug/MicroAuto-0.8.1.apk` |

- JVM：`134` 项原有/开发者自测 + `2` 项本次新增（`NodeDumpTest` 1、`RunStateTest` 1）= `136`。
- 仪器化分布：`SchedulePrefsTest 6`、`RunNotificationsInstrumentedTest 2`、`ScriptBackupRestoreTest 10`、`ScriptRepositoryPathTest 1`、`ScriptRepositoryTest 15`、`DialogLifecycleTest 4`、`EditorActivityTest 16`、`MainActivityTest 14`、`VersionsActivityTest 1`。
- 仪器化首轮（加入新测试后）69 项中 1 项失败，是**本次新增测试**的断言错误：`menuOnlyExposesUnifiedEnvironmentEntry` 断言菜单标题等于「定时」，实际运行时标题为「定时：未启用」；改为 `startsWith("定时")` 后，连续两轮 69/69，产品代码零失败。其余 68 项在三个完整/半完整轮次中均通过，未观察到 flaky。
- Lint 唯一 warning 仍是 `app/build.gradle.kts:104` 的 `GradleDependency`（`androidx.core:core-ktx 1.18.0` → `1.19.1`），与本次改动无关。

### 9.3 逐项验收

#### SEC-004（P1）运行通知与 dump 脱敏

**结论：通过（核心承诺），有 2 项加固建议。**

- 通知正文：`RunNotifications.notifyResult()` 只使用 `resultText(scriptName, success)`（脚本名 + 「运行完成/失败」）；`notifySkipped()` / `notifyFailure()` 只拼接脚本名与调用方给出的原因/错误；`RunNotifications` 已不再引用 `RunLog`，`ScriptService.finishRun()` 只传 `runningName`（`app/src/main/java/com/microauto/script/RunNotifications.kt:27-55`、`ScriptService.kt:107-115`）。不存在从日志取正文的路径。
- 可见性：结果、跳过、失败三类通知全部经过同一个私有 `notify()`，统一设置 `VISIBILITY_SECRET` 与 `setLocalOnly(true)`（`RunNotifications.kt:57-77`）。仪器化测试 `resultNotificationIsSecretLocalAndLogFree`、`skippedAndFailureNotificationsAreSecretAndLogFree` 读取 `NotificationManager.activeNotifications`，断言 `visibility == VISIBILITY_SECRET`、`NotificationCompat.getLocalOnly == true`，并断言正文不含预先写入 `RunLog` 的 `secret-screen-text`。
- `dump()` 打码：`NodeDump` 对 `isPassword` 节点输出 `text=<hidden>`，递归覆盖整棵树；普通节点的 `text`、`desc`、`bounds` 保持原样（`app/src/main/java/com/microauto/core/NodeDump.kt:11-25`；`NodeDumpTest` 3/3，含嵌套密码节点）。
- **未覆盖项**：`desc` 未打码（9.5.2）；前台服务通知未设置 SECRET/localOnly（9.5.3）；`dump()` 仍写入 `latest.log`，未提供关闭日志开关（9.5.4）。

#### R1（P0）运行环境引导与定时健康提示

**结论：通过（自动化覆盖主要状态与入口），设置页跳转/返回链路需手工验收。**

- 提示条：`updateSetupBanner()` 的优先级为「无障碍未开启」→「已开启 + 有启用定时 + 精确闹钟不可用」→ 隐藏；文案、图标、按钮与需求一致；`onResume` 重新计算；无障碍从关到开时在底部导航上方显示 Snackbar（`app/src/main/java/com/microauto/ui/MainActivity.kt:156-166,489-553`）。测试覆盖：无障碍未开时提示条可见、文案正确；点击提示条打开环境面板。
- 环境面板：四行状态、按钮 enabled 与系统 API 一致；测试在运行时用同一套 `AccessibilityUtils` / `POST_NOTIFICATIONS` / `canScheduleExactAlarms` / `isIgnoringBatteryOptimizations` 计算期望值并逐行断言（`MainActivityTest.environmentSheetStatusMatchesSystem`）。四行标题、48dp 按钮与布局符合需求（`sheet_environment.xml`）。
- 菜单：只保留统一的「运行环境」+「定时」等 7 项；旧「无障碍 / 精确闹钟 / 省电」菜单项与字符串已删除；测试直接断言 `toolbar.menu` 中不存在这三个前缀的标题。
- 编辑页拦截：无障碍未开启点「运行」弹出解释对话框（标题、正文、「去开启」「取消」）；取消后对话框消失且停留在编辑页（`EditorActivityTest.runWithoutAccessibilityShowsExplanationDialog`）。「去开启」会启动系统无障碍设置，返回后由 `awaitingAccessibilityRun` 触发一次 Snackbar +「运行」操作（`EditorActivity.kt:209-218,634-657`），代码审查确认标志只置位/复位一次；该链路未自动化（见 9.6）。
- 定时 sheet：开关打开且精确闹钟不可用时显示「精确闹钟未授权，定时可能延迟」+「去授权」，关闭开关后隐藏；保存 Toast 带「可能延迟」（`EditorActivity.kt:1020-1035,1065-1080`；`EditorActivityTest.scheduleSheetWarnsWhenExactAlarmMissing` 断言显示与隐藏两种状态）。
- **窗口生命周期**：环境面板与运行拦截对话框未在 `onDestroy` 释放，logcat 出现 `WindowLeaked`（9.5.1）。

#### R2（P0）首页行内运行

**结论：通过（UI 状态与事件路径），真实服务启动/停止需手工或后续仪器化验证。**

- `RunState`：新增 `activeScriptId`，`start()` 同时置 running 与 id，`finish()`/`setRunning(false)` 清空（`app/src/main/java/com/microauto/script/RunState.kt:13-26`）。`ScriptService` 在接受运行后 `RunState.start(scriptId)`（`ScriptService.kt:87`），`finishRun()`、`stopRun()`、`onDestroy()` 均调用 `RunState.finish()`（`ScriptService.kt:107-126,181-188`）；`ScriptRunner` 的 `finally` 也 `setRunning(false)`（`ScriptRunner.kt:61-66`）。`RunStateTest` 覆盖 start/finish/setRunning(false)/重复 start/finish。
- 行内按钮：`LibraryAdapter` 动态设置 48dp 播放按钮的可见性、enabled 与 contentDescription；运行行显示 16dp 进度圈并隐藏按钮，其他行禁用并读作「已有脚本在运行」（`app/src/main/java/com/microauto/ui/LibraryAdapter.kt:39-57,102-121`；`item_script.xml`）。
- 点击路径：按钮独立 `OnClickListener`，父卡片仍负责进入编辑页；Espresso 断言点击行内按钮后 `scriptEditor` 不存在、脚本内容未被修改（`MainActivityTest.inlineRunButtonExplainsMissingAccessibility`）。
- 运行态：`MainActivityTest.runningStateShowsSpinnerAndDisablesOtherRows` 直接设置 `RunState`，断言运行行进度圈可见、按钮隐藏、描述含脚本名，空闲行按钮禁用且描述为「已有脚本在运行」；`RunState.finish()` 后重新绑定恢复。
- **风险**：`activeScriptId` 与既有 SEC-008 生命周期竞态共存，旧运行的收尾可能清掉新运行的状态（9.5.6）。

### 9.4 新增 / 加强的测试

| 文件 | 数量 | 内容 |
| --- | ---: | --- |
| `app/src/androidTest/java/com/microauto/ui/DialogLifecycleTest.kt` | 4（新增） | 环境面板、Main/Editor 运行拦截对话框、定时 sheet 在 Activity 关闭后不残留 |
| `app/src/androidTest/java/com/microauto/script/RunNotificationsInstrumentedTest.kt` | 2（新增） | 结果/跳过/失败通知的 `VISIBILITY_SECRET`、`localOnly`、正文不含 `RunLog` |
| `app/src/androidTest/java/com/microauto/ui/MainActivityTest.kt` | +3 | 环境面板状态与按钮、提示条打开面板、菜单旧入口已移除 |
| `app/src/test/java/com/microauto/core/NodeDumpTest.kt` | +1 | 嵌套密码节点全部打码；普通节点 `desc` 保持 |
| `app/src/test/java/com/microauto/script/RunStateTest.kt` | +1 | 重复 `start` 覆盖 id、`finish` 幂等 |
| `app/src/androidTest/java/com/microauto/ui/EditorActivityTest.kt` | 加强 2 | 取消后对话框消失；定时警告随开关隐藏 |

说明：`DialogLifecycleTest` 的 4 个用例在 API 35 上均通过（旧窗口不会遮挡后续交互），但 logcat 同时记录了 `WindowLeaked`，说明“行为通过”不代表窗口被正确释放（见 9.5.1）。

### 9.5 发现的问题

#### 9.5.1（中）环境面板与两个运行拦截对话框未在 `onDestroy` 释放，logcat 记录 `WindowLeaked`

**证据：**

- `MainActivity.showEnvironmentSheet()` 使用局部 `BottomSheetDialog`，无字段跟踪、无 `onDestroy` 关闭（`app/src/main/java/com/microauto/ui/MainActivity.kt:274-318`，`dialog.show()` 在 317 行）。
- `MainActivity.showAccessibilityRequiredDialog()` 与 `EditorActivity.showAccessibilityRequiredDialog()` 同样使用局部 `MaterialAlertDialogBuilder(...).show()`（`MainActivity.kt:777-785`、`EditorActivity.kt:648-657`）。
- 对照：`EditorActivity.onDestroy()` 会 dismiss `scheduleDialog`、`findDialog`、`renameDialog`（`EditorActivity.kt:230-236`），`DialogLifecycleTest.scheduleSheetIsDismissedWithActivity` 的 logcat 中没有 `WindowLeaked`。
- 两轮完整仪器化测试的 logcat 均出现：
  - `WindowLeaked: Activity com.microauto.ui.MainActivity has leaked window ... at com.microauto.ui.MainActivity.showEnvironmentSheet(MainActivity.kt:317)`
  - `WindowLeaked: ... at com.microauto.ui.MainActivity.showAccessibilityRequiredDialog(MainActivity.kt:785)`
  - `WindowLeaked: ... at com.microauto.ui.EditorActivity.showAccessibilityRequiredDialog(EditorActivity.kt:657)`

**影响：** 与上一轮“重命名对话框残留导致测试失焦”属于同一类窗口生命周期问题。当前用例仍能通过（系统会移除旧窗口），但窗口泄漏日志、慢设备/配置变化下的焦点竞争、以及未来测试顺序变化都可能再次触发失焦；不应依赖系统兜底。

**建议：** 按 `renameDialog` / `scheduleDialog` 的既有模式，为三个对话框增加字段与 `setOnDismissListener`，在 `EditorActivity.onDestroy` / `MainActivity.onDestroy` 中 dismiss；或改用 `DialogFragment`。测试侧已在 `DialogLifecycleTest` 中保留回归保护。

#### 9.5.2（低）`NodeDump` 只打码 `text`，未打码 `desc`

- `NodeDump.kt:14` 对密码节点的 `text` 输出 `<hidden>`，但 `NodeDump.kt:19` 仍原样输出 `node.desc`。
- 复现（临时 JVM 探针，已删除）：`NodeDump.format(FakeNode(className="android.widget.EditText", text="hunter2", desc="hunter2", isPassword=true))` 输出：
  `android.widget.EditText id=- text=<hidden> desc=hunter2 clickable=false bounds=-`
- 影响：密码值通常位于 `text`，但若某些 App 把敏感内容放在 `contentDescription`，仍会进入 `dump()`/`latest.log`。属低概率泄露。
- 建议：`isPassword` 时同时输出 `desc=<hidden>`（或复用同一个 `<hidden>` 标记），并补一条 `NodeDumpTest`。

#### 9.5.3（低）前台服务通知未设置 `VISIBILITY_SECRET` / `localOnly`

- 结果/跳过/失败通知走 `RunNotifications.notify()`，已设置 `VISIBILITY_SECRET` + `setLocalOnly(true)`；但前台服务常驻通知由 `ScriptService.buildNotification()` 单独构建，未设置这两项（`app/src/main/java/com/microauto/script/ScriptService.kt:141-162`）。
- 该通知正文只有「正在运行：<脚本名>」，不含屏幕/日志内容；但锁屏上会显示脚本名，若脚本名本身含账号/用途等敏感信息，仍有轻度暴露。
- 建议：与结果通知保持一致，设置 `VISIBILITY_SECRET` + `setLocalOnly(true)`。

#### 9.5.4（提示）SEC-004 的日志策略未完成

- `dump()` 仍通过 prelude 的 `log()` 写入 `RunLog` / `latest.log`；没有“关闭日志”设置或退出清理。审计的完整验收包含“关闭日志后 `latest.log` 不写入内容”，本次承诺只覆盖密码打码与通知脱敏。
- 非密码屏幕文本（聊天、验证码之外的普通内容）仍会长期保存在私有目录；`allowBackup=false` 降低了外泄面，但取证/误分享日志时仍有风险。
- 建议：后续增加日志开关或启动清理策略；在 UI/教程中说明日志可能包含屏幕内容。

#### 9.5.5（提示）R1/R2 的关键人工路径未自动化

- R1：`去开启` / `去授权` / `去设置` 跳转系统设置、返回后的 Snackbar「无障碍服务已开启」+「运行」、提示条的「定时可能延迟」变体（需无障碍已开启 + 有启用定时 + 精确闹钟缺失）、TalkBack 与 2 倍字体布局均未自动化；本次只验证了对话框/面板的展示、取消和状态计算。
- R2：真实 `ScriptService` 启动/停止（需要无障碍开启）未在仪器化中执行；`activeScriptId` 的清理通过单元测试与代码审查验证，未覆盖实际服务生命周期。

#### 9.5.6（提示）`activeScriptId` 继承 SEC-008 生命周期竞态

- `ScriptService` 旧运行的 `finally` 仍可能在新运行启动后调用 `finishRun()`/`onDestroy()`，从而 `RunState.finish()` 清掉新运行的 `activeScriptId`，导致首页进度圈提前消失、其他行提前恢复（`ScriptService.kt:91-98,107-126,181-188`）。
- SEC-008（服务生命周期 token）不在本批范围；R2 让该竞态的 UI 表现更明显，建议在 SEC-008 修复时一并覆盖服务级 start/stop/start 测试。

### 9.6 未覆盖 / 需人工验收的风险

- **系统设置往返**：无障碍、精确闹钟、通知权限、电池优化的系统页跳转与返回后的状态刷新/Snackbar 去重，需要真机或手工验收；本次只验证按钮/行存在与对话框行为。
- **提示条定时变体**：模拟器未开启无障碍，无法稳定进入「定时可能延迟」分支；需在已开启无障碍且存在启用定时的设备上验收。
- **真实运行链路**：R2 的 `ScriptService` 启动、运行中、停止、超时、进程被杀后的 `activeScriptId` 清理未做设备级压测；SEC-008 竞态未验证。
- **可访问性**：TalkBack 对行内运行按钮/进度圈、环境面板四行按钮、保存菜单 contentDescription 的播报，2 倍字体下的布局均未人工验收。
- **通知**：不同厂商 ROM 对 `VISIBILITY_SECRET`/`localOnly` 的呈现、无通知权限时的行为未验证；前台服务通知的锁屏显示未验证。
- **范围外**：SEC-006（大文件）、SEC-007（主线程 IO）、SEC-008（服务生命周期）、SEC-009（BootReceiver）、SEC-010（release 签名）、SEC-011（正则 DoS）仍未验证。

### 9.7 结论与上线建议

- **SEC-004 / R1 / R2 的核心承诺均通过**：结果通知不再包含任何 `RunLog` 文本，三类通知统一 `VISIBILITY_SECRET` + `localOnly`；`dump()` 对密码 `text` 打码；环境提示条/面板/菜单合并/编辑页拦截/定时警告可用；首页行内运行、进度圈、其他行禁用与 `activeScriptId` 清理有测试与代码证据。
- 发现的 1 项中等级问题是**窗口生命周期**（9.5.1），当前测试不失败但 logcat 有 `WindowLeaked`，与上一轮已修复的焦点泄漏同类，建议在合并前或下个补丁按既有模式修复；其余 2 项低级为密码 `desc` 与前台通知加固，2 项提示为日志策略与人工验收缺口。
- **建议上线**（附条件：修复 9.5.1 的 `onDestroy` 释放；将 9.5.2/9.5.3 纳入后续加固；发布前补一次系统设置往返、TalkBack 与 2 倍字体人工验收）。

### 附：第二轮测试轮次记录

| 阶段 | JVM | 仪器化 | 说明 |
| --- | --- | --- | --- |
| 基线复核 | 134/134 | —（开发者自测 60） | JVM 强制重跑；仪器化未单独跑基线 |
| 新增测试后首轮 | 136/136 | 69，1 失败 | 失败为我的菜单标题断言（「定时」实际为「定时：未启用」），修正 |
| 完整第 A 轮 | — | 69/69 | `run-emulator-tests.sh` |
| 完整第 B 轮 | — | 69/69 | `run-emulator-tests.sh`，未见 flaky |
| 最终 lint/assemble | — | — | 0 errors / 1 warning；`MicroAuto-0.8.1.apk` |

### 9.8 修复复核

- 9.5.1（中，窗口泄漏）已修复：`MainActivity` 持有运行环境面板与运行拦截对话框，`EditorActivity` 持有运行拦截对话框，均在 `onDestroy` 中 `dismiss()` 并通过 `setOnDismissListener` 置空；`DialogLifecycleTest` 4 项通过，最终仪器化 logcat 无 `WindowLeaked`。
- 9.5.2（低，密码 desc）已修复：`NodeDump` 对密码节点同时输出 `text=<hidden>` 与 `desc=<hidden>`，`NodeDumpTest` 覆盖。
- 9.5.3（低，前台通知）已修复：`ScriptService` 常驻通知增加 `VISIBILITY_SECRET` + `localOnly`。
- 复核结果：JVM 单元测试 **137/137**，仪器化 **69/69**，Lint 0 error（1 既有警告），`assembleDebug` 通过。
- 仍未覆盖：关闭日志设置、系统设置往返、TalkBack/大字号、SEC-008 生命周期竞态（均按约定留待后续）。

---

## 10. 第三批验收（SEC-006 / SEC-009 / SEC-010 / SEC-011）

- 测试日期：2026-10-21
- 代码基线：v0.9.0 + 工作区未提交改动
- 范围：SEC-009（BootReceiver 导出收紧）、SEC-010（release 签名 fail-fast）、SEC-011（正则防护）、SEC-006（有界导入 / 编辑器 / 运行大小限制，部分）
- 测试角色：独立 QA；只读审查生产代码，仅新增/加强测试；未修改生产代码，未 commit / tag / push

### 10.1 环境与命令

环境同前：macOS（Apple Silicon）、JDK 17、Gradle 8.11.1 / AGP 8.10.1 / Kotlin 2.1.20、Android SDK platform 36 / build-tools 36.0.0、AVD `microauto_api35`（API 35，arm64-v8a）。

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./scripts/run-emulator-tests.sh   # 完整跑两轮

# SEC-010
RELEASE_KEYSTORE_PATH=/tmp/nonexistent ./gradlew :app:assembleRelease --dry-run
RELEASE_KEYSTORE_PATH="$PWD/signing/debug.keystore" \
  RELEASE_KEYSTORE_PASSWORD=android RELEASE_KEY_ALIAS=androiddebugkey \
  RELEASE_KEY_PASSWORD=android ./gradlew :app:assembleRelease --dry-run
RELEASE_KEYSTORE_PATH=/tmp/nonexistent ./gradlew :app:assembleDebug --dry-run
```

### 10.2 结果汇总

| 套件 | 数量 | 失败 | 错误 | 跳过 | 结论 |
| --- | ---: | ---: | ---: | ---: | --- |
| JVM 单元测试（类数随新增测试增加） | 165 | 0 | 0 | 0 | 通过 |
| 仪器化测试（API 35） | 74 | 0 | 0 | 0 | 最终连续两轮 74/74 通过 |
| Android Lint | — | — | — | — | 0 errors，1 warning（既有 `core-ktx` 版本提示） |
| Debug 构建 | — | — | — | — | 成功，`app/build/outputs/apk/debug/MicroAuto-0.9.0.apk` |

- JVM：开发者自测 `154`，本轮新增 `11` 项（正则/边界/流式/JS 捕获等）后 `165/165`；0 skipped。
- 仪器化：开发者自测 `70`，本轮新增 `4` 项（`ManifestTest` 2、`ScriptServiceStartGuardTest` 1、Editor 1MB 用例 1）后 `74`；最终两轮 `74/74`，0 skipped。
- 仪器化分布（最终轮）：`ManifestTest 2`、`SchedulePrefsTest 6`、`RunNotificationsInstrumentedTest 2`、`ScriptBackupRestoreTest 10`、`ScriptRepositoryPathTest 1`、`ScriptRepositoryTest 15`、`ScriptServiceStartGuardTest 1`、`DialogLifecycleTest 4`、`EditorActivityTest 18`、`MainActivityTest 14`、`VersionsActivityTest 1`。
- **SEC-010 命令结果**：
  - `RELEASE_KEYSTORE_PATH=/tmp/nonexistent ./gradlew :app:assembleRelease --dry-run` → **exit 1**，错误信息：`release keystore 未配置：请设置 RELEASE_KEYSTORE_PATH、RELEASE_KEYSTORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD，或在 keystore.properties 中配置签名信息。release 构建不再回退 debug 签名。`
  - `RELEASE_KEYSTORE_PATH=$PWD/signing/debug.keystore ... assembleRelease --dry-run` → **exit 0**，`packageRelease`/`assembleRelease` 均为 `SKIPPED`（仅 dry-run，未生成/发布使用 debug key 的 release 包）。
  - `RELEASE_KEYSTORE_PATH=/tmp/nonexistent ./gradlew :app:assembleDebug --dry-run` → **exit 0**，debug 构建不受影响。
  - 常规 `testDebugUnitTest + lintDebug + assembleDebug` → 成功。
- **仪器化 flaky 说明**：加入新测试后的第二轮完整运行 74 项中 1 项失败（`characterOversizedScriptIsBlockedWithMessage` 抛 `RootViewWithoutFocusException`），根因是编辑器超限对话框在 `setContentView` 之前显示，Activity 基础窗口无焦点；测试侧用 `inRoot(isDialog())` 定向到对话框窗口后，连续 3 次定向复跑与最终两轮完整运行均通过（产品代码未改，问题见 10.5.2）。

### 10.3 逐项验收

#### SEC-009（P2）BootReceiver 导出收紧

**结论：通过（静态 + manifest 断言），系统广播实际送达未设备验证。**

- `AndroidManifest.xml` 中 `BootReceiver` 已改为 `android:exported="false"`，仍保留 `BOOT_COMPLETED` / `TIME_SET` / `TIMEZONE_CHANGED` 三个 filter；`AutomationService` 仍为 `exported="true"`（系统绑定所必需），`ScheduleReceiver`/`ScriptService` 保持 `exported=false`。
- 新增 `ManifestTest`：
  - `bootReceiverIsUnexportedAndKeepsSystemFilters`：`PackageManager.getReceiverInfo(...).exported == false`，并通过 `queryBroadcastReceivers` 对三个 action 解析到 `BootReceiver`（同包查询）；
  - `automationServiceRemainsExportedForSystemBinding`：`AutomationService` 仍导出。
- 静态结论：系统（system UID）仍可向非导出 receiver 投递 protected broadcast；第三方应用无法显式发送到该 receiver。实际重启/改时间/改时区后的重注册未在设备上验证（见 10.6）。

#### SEC-010（P2）release 缺少 keystore 时 fail-fast

**结论：通过。**

- `app/build.gradle.kts` 移除 release → debug 的签名回退；`gradle.taskGraph.whenReady` 在任务图中出现 `assembleRelease`/`bundleRelease`/`packageRelease`/`installRelease` 且 `releaseSigningConfig.storeFile == null` 时抛 `GradleException`，错误信息明确列出环境变量与 `keystore.properties` 两种配置方式。
- 签名配置只在 `RELEASE_KEYSTORE_PATH`（或 `keystore.properties.storeFile`）指向的**文件存在**时设置 `storeFile`；因此 `/tmp/nonexistent` 会触发 fail-fast，而不是拖到签名阶段。
- 实测：无 release keystore 的 `assembleRelease --dry-run` exit 1（信息清晰）；用仓库 debug keystore 作为 `RELEASE_KEYSTORE_PATH` 的 dry-run exit 0；`assembleDebug` 在 `RELEASE_KEYSTORE_PATH=/tmp/nonexistent` 下仍成功。
- CI：`.github/workflows/android.yml` 未改动；tag/workflow_dispatch 先执行 `Decode release keystore` 生成 `$RUNNER_TEMP/microauto-release.jks`，随后 `Test, lint and build` 步骤把同一路径注入 `RELEASE_KEYSTORE_PATH` 并仅在 tag 分支执行 `assembleRelease`，因此新 fail-fast 不会阻断 PR 的 debug 构建，tag 构建仍可用（静态审查；未实际跑 CI tag）。
- **行为变化提示**：任何包含 release 产物的任务图（如 `./gradlew assemble` 或 `./gradlew build`）现在在缺少 release keystore 时都会失败，这是 fail-fast 的预期结果，但本地开发者需要知晓；`testDebugUnitTest`、`lintDebug`、`assembleDebug` 不受影响。

#### SEC-011（P2）正则防护

**结论：核心验收通过（1MB + `(a+)+$` 被快速拒绝，正常正则不受影响，异常可被脚本捕获），但静态规则是启发式，仍有误伤与漏判（见 10.5.1）。**

- `RegexGuard`：`MAX_PATTERN_CHARS=1024`、`MAX_TEXT_CHARS=4096`；`validate()` 拒绝超长与“组内出现 `*`/`+` 且组后紧跟 `*`/`+`/`{`”的模式；`matches()` 先拒绝超长文本/不安全 pattern，再执行 `find()`（`app/src/main/java/com/microauto/core/RegexGuard.kt:10-68`）。
- 接入：`UiSelector.textMatches()` 在 `Pattern.compile` 前 `RegexGuard.validate(source)`；`Selector.matches()` 用 `RegexGuard.matches(textPattern, nodeText)` 替代直接 `matcher.find()`（`UiSelector.kt:15-19`、`Selector.kt:22`）。
- 测试证据：
  - `RegexGuardTest.oneMegabyteTextWithNestedQuantifierReturnsImmediately`：1,000,000 字符 + `(a+)+$` 在 1 秒内返回 false（实际远低于 1 秒）。
  - `textLongerThanLimitIsNotMatched`：4096 字符可匹配，4097 不匹配。
  - `RegexGuardTest.acceptsGroupedAndAlternationPatterns` / `UiSelectorTest`：`a+b`、`(ab)+`、`(a|b)+`、`(?:ab)+`、`(a+)?`、`(abc){2}`、`\d{2,4}` 均被接受；1024 字符边界接受、1025 拒绝。
  - `PreludeSmokeTest.rejectedRegexIsCatchableFromScript`：真实 Rhino 下 `try { textMatches(/(a+)+/) } catch (e)` 可捕获，日志包含“嵌套量词”，脚本不会因未捕获异常直接中断。
  - `UiSelectorTest` 覆盖超长/嵌套拒绝；`ApiDocs`/教程已补充限制说明。
- 限制说明：`(a|aa)+$`、`(a{2,})+`、`(a?)+` 通过静态检查；4096 字符文本下仍可能发生灾难性回溯（见 10.5.1）。`(a+){2}` 这类有界重复被误判为嵌套量词（误伤）。

#### SEC-006（P2，部分）有界导入与编辑器/运行上限

**结论：已实现的三项守卫均通过，无半成品文件；导入 >20,000 行的 UX 与编辑/保存阶段的限制仍不完整（见 10.5.3/10.5.4）。**

- 导入：`MainActivity.importFromUri()` 在 IO 线程用 `BoundedTextReader.read()` 读取（8 KB 缓冲、上限 1,000,000 字节、NUL 拒绝），读取成功后才 `repository.create()`；失败由 `runCatching` 转成 `导入失败：...` Toast（`MainActivity.kt:957-973`）。没有“先建脚本再读文件”的半成品路径。
  - `BoundedTextReaderTest.stopsReadingShortlyAfterByteLimit` 用无限流验证：抛错时实际读取 ≤ 上限 + 8 KB，未读完整个流；`acceptsExactlyByteLimit` 验证 1,000,000 字节边界；NUL 拒绝。
- 编辑器：`EditorActivity.onCreate()` 在 inflate/setContentView 之前读取内容并调用 `ScriptLimits.editorError()`；超 1,000,000 字符或 20,000 行时显示“无法打开脚本”对话框并 `finish()`（`EditorActivity.kt:143-148,690-702`）。行号渲染也限制到 20,000 行（超出显示 `…`）。
  - `EditorActivityTest.oversizedScriptIsBlockedWithMessage`（20,001 行）与 `characterOversizedScriptIsBlockedWithMessage`（1,000,001 字符）验证提示与关闭；正常脚本用例全部保持通过。
- 运行：`ScriptService.start()` 统一调用 `ScriptLimits.runError()`，超过 512 KB（UTF-8 字节）抛 `IllegalArgumentException`（`ScriptService.kt:33-35`）。三条路径：首页 `runScript()` 预检 + `runCatching`、编辑页 `runCurrentScript()` 预检 + `runCatching`、`ScheduleReceiver` 的 `runCatching { ScriptService.start(...) }` 转失败通知。
  - `ScriptLimitsTest` 覆盖 512 KB 精确边界、512 KB+1 ASCII、多字节 UTF-8 超限；`ScriptServiceStartGuardTest` 验证超限脚本在服务启动前被拒绝。

### 10.4 新增 / 加强的测试

| 文件 | 数量 | 内容 |
| --- | ---: | --- |
| `app/src/test/java/com/microauto/core/RegexGuardTest.kt` | +3 | 常见分组/交替模式不误伤、1024 边界、`Selector` 正常匹配 |
| `app/src/test/java/com/microauto/core/BoundedTextReaderTest.kt` | +2 | 无限流在超限后停止读取、恰好 1,000,000 字节可读 |
| `app/src/test/java/com/microauto/core/ScriptLimitsTest.kt` | +3 | 编辑器 1,000,000 字符边界、运行 512 KB 边界、多字节 UTF-8 超限 |
| `app/src/test/java/com/microauto/api/UiSelectorTest.kt` | +2 | 分组/交替模式、1024 字符边界 |
| `app/src/test/java/com/microauto/script/PreludeSmokeTest.kt` | +1 | 正则拒绝异常在真实 Rhino 脚本中可捕获并带中文提示 |
| `app/src/androidTest/java/com/microauto/ui/EditorActivityTest.kt` | +1（并加强 2） | 1,000,001 字符脚本拒绝；超限对话框改用 `inRoot(isDialog())` 定向窗口，消除焦点 flake |
| `app/src/androidTest/java/com/microauto/ManifestTest.kt` | 2（新增） | BootReceiver `exported=false` + 三个系统 action 仍可解析；AutomationService 仍导出 |
| `app/src/androidTest/java/com/microauto/script/ScriptServiceStartGuardTest.kt` | 1（新增） | 超 512 KB 脚本在 `ScriptService.start` 之前被拒绝 |

### 10.5 发现的问题

#### 10.5.1（中）`RegexGuard` 静态规则不完整：漏判灾难性回溯，并误伤有界重复

**探测结果（`isSafe`，临时 JVM 探针，已删除）：**

```
a+b          -> true      (ab)+        -> true
(a|b)+       -> true      (?:ab)+      -> true
(a+)?        -> true      \d{2,4}      -> true
(abc){2}     -> true      (a+){2}      -> false
(a{2,})+     -> true      (a?)+        -> true
(a|aa)+      -> true
```

- **漏判（安全）**：`RegexGuard.hasNestedQuantifier()` 只跟踪组内的 `*`/`+`，不跟踪 `{m,n}` 和 `?`；`(a|aa)+$`、`(a{2,})+`、`(a?)+` 均通过。`RegexGuard.matches()` 允许最多 4096 字符节点，`(a|aa)+$` 对 4096 个 `a` + 结尾 `b` 仍可指数级回溯，恶意脚本可让运行线程长时间挂起，SEC-011 的“脚本可控正则不能 DoS”目标未完全达成。代码：`app/src/main/java/com/microauto/core/RegexGuard.kt:32-68`、`RegexGuard.kt:26-30`。
- **误伤（兼容）**：`(a+){2}` 被判定为“嵌套量词”并抛 `IllegalArgumentException`，但这是有界重复、通常安全；`(a+){0,1}` 同理。使用这类正常模式的脚本会在 `textMatches()` 构造阶段直接失败。代码：`RegexGuard.kt:57`（`next == '{'` 一律拒绝）。
- 说明：1MB + `(a+)+$` 的专门验收已通过，文档也明确“缓解，不是完整线性正则引擎”；但既然产品承诺“静态拒绝嵌套量词”，建议至少：
  - 优先引入 RE2/J 或等价线性引擎；
  - 或把 `{m,n}` 纳入解析并只在真正嵌套/无界时拒绝，减少 `(a+){2}` 误伤；
  - 或把 `MAX_TEXT_CHARS` 降到更保守的值，并对 `(a|aa)+` 这类交替歧义做额外检测；
  - 增加运行侧 watchdog/超时（当前 Java 正则不响应线程中断）。

#### 10.5.2（中）编辑器超限错误对话框在 `setContentView` 之前显示，基础窗口无焦点

- `EditorActivity.onCreate()` 在 inflate/setContentView 之前读取内容；超限时 `showOpenError(message); return`，此时 Activity 没有内容视图（`EditorActivity.kt:143-148`），`showOpenError()` 用 Activity context 直接 `AlertDialog.show()`（`EditorActivity.kt:690-702`）。
- 全量仪器化第二轮复现：`characterOversizedScriptIsBlockedWithMessage` 抛 `RootViewWithoutFocusException`，等待 10 秒仍无窗口焦点；失败日志中的根为应用基础窗口 `has-window-focus=false`、`child-count=2`（空 DecorView），对话框窗口虽存在但 Espresso 默认选中了基础窗口。定向 3 次复跑 + 最终两轮完整运行在测试使用 `inRoot(isDialog())` 后通过。
- 影响：真实设备上用户能看到对话框（对话框自身窗口有焦点），但 Activity 基础窗口始终无焦点/空白，存在时序性焦点问题，且会使 UI 测试不稳定；与前两轮“对话框焦点”问题同类。
- 建议：先 `setContentView(binding.root)` 再显示错误对话框（或 `binding.root.post { showOpenError(...) }`），或在 `MainActivity` 打开编辑器前先做 `ScriptLimits.editorError` 检查；错误对话框继续在 `onDestroy` 中 dismiss（当前已跟踪）。

#### 10.5.3（低）导入超过 20,000 行不会在导入阶段拒绝，只会在打开编辑器时提示

- `MainActivity.importFromUri()` 只做 1 MB 字节/NUL 检查，随后 `repository.create()` 并用 `openEditor(meta.id)` 打开；`EditorActivity` 才用 `ScriptLimits.editorError()` 拒绝 >20,000 行并 `finish()`（`MainActivity.kt:957-973`、`EditorActivity.kt:143-148`）。
- 结果：用户导入一个 1 MB 换行文件会“导入成功”，随即看到“无法打开脚本”，脚本仍留在列表中且无法编辑；虽不会 ANR（满足 SEC-006 的主要验收），但 UX 不佳。
- 建议：导入前调用 `ScriptLimits.editorError(content)`，直接提示“脚本超过 20,000 行，未导入”；或在错误对话框中提供删除入口。

#### 10.5.4（低）编辑器 1 MB/20k 限制只在“打开”时生效，编辑/保存可越过；1 MB 编辑与 512 KB 运行限制不一致

- `EditorActivity.saveScript()` 未调用 `ScriptLimits.editorError()`（`EditorActivity.kt:729` 起）；用户可以在已打开的编辑器里粘贴 >1 MB 或 >20,000 行内容并保存，下次打开才被拒绝。
- `ScriptLimits.MAX_EDITOR_CHARS=1,000,000` 而 `MAX_RUN_BYTES=512 KB`：512 KB–1 MB 的脚本可以保存/打开，但无法运行；用户只有在点运行时才看到“脚本过大”。这不是安全漏洞，但属于产品规则不一致。
- 建议：保存前做同样的 editor 限制并给出明确提示，或在 UI/教程中明确“可编辑但不可运行”的区间；若希望统一，可把运行上限与编辑上限对齐（受 Binder 限制约束）。

#### 10.5.5（提示）SEC-009 的系统广播实际送达未设备验证

- `ManifestTest` 只验证 manifest `exported=false` 与同包查询可解析 filter；真正的 `BOOT_COMPLETED`/`TIME_SET`/`TIMEZONE_CHANGED` 由 system UID 投递到非导出 receiver 的链路需要重启/改时间/改时区实测。Android 文档与现有 manifest 结构支持该行为，但本轮未做设备级验证。

#### 10.5.6（提示）SEC-010 的 CI tag 路径与 `assemble/build` 行为变化

- CI tag 路径仅做静态审查（decode → `RELEASE_KEYSTORE_PATH` → `assembleRelease`），未实际跑 GitHub Actions；工作流未改动，PR 的 debug 构建不受影响。
- 现在任何包含 release 产物的任务图（`assemble`、`build`）在缺少 release keystore 时都会 fail-fast；这是预期的安全收紧，但本地开发者需要改用 `assembleDebug` 或配置 release keystore。

### 10.6 未覆盖 / 需人工验收的风险

- **Regex 灾难性回溯**：未实际运行 `(a|aa)+$` 对 4096 字符（会长时间挂起）；仅通过静态分析/小规模矩阵确认规则漏判。若需要量化，应在隔离进程/超时保护下测试。
- **SAF 导入 UI**：`BoundedTextReader` 的流式/超限由单元测试覆盖，但系统文件选择器、无限/阻塞流、导入失败 Toast 的端到端路径未在设备上验证。
- **真实运行/定时链路**：512 KB 守卫由 `ScriptService.start` 单元/仪器化测试覆盖，但真实无障碍运行、定时触发大脚本的完整链路未跑（需要开启无障碍）。
- **BootReceiver 运行时**：未重启设备或改系统时间/时区验证重注册；仅 manifest 静态断言。
- **CI release**：未执行 tag 推送/真实 secrets 构建，未验证 `apksigner` 证书输出。
- **编辑器大脚本**：1 MB/20k 限制在低内存设备上的主线程读取/对话框表现未压测；>4096 字符节点在 `textMatches` 下会静默不匹配（有意限制，需在文档中说明）。
- **范围外**：SEC-007 主线程 IO、SEC-008 服务生命周期、SEC-004 日志开关等仍未验证。

### 10.7 结论与上线建议

- **SEC-009、SEC-010 通过**；SEC-006 的导入有界读取、编辑器 1 MB/20k 打开守卫、运行 512 KB 统一守卫均通过；SEC-011 的 1 MB + `(a+)+$` 验收、正常正则与脚本异常捕获均通过。
- 主要遗留是 **RegexGuard 的启发式边界**（10.5.1）：静态规则漏判 `(a|aa)+` 等灾难性模式且误伤 `(a+){2}`；由于应用运行的是用户脚本，最坏影响是脚本自我 DoS/运行线程挂起，不构成新的权限逃逸。其次为 **编辑器超限对话框的焦点时序**（10.5.2），会让 UI 测试 flaky、真实设备上基础窗口无焦点。
- **建议上线（附条件）**：
  1. 在发布说明/教程中明确 RegexGuard 只是缓解，并限制 `textMatches` 的使用；下一个补丁优先引入 RE2/J 或更严格的解析/超时保护；
  2. 修复编辑器超限对话框的显示顺序（先 `setContentView` 或 `post`），消除焦点 flake；
  3. 视产品取舍在导入/保存阶段提前应用 20,000 行与 1 MB 限制；
  4. 发布前补一次真实重启/改时间、SAF 导入和 CI tag 构建验证。

### 10.8 修复复核（第四轮，2026-10-21）

复核范围：RegexGuard 边界、编辑器超限对话框显示顺序、导入阶段 20,000 行拒绝、统一 512KB/20k 限制与保存检查。只修改测试代码；临时探针已删除；未 commit/tag/push。

#### 10.8.1 结果汇总

| 项 | 结果 |
| --- | --- |
| JVM 单元测试 | **165/165 通过**（0 失败/0 跳过） |
| 仪器化测试（API 35） | **75/75 通过，连续两轮**（`run-emulator-tests.sh`） |
| Lint | 0 errors，1 warning（既有 `core-ktx` 版本提示） |
| assembleDebug | 成功，`MicroAuto-0.9.0.apk` |
| 焦点/窗口日志（完整两轮） | `RootViewWithoutFocusException` **0**、`WindowLeaked` **0**、`TransactionTooLargeException` **0**（全量 logcat） |
| 定向编辑器超限用例 | 打开超限对话框 2/2 通过；保存 dirty 用例在完整轮通过 |

修复前的问题已按要求处理：`(a+){2}`、`(a{2})+`、`(a+)?` 放行；`(a?)+`、`(a{2,})+`、`(a{0,1})+`、`(a|aa)+` 拒绝；`a+b`、`(ab)+` 行为不变；1MB + `(a+)+$` 仍快速返回。编辑器错误对话框改为 `setContentView` 后 `post` 显示并由字段持有/`onDestroy` 释放；导入在 `repository.create` 前调用 `ScriptLimits.scriptError`；限制统一为 512 KB + 20,000 行，保存也检查。

#### 10.8.2 RegexGuard 矩阵（`RegexGuard.isSafe`）

| 模式 | 期望（本轮要求） | isSafe | 结论 |
| --- | --- | --- | --- |
| `(a+){2}` | 放行 | true | 通过 |
| `(a{2})+` | 放行 | true | 通过 |
| `(a+)?` | 放行 | true | 通过 |
| `(a?)+` | 拒绝 | false | 通过 |
| `(a{2,})+` | 拒绝 | false | 通过 |
| `(a{0,1})+` | 拒绝 | false | 通过 |
| `(a|aa)+` | 拒绝 | false | 通过 |
| `a+b` | 放行 | true | 通过 |
| `(ab)+` | 放行 | true | 通过 |

补充矩阵（未在本轮要求内，用于评估残留风险）：

| 模式 | isSafe | 说明 |
| --- | --- | --- |
| `(a|b)+` | false | 保守拒绝；`(a|b)+` 是常见安全模式，现被误伤（文档已说明任何无界重复的交替分支都拒绝） |
| `(ab|cd)+` | false | 保守拒绝（文档已说明） |
| `(a|b)` / `(a|b){2}` | true | 非无界重复/有界重复，放行 |
| `(a|b){2,}` | false | 无界重复 + 交替，拒绝 |
| `(?:ab)+` | true | 放行 |
| `((a|b)c)+` | false | 嵌套交替传播到外层，保守拒绝 |
| `(a+){2,}` | false | 无界外层重复，保守拒绝 |
| `(a+){2,3}` | true | 有界重复，放行 |
| `(a*)*` | false | 嵌套量词，拒绝 |
| `(a{1,2})+` | **true** | **残留漏判**：变长有界重复嵌套在无界组内，仍可灾难性回溯 |
| `(a{0,2})+` | **true** | 同上 |
| `(a{2,3})+` | **true** | 同上 |
| `(a{0,3})+` | **true** | 同上 |

- 1MB + `(a+)+$`：`RegexGuardTest.oneMegabyteTextWithNestedQuantifierReturnsImmediately` 通过（断言 <1s，实际远低于 1s）。
- **残留风险（中）**：`(a{1,2})+`、`(a{0,2})+` 等“变长有界重复 + 外层无界重复”不在 `hasRiskyRepetition()` 的判定内，4096 字符文本仍可能指数回溯；建议将 `{m,n}` 中 `m != n` 且 n>1 的情况也视为风险，或引入 RE2/J/超时保护。
- **兼容性（低）**：`(a|b)+`、`(ab|cd)+`、`((a|b)c)+` 等安全模式被保守拒绝；这是新文档明确接受的权衡，但会误伤常见脚本，建议在教程中给出改写示例（如 `[ab]+`、`(?:ab|cd)+` 也会被拒绝，需拆分为多个选择器）。

#### 10.8.3 编辑器超限对话框与 Activity 生命周期

- **修复确认**：`EditorActivity.onCreate()` 先 `setContentView(binding.root)`，再用 `binding.root.post { if (!isFinishing && !isDestroyed) showOpenError(...) }`（`EditorActivity.kt:143-150`）；`openErrorDialog` 字段持有、`onDestroy` 中 dismiss（`EditorActivity.kt:244-248`）。打开超限脚本的两个用例 2/2 通过，完整两轮 logcat 中 `RootViewWithoutFocusException` 与 `WindowLeaked` 均为 **0**，焦点 flake 已消除。
- **阻塞残留（高）：超大文本在 Activity stop 时触发 `TransactionTooLargeException`。**
  - 复现（临时探针，已删除）：打开编辑器，将 `scriptEditor` 设为 `"a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)`，执行 `ActivityScenario.moveToState(Lifecycle.State.CREATED)`（模拟 Home/切后台导致的 stop），系统在 `ActivityClientController.activityStopped` 抛：
    `android.os.TransactionTooLargeException: data parcel size 529680 bytes`
    （Bundle 中 `android:viewHierarchyState`/`android:views` 包含 512 KB+ 的 EditText 文本）。
  - 现有 `oversizedSaveKeepsDirtyAndDoesNotWrite` 断言本身通过（Snackbar、仓库内容不变、dirty），但在一次与其他编辑器用例连续执行时曾以同一异常在 Activity 收尾阶段失败；单独/后续组合又通过，说明是“stop 时保存视图状态”的时序性崩溃，不是断言问题。
  - 根因：保存守卫（`EditorActivity.kt:732-745`）只阻止写入仓库；EditText 仍持有 >512 KB 文本，`onSaveInstanceState` 的 `super` 会把 view hierarchy 放进 saved state（`EditorActivity.kt:234`），`activityStopped` 的 Binder 事务超过限制。用户粘贴大文本后按 Home/切后台/旋转即可触发，属于**真实崩溃路径**，不是测试专有。
  - 建议：给 `scriptEditor` 增加按 UTF-8 字节限制的自定义 `InputFilter`（或在粘贴时截断/提示）；或禁用/裁剪 `saveEnabled` 并在 `onSaveInstanceState` 中不保存超大文本；仅靠保存守卫不足。
  - 影响评估：需要用户粘贴 >512 KB 内容才触发，概率低但后果是应用崩溃/系统异常；在“限制 512 KB”的目标下应视为阻塞修复项。

#### 10.8.4 导入阶段与统一限制

- **导入 20,000 行拒绝**：`MainActivity.importFromUri()` 在 `BoundedTextReader.read()` 后、`repository.create()` 前调用 `ScriptLimits.scriptError(content)`，超限直接 `error(...)`（`MainActivity.kt:965-973`）；不会创建脚本。`BoundedTextReader.MAX_BYTES` 已统一为 `ScriptLimits.MAX_SCRIPT_BYTES`（512 KB），错误文案也改为动态 KB（`BoundedTextReader.kt:7,34-35`）。`ScriptLimitsTest` 覆盖 20k 行/512 KB/多字节边界；**SAF 端到端导入未做设备测试**（见 10.8.5）。
- **统一限制**：`ScriptLimits.MAX_SCRIPT_BYTES=512*1024`、`MAX_SCRIPT_LINES=20_000`、`scriptError()` 同时检查字节与行数；编辑器打开（`:143`）、保存（`:734`）、运行（`:662`）、首页运行（`MainActivity.kt:779`）、导入（`:970`）、`ScriptService.start`（`ScriptService.kt:34`）全部使用同一函数。
- **正常脚本不受影响**：JVM 165/165 与仪器化 75/75 中，现有编辑器/保存/运行/备份/UI 用例全部通过；`ScriptLimitsTest` 验证恰好 512 KB、20,000 行边界通过。
- **保存行为**：`oversizedSaveKeepsDirtyAndDoesNotWrite` 验证 Snackbar「保存失败：脚本超过 512 KB」、仓库内容不变、副标题「未保存的修改」、保存菜单仍启用；该用例在最终两轮完整运行中通过。
- **残留**：编辑器仍允许输入/粘贴超过 512 KB（见 10.8.3 的 stop 崩溃）；保存守卫本身正确，但输入层未限制。

#### 10.8.5 结论与阻塞判断

- **已确认修复**：RegexGuard 本轮要求的 9 个模式全部符合预期，1MB + `(a+)+$` 仍快速返回；编辑器错误对话框不再产生 `RootViewWithoutFocusException`/`WindowLeaked`；导入在创建前执行 20,000 行/512 KB 校验；保存守卫保持 dirty 且不写入；正常脚本全部通过。
- **仍存在 1 个阻塞问题**：编辑器内 >512 KB 文本在 Activity stop（Home/切后台/旋转）时触发 `TransactionTooLargeException`（10.8.3）。建议按 InputFilter/save-state 方案修复后重新跑两轮完整仪器化。
- **非阻塞残留**：RegexGuard 对 `(a{1,2})+` 等变长有界重复仍漏判（中）；`(a|b)+` 等安全交替模式被保守拒绝（低，文档已声明）；SAF 导入端到端、BootReceiver 真实重启、CI tag 构建仍未设备/CI 验证。
- **是否建议关闭验收/上线**：**暂不建议关闭**，直到 10.8.3 的崩溃路径修复；其余修复项可视为通过。

### 10.9 高危修复复核（第五轮，2026-10-21）

复核范围：`TransactionTooLargeException` 修复、InputFilter/保存守卫、RegexGuard 残留、128 KB + 20,000 行统一限制。只修改测试代码；临时探针已删除；未 commit/tag/push。

#### 10.9.1 结果汇总

| 项 | 结果 |
| --- | --- |
| JVM 单元测试 | **173/173 通过**（0 失败/0 跳过） |
| 仪器化测试（API 35） | **77/77 通过，连续两轮**（`run-emulator-tests.sh`） |
| Lint | 0 errors，1 warning（既有 `core-ktx` 提示） |
| assembleDebug | 成功，`MicroAuto-0.9.0.apk` |
| 完整两轮 logcat | `TransactionTooLargeException` **0**、`RootViewWithoutFocusException` **0**、`WindowLeaked` **0** |
| 定向编辑器用例 | 大文本 stop/resume、正常文本 recreate、超限保存、超限打开 **5/5 通过** |

#### 10.9.2 `TransactionTooLargeException` 修复

- `EditorActivity.onSaveInstanceState()` 在文本超过 `VIEW_STATE_SAFE_CHARS=64_000` 时暂停 undo 监听、临时 `setText("")`、调用 `super` 保存视图状态，再在 `finally` 中恢复原文本/选区并 `resume`；同时写入 `STATE_TEXT_OMITTED`。`lineNumbers`/`logText` 设置 `freezesText=false`（`EditorActivity.kt:242-270`、`:82`、`:152-153`）。
- 恢复时若 `STATE_TEXT_OMITTED`，从仓库重新读取内容并恢复（`EditorActivity.kt:272-285`）；大文本不进入 view state，避免 `activityStopped` 的 Binder 事务超限。
- 验证：
  - `largeProgrammaticTextDoesNotCrashOnStop`：程序化写入 `MAX_SCRIPT_BYTES + 1` 字符（绕过 InputFilter），`moveToState(CREATED)` → `RESUMED`，文本长度保持，不再抛 `TransactionTooLargeException`；本次复核该用例通过。
  - `normalTextSurvivesRecreate`：普通文本 `recreate()` 后内容仍是 `log('rotate');`，原有行为未回退。
  - 完整两轮 logcat 中 `TransactionTooLargeException` 为 **0**；`RootViewWithoutFocusException`/`WindowLeaked` 也为 **0**，焦点/窗口问题保持修复。
- **说明（非缺陷，已写入 AGENTS）**：超过 64,000 字符的**未保存**内容不会随旋转保留，旋转/重建后回到最近保存版本；正常大小文本不受影响。

#### 10.9.3 InputFilter 与保存守卫

- `ScriptInputGuard`（128 KB 字节、20,000 行）接入 `InputFilter`：普通输入放行、超限插入/替换拒绝并 Snackbar 提示、已在超限文本上的纯删除放行（`ScriptInputGuardTest` 7/7；`EditorActivity.kt:513-537`）。
- 保存守卫仍有效：`oversizedSaveKeepsDirtyAndDoesNotWrite` 验证「保存失败：脚本超过 128 KB」、仓库内容不变、副标题「未保存的修改」、保存菜单启用；该用例在完整两轮中通过。
- **残留（中，数据丢失）：拒绝“选中文本 + 超大粘贴”时会删除选区。** 临时探针：编辑器内容 `abcdef`，选中 `cd`（2..4），用 `MAX_SCRIPT_BYTES + 1` 的文本替换，实际结果 `abef`（期望 `abcdef`）。根因：InputFilter 在拒绝时返回 `""`（`EditorActivity.kt:532-533`），Android 将其解释为“用空串替换选区”，于是选区被删除；Snackbar 虽提示，但原文本已丢失。
  - 建议：拒绝时返回 `dest.subSequence(dstart, dend)`（插入时为空串、替换时保留原选区），或在 Activity 层禁止在超大拒绝时修改编辑器；并补一条“选中文本被拒绝粘贴后保持不变”的仪器化用例。
  - 影响：不崩溃，但会静默丢失用户选中的内容；属中危数据丢失残留，建议发布前修复。

#### 10.9.4 RegexGuard 矩阵

本轮要求（`RegexGuard.isSafe`，临时探针结果）：

| 模式 | 期望 | isSafe | 结果 |
| --- | --- | --- | --- |
| `(a{1,2})+` | 拒绝 | false | 通过 |
| `(a{0,2})+` | 拒绝 | false | 通过 |
| `(a{2,3})+` | 拒绝 | false | 通过 |
| `(a{0,1})+` | 拒绝 | false | 通过 |
| `(a{2})+` | 放行 | true | 通过 |
| `(a{2,2})+` | 放行 | true | 通过 |
| `a+b` | 放行 | true | 通过 |
| `(ab)+` | 放行 | true | 通过 |

补充矩阵：

| 模式 | isSafe | 说明 |
| --- | --- | --- |
| `(a+){2}` / `(a+)?` | true | 有界/可选外层，放行 |
| `(a?)+` / `(a{2,})+` / `(a|aa)+` | false | 拒绝 |
| `(a{2,4})+` | false | 变长有界重复嵌套在无界组内，拒绝 |
| `(a{2,4})` / `(a{2,3}){2}` / `(a+){2,3}` | true | 无外层无界重复或有界外层，放行 |
| `(a|b)+` / `(ab|cd)+` / `((a|b)c)+` | false | 保守拒绝；`(a|b)+` 等安全模式会被误伤（文档已声明） |
| `(a|b)` / `(a|b){2}` / `(?:ab)+` | true | 非无界交替/有界重复，放行 |

- 1MB + `(a+)+$`：`RegexGuardTest.oneMegabyteTextWithNestedQuantifierReturnsImmediately` 通过（断言 <1s，实际远低于 1s）。
- 残留：正则防护仍是启发式；`(a|b)+` 等常见安全交替被保守拒绝（低，文档已说明）；未引入 RE2/J，4096 字符上限内仍以静态规则为主。

#### 10.9.5 统一限制（128 KB + 20,000 行）

- `ScriptLimits`：`MAX_SCRIPT_BYTES=128*1024`、`MAX_SCRIPT_LINES=20_000`、`scriptError()` 同时检查字节与行数；错误文案为「脚本超过 128 KB（当前约 N KB）」/「脚本超过 20,000 行」。
- 接入点一致：导入（`MainActivity.kt:965-973`，先校验后 `repository.create`）、编辑器打开（`:143-148`）、保存（`:824-834`）、运行（`:751-761`）、首页运行（`MainActivity.kt:779`）、`ScriptService.start`（`ScriptService.kt:34`）。`BoundedTextReader.MAX_BYTES` 直接引用 `ScriptLimits.MAX_SCRIPT_BYTES`，错误文案动态为 128 KB（`BoundedTextReader.kt:7,34-35`）。
- 正常脚本不受影响：JVM 173/173、仪器化 77/77 中现有编辑/保存/运行/备份/UI 用例全部通过；`ScriptLimitsTest`/`ScriptInputGuardTest` 覆盖 128 KB、20,000 行、精确边界、多字节 UTF-8、纯删除。
- 导入 20,000 行拒绝为**代码审查 + `ScriptLimits` 单测**确认（`importFromUri` 在 `create` 前调用 `scriptError`）；SAF 端到端导入未做设备测试。

#### 10.9.6 结论与阻塞判断

- **高危崩溃已修复**：`TransactionTooLargeException` 的确定性路径（大文本 + `moveToState(CREATED)`/恢复）通过；正常文本 recreate 保留；完整两轮 logcat 三类异常均为 0。
- **RegexGuard 本轮矩阵全部通过**；变长有界重复（`(a{1,2})+` 等）已被拒绝；1MB `(a+)+$` 仍快速返回；剩余为保守误伤 `(a|b)+` 等（低，已文档化）。
- **统一限制与保存守卫通过**：128 KB + 20,000 行在各入口一致，超限保存保持 dirty 且不写入；正常脚本不受影响。
- **仍建议发布前修复 1 个中危残留**：InputFilter 拒绝“选中文本 + 超限粘贴”时删除选区（`abcdef` → `abef`，`EditorActivity.kt:532-533`），属静默数据丢失，不崩溃但应修复；其余无高危/崩溃阻塞。
- 未覆盖：SAF 导入端到端、BootReceiver 真实重启/改时间、CI tag 构建（沿用前文）。

### 附：第三轮测试轮次记录

| 阶段 | JVM | 仪器化 | 说明 |
| --- | --- | --- | --- |
| 基线（开发者自测） | 154 | 70 | `testDebugUnitTest + lintDebug + assembleDebug` 通过 |
| 加入新测试后首轮完整 | 165/165 | 74/74 | `run-emulator-tests.sh`；此轮通过 |
| 第二轮完整（flake） | — | 74，1 失败 | `characterOversized...` 因基础窗口无焦点抛 `RootViewWithoutFocusException`（测试侧 `inRoot(isDialog())` 修正） |
| 定向稳定性复跑 | — | 2/2 × 3 次 | 修正后连续 3 次通过 |
| 修复后完整第 A 轮 | — | 74/74 | `run-emulator-tests.sh` |
| 修复后完整第 B 轮 | — | 74/74 | `run-emulator-tests.sh`，未见 flaky |
| SEC-010 dry-run | — | — | 无 keystore exit 1；debug keystore exit 0；assembleDebug exit 0 |
| 最终 lint/assemble | — | — | 0 errors / 1 warning；`MicroAuto-0.9.0.apk` |
