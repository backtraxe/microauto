# MicroAuto 下一阶段功能与 UI 需求

> 文档版本：2026-10-21  
> 代码基线：`main` @ `81b0555`（v0.7.0）  
> 适用范围：经典 View + ViewBinding + Material 3，不引入 Compose、不引入新依赖  
> 目标：在不改动脚本运行时架构的前提下，补齐「第一次能用起来」「运行中看得见」「失败后找得到」「写脚本更省事」四条主线。

---

## 1. 现状摘要

### 1.1 已经具备的能力

- 首页：脚本/文件夹列表、名称搜索、空状态、底部悬浮导航（脚本 / 新建 / 教程）、右上角菜单（无障碍、精确闹钟、省电、定时、导入、备份、恢复、内置示例）。
- 编辑页：编辑 / 日志 / API 三个 tab；行号、JS 语法高亮、撤销重做、查找替换、符号栏开关；底部运行 / 停止 / 看界面 / 定时；定时状态圆点。
- 运行：前台服务、通知栏停止、成功/失败通知、10 分钟超时、单脚本串行。
- 日志：`latest.log` 最近 20,000 字符，编辑页日志 tab 展示，文本可选中。
- 版本：发布快照（可写备注）、版本列表、恢复、删除；恢复前自动快照。
- 定时：每个脚本独立 `HH:mm`，精确闹钟不可用时降级，锁屏/息屏 1 小时内每 2 分钟重试。
- API 文档：`ApiDocs.kt` 当前为 6 组、35 条，卡片式展示签名、说明、示例（早期简介中的「5 组 33 条」是旧口径，以代码为准）。
- 设计语言：蓝色强调色（浅色 `#1D4ED8` / 深色 `#60A5FA`）、8dp 圆角、1dp 描边卡片、无阴影、12dp 页面边距、系统无衬线 + JetBrains Mono、中文界面、48dp 图标按钮。

### 1.2 现状观察与用户代价

| 场景 | 现状 | 用户代价 |
|---|---|---|
| 第一次安装 | 无障碍入口在右上角菜单里；教程 tab 是静态文字；首页没有环境状态 | 用户先点「运行」，被 Toast 和系统设置打断，不知道还要回来；首五分钟流失 |
| 运行脚本 | 首页要「⋮ → 运行」两次点击；运行中首页没有任何反馈；结果只在通知栏；点通知回首页而不是日志 | 核心动作被藏起来；失败后要回忆是哪个脚本、再找日志 |
| 日志 | 全局「最近一次」日志，标题不写脚本名；长日志每次追加都强制滚到底；JS 错误只有文本，没有行号跳转 | 看错脚本的日志、dump 读到一半被拽走、改错位置 |
| 编辑 | 标题单行截断（截图中为「示例：常用工…」）；保存图标始终可点；重命名只能回首页菜单 | 不确定是否已保存；重命名要退出编辑页，增加丢改动风险 |
| API | 35 条卡片平铺；没有搜索；示例不能直接插入 | 翻找、手抄、来回切 tab |
| 版本 | 列表只有版本号、备注、时间；点击无反应；空状态没有操作；恢复前看不到内容 | 只能盲恢复；空状态要退出页面再走菜单 |
| 定时 | 底部 sheet 只填时间和开关；精确闹钟缺失时仍提示「已设置」 | 定时静默延迟，用户以为 App 坏了 |
| 无障碍 | 主要按钮 48dp；但多个「更多操作」同名；行号会被 TalkBack 读到；大字号下列表元信息与底部四按钮会挤压 | TalkBack 用户分不清操作对象；大字号用户操作困难 |

### 1.3 本次不重复建设

以下能力已存在，本文档只在必要处做「前置、反馈、检索」类增强，不重新设计：

- API 文档的分组与条目内容（只增加搜索与插入）。
- 符号栏及其开关。
- 编辑页底部「定时」按钮、每个脚本独立定时、定时重试策略。
- 暗色主题、配色 token、边到边、悬浮底部导航。
- 备份/恢复、导入/导出、文件夹、搜索、撤销重做、查找替换。
- 版本发布、恢复、删除本身（只增加预览与空状态入口）。
- 不引入 OCR、找图、悬浮窗、可视化选择器等新运行时能力。

---

## 2. 需求汇总

| ID | 需求 | 优先级 | 覆盖维度 | 改动规模 | 依赖 |
|---|---|---|---|---|---|
| R1 | 运行环境引导与定时健康提示 | P0 | 空状态/引导、错误预防 | 中 | 无 |
| R2 | 首页一键运行 | P0 | 核心流程效率 | 小 | R1 的拦截对话框 |
| R3 | 全局运行状态条与结果闭环 | P1 | 加载/成功/错误反馈、信息架构 | 中 | R2、`RunState` |
| R4 | 日志归属、错误行定位与阅读体验 | P1 | 错误反馈、开发者工具 | 中 | R3、`RunLog` |
| R5 | 编辑器保存状态与重命名 | P1 | 信息架构、信任感 | 小 | 无 |
| R6 | API 速查搜索与插入 | P1 | 开发者工具、核心流程效率 | 中 | 无 |
| R7 | 版本预览与空状态发布 | P2 | 开发者工具、空状态 | 中 | 无 |
| R8 | 可访问性与大字号适配 | P2 | 可访问性 | 小 | R2、R3 完成后补语义 |

优先级定义：

- **P0**：不做会直接阻断首次使用或核心动作，下一版必须完成。
- **P1**：高频路径上的明显摩擦，下一到两个版本完成。
- **P2**：质量与体验补强，可独立排期。

---

## 3. 详细需求

### R1 运行环境引导与定时健康提示（P0，中）

#### 用户问题

MicroAuto 的一切能力都依赖无障碍服务，但当前只有「右上角菜单 → 无障碍：未开启」这一处状态。新用户看不到它；编辑页点「运行」只弹 Toast 并跳系统设置，返回后也没有确认。定时任务还依赖精确闹钟和电池白名单，但定时 sheet 在权限缺失时仍然提示「已设置」，用户会把延迟归因于 App 不稳定。

#### 方案

**1）首页环境提示条**

位置：`activity_main.xml` 的 `scriptsPage` 内、搜索框上方，仅脚本 tab 显示。

结构：

```
MaterialCardView id=setupBanner
  style=Widget.MicroAuto.Card（8dp 圆角、1dp 描边、0 阴影）
  marginStart/End=12dp, marginTop=8dp
  LinearLayout horizontal, padding=12dp, gravity=center_vertical
    ImageView 20dp（ic_warning / ic_clock）
    LinearLayout vertical, weight=1, marginStart=12dp
      TextView id=setupTitle  textAppearance=TitleSmall
      TextView id=setupHint   textAppearance=BodySmall, colorOnSurfaceVariant, maxLines=2
    MaterialButton id=setupAction style=TextButton, 48dp minHeight
```

可见性规则（同一时刻只显示一条，优先级从上到下）：

| 条件 | 变体 | 标题 | 说明 | 按钮 |
|---|---|---|---|---|
| 无障碍未开启 | 阻断 | `无障碍服务未开启` | `开启后才能查找控件、点击和运行脚本` | `去开启` |
| 无障碍已开启，且至少 1 个启用定时，精确闹钟不可用（Android 12+） | 提醒 | `定时可能延迟` | `精确闹钟未授权，定时任务可能晚触发` | `去授权` |
| 以上都不满足 | 隐藏 | — | — | — |

- 电池优化、通知权限不在首页提示条出现，避免连续打扰；放进「运行环境」面板。
- 点提示条主体打开 R1 的「运行环境」面板；点右侧按钮直接进对应系统设置。
- `onResume` 重新计算；状态变化用 160ms alpha 动画显隐，和现有 `showPage` 一致，不做高度动画。
- 返回 App 后，如果无障碍从关变开，用 Snackbar `无障碍服务已开启`，锚定在底部导航上方（`setAnchorView(binding.navCard)`），不弹对话框。

**2）运行环境面板**

入口：把右上角菜单里现有的「无障碍」「精确闹钟」「省电」三项合并为一项 **「运行环境」**，放在菜单第一项；保留「定时」项。菜单结构从 9 项降为 7 项，降低认知负担。

面板：`BottomSheetDialog` + 新布局 `sheet_environment.xml`。

- 标题：`运行环境`
- 说明：`脚本运行依赖无障碍服务；定时任务还依赖精确闹钟和后台运行权限。`
- 四行，每行最小高度 56dp，左侧标题 14sp，第二行状态 12sp，右侧 TextButton 48dp：

| 行 | 状态来源 | 状态文案 | 操作 |
|---|---|---|---|
| 无障碍服务（必须） | `AccessibilityUtils.isEnabled` | `已开启` / `未开启` | `去开启` → `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| 通知权限（建议） | `POST_NOTIFICATIONS`（Android 13+） | `已允许` / `未允许` / `无需设置` | `去允许` → 运行时权限；旧系统禁用 |
| 精确闹钟（定时需要） | `AlarmManager.canScheduleExactAlarms()` | `可用` / `未授权` / `无需授权` | `去授权` → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| 电池优化（定时建议） | `PowerManager.isIgnoringBatteryOptimizations` | `已允许` / `未允许` | `去设置` → 现有 `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |

- 状态颜色：必须项缺失用 `colorError`；建议项缺失用 `colorOnSurfaceVariant`；正常用 `colorPrimary`。不能只靠颜色，状态文案必须完整。
- 点操作前先 `dismiss()` 面板，再 `startActivity`；返回后首页提示条自动刷新。
- 面板每行可整体点击（除按钮外）打开对应设置，按钮只是更明确的 affordance。

**3）编辑页运行拦截**

`EditorActivity.btnRun` 在无障碍未开启时，不再只 Toast：

- 对话框标题：`需要开启无障碍服务`
- 正文：`MicroAuto 通过无障碍服务读取界面和执行点击。开启后返回即可运行当前脚本。`
- 按钮：`去开启`（主）/ `取消`
- 从设置返回后，如果已开启，Snackbar `无障碍服务已开启` + 操作 `运行`；点「运行」再走正常 `btnRun` 流程，不自动执行。
- 无障碍已开启但 `AutomationService.instance == null` 时，仍按现有逻辑等待 5 秒并在日志中说明，不新增阻塞。

**4）定时 sheet 的一致性提示**

在 `sheet_schedule.xml` 的开关下方增加一行提示（仅当开关打开且精确闹钟不可用时显示）：

- 文案：`精确闹钟未授权，定时可能延迟`
- 右侧 TextButton：`去授权`
- 保存成功 Toast 改为：`已设置每天 08:00；精确闹钟未授权，可能延迟`
- 精确闹钟可用时，该行 `GONE`；Android 12 以下不显示。

#### 文案汇总

| 位置 | 文案 |
|---|---|
| 提示条 A 标题/说明/按钮 | `无障碍服务未开启` / `开启后才能查找控件、点击和运行脚本` / `去开启` |
| 提示条 B 标题/说明/按钮 | `定时可能延迟` / `精确闹钟未授权，定时任务可能晚触发` / `去授权` |
| 返回 Snackbar | `无障碍服务已开启` |
| 环境面板标题/说明 | `运行环境` / `脚本运行依赖无障碍服务；定时任务还依赖精确闹钟和后台运行权限。` |
| 编辑页对话框 | `需要开启无障碍服务` / `MicroAuto 通过无障碍服务读取界面和执行点击。开启后返回即可运行当前脚本。` / `去开启`、`取消` |
| 定时提示 | `精确闹钟未授权，定时可能延迟` / `去授权` |

#### 验收标准

1. 全新安装、无障碍未开启时，进入首页 300ms 内看到提示条；TalkBack 能依次读到标题、说明和「去开启」按钮，卡片主体也可点击进入「运行环境」面板，不出现焦点被容器吞掉的情况。
2. 点「去开启」进入系统无障碍设置；开启后返回，提示条消失，底部导航上方出现 `无障碍服务已开启` Snackbar。
3. 无障碍已开启时，首页不再出现提示条；已启用定时且精确闹钟缺失时，改为显示 `定时可能延迟`，授权后返回消失。
4. 右上角菜单存在「运行环境」；面板四行状态与系统实际状态一致；四个操作分别进入正确设置页；旧系统上「精确闹钟」显示 `无需授权`。
5. 编辑页无障碍未开启点「运行」，出现解释对话框而不是只弹 Toast；从设置返回后可通过 Snackbar 的「运行」直接重试。
6. 定时开关打开、精确闹钟缺失时，sheet 内出现提示；保存成功文案包含「可能延迟」；授权后提示消失。
7. 2 倍字体下提示条最多两行说明，按钮仍为 48dp，不覆盖搜索框或列表。

#### 改动范围

- **中**。
- 布局：`activity_main.xml`、新增 `sheet_environment.xml`、`sheet_schedule.xml`。
- 代码：`MainActivity.kt`（提示条、面板、菜单）、`EditorActivity.kt`（运行拦截）、`menu_main.xml`、`strings.xml`。
- 新增矢量图：`ic_warning.xml`、`ic_clock.xml`（或复用 `ic_book` 的时钟语义不合适，建议新增）。
- 测试：更新 `MainActivityTest.showsToolbarAndNav` 中「无障碍」菜单断言为「运行环境」；环境面板行状态与点击可用 Espresso 覆盖；系统设置跳转与无障碍开关需手工验收。

---

### R2 首页一键运行（P0，小）

#### 用户问题

运行是产品最核心的动作，但首页每行只有「⋮ → 运行」。用户为了跑一次脚本至少要 2 次点击，还要先打开菜单寻找，或者进入编辑页再点底部「运行」。运行开始后首页也没有任何行内状态，用户不确定是否真的启动了。

#### 方案

**1）行内运行按钮**

在 `item_script.xml` 的 `moreButton` 左侧增加 48dp 运行控制：

```
FrameLayout id=runAction  48dp x 48dp
  MaterialButton id=btnRunScript
    style=Widget.Material3.Button.IconButton
    icon=ic_play_arrow, iconTint=?attr/colorPrimary, iconSize=20dp
    48dp x 48dp
  CircularProgressIndicator id=runSpinner
    16dp, indeterminate, visibility=gone, layout_gravity=center
```

- 默认显示播放图标；该脚本运行中时隐藏图标、显示 16dp 进度圈，按钮禁用。
- 运行按钮与「⋮」都保持 48dp，卡片右侧不再增加其他操作。
- 行点击仍然进入编辑页；运行按钮独立 `OnClickListener`，不触发父卡片点击。
- 新增 `ic_play_arrow.xml` 矢量图；不引入图标库。

**2）运行中状态**

- `RunState` 增加 `activeScriptId: StateFlow<String?>`（或在 R3 的 `snapshot` 中读取）。
- `ScriptService` 在真正接受运行请求后设置 active id；`ScriptRunner` 结束或 `stopRun()` 时清空。
- `LibraryAdapter` 增加 `onScriptRun: (ScriptMeta) -> Unit` 和 `runningScriptId: String?`：
  - `btnRunScript.contentDescription = "运行「${meta.name}」"`。
  - 运行中：`btnRunScript.isEnabled = false`，`runSpinner.isVisible = true`，`btnRunScript.contentDescription = "「${meta.name}」正在运行"`；`runAction` 只作布局容器，不设 contentDescription，避免吞掉按钮焦点。
  - 其他行：`btnRunScript.isEnabled = false`，contentDescription `已有脚本在运行`。
- `MainActivity` 用 `repeatOnLifecycle(STARTED)` 同时收集 `RunState.running` 和 `activeScriptId`，只对变化的两行 `notifyItemChanged`，不整表刷新。
- 保留「⋮ → 运行」菜单项，兼容已有操作习惯与测试；行内按钮是新的主路径。

**3）点击行为**

- 无障碍未开启：走 R1 对话框。
- 已有脚本运行：`Snackbar` `已有脚本在运行`，不启动第二个。
- 脚本内容读取失败：`Snackbar` `脚本不存在，可能已被删除`，并刷新列表。
- 正常启动：不弹 Toast，由 R3 状态条给出反馈。

#### 验收标准

1. 每个脚本行右侧有 48dp 播放按钮，contentDescription 包含脚本名；点击一次直接开始运行，不进入编辑页。
2. 行点击、运行按钮、「⋮」三个点击区域互不误触；2 倍字体下名称最多两行，三个控件不重叠。
3. 该脚本运行中，行内显示进度圈；其他脚本的运行按钮禁用；TalkBack 能读出「正在运行」或「已有脚本在运行」。
4. 无障碍未开启时，点行内运行出现 R1 对话框；返回并开启后可再次运行。
5. 运行结束、停止、超时后，行内进度圈在 300ms 内消失，按钮恢复可用。
6. 现有「⋮ → 运行」仍然可用，行为与行内按钮一致。

#### 改动范围

- **小**。
- 布局：`item_script.xml`。
- 代码：`LibraryAdapter.kt`、`MainActivity.kt`、`RunState.kt`、`ScriptService.kt`。
- 新增矢量图：`ic_play_arrow.xml`。
- 测试：`MainActivityTest` 增加行内运行控件的 contentDescription/点击断言；运行态可在测试中直接设置 `RunState` 后断言进度圈可见。

---

### R3 全局运行状态条与结果闭环（P1，中）

#### 用户问题

脚本从首页启动后，首页没有「正在运行」「已运行 12 秒」或「运行失败」的可见反馈。用户只能等通知，或者切到编辑页看日志；通知点开回到首页，仍要自己找脚本。定时任务在后台跑完后，用户更容易错过结果。

#### 方案

**1）运行状态数据**

把 `RunState` 从单一布尔值扩展为快照，保留 `running` 兼容现有编辑页：

```kotlin
enum class RunPhase { STARTING, RUNNING, SUCCESS, FAILED, STOPPED }
enum class RunSource { MANUAL, SCHEDULE, DUMP }

data class RunSnapshot(
    val scriptId: String?,
    val scriptName: String,
    val phase: RunPhase,
    val source: RunSource,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val errorLine: Int? = null,
    val errorMessage: String? = null
)
```

- `RunState.snapshot: StateFlow<RunSnapshot?>`；`RunState.running` 继续保留，由 `snapshot` 或现有 `_running` 同步维护。
- `ScriptService.start(...)` 增加 `source: RunSource = MANUAL`；`ScheduleReceiver` 传 `SCHEDULE`；编辑页「看界面」传 `DUMP`。
- `ScriptService` 接受请求后 `RunState.start(...)`；`finishRun` 调 `RunState.finish(success)`；`stopRun` 调 `RunState.stop()`。
- `MainActivity.runScript` / `EditorActivity` 启动服务失败时调 `RunState.failStart(message)`，避免状态条永远停在「启动中」。

**2）首页底部状态条**

把 `activity_main.xml` 底部改为一个纵向 `bottomStack`（`layout_gravity=bottom`，左右 12dp，bottom 12dp）：

```
LinearLayout bottomStack
  MaterialCardView id=runStatusCard   // 8dp 圆角、1dp 描边、0 阴影、surface
    LinearLayout horizontal, padding=12dp, minHeight=56dp
      ImageView/状态点 8dp 或 20dp icon
      LinearLayout vertical, weight=1
        TextView runStatusTitle  14sp titleSmall
        TextView runStatusMeta   12sp bodySmall, colorOnSurfaceVariant
      MaterialButton runStatusAction  TextButton 48dp
      MaterialButton runStatusClose   IconButton 48dp（仅结果态）
  MaterialCardView navCard（现有底部导航，移入 stack）
```

- `bottomStack` 承担原来 `navCard` 的左右 12dp、底部 12dp 外边距；`navCard` 自身的这些外边距移到 stack 上，避免双重间距。
- `runStatusCard` 默认 `GONE`；运行时 GONE→VISIBLE，`scriptList` 底部 padding 从 104dp 临时调到 176dp，教程页 `tutorialContent` 的底部留白同步从 120dp 调到 176dp，避免最后一行/最后一段被遮住；结束后恢复。
- 状态条位于悬浮导航上方，`停止` / `查看日志` 都在拇指区；默认在脚本与教程两个 tab 都可见（它挂在根布局底部），如果只想在脚本 tab 显示，可把 `bottomStack` 移入 `scriptsPage`。
- 颜色：运行中用 `colorPrimary` 圆点；成功用 `colorPrimary` + `ic_check`；失败用 `colorError` + `ic_warning`；停止用 `colorOnSurfaceVariant`。不新增绿色 token，成功/失败同时靠图标和文案区分，避免只靠颜色。

**3）状态矩阵**

| 阶段 | 标题 | 副标题 | 右侧操作 | 消失规则 |
|---|---|---|---|---|
| STARTING | `正在启动「脚本名」` | `准备中…` | `停止` | 进入 RUNNING 或失败 |
| RUNNING | `正在运行「脚本名」` | `已运行 00:12 · 手动运行` | `停止` | 运行结束/停止 |
| SUCCESS | `运行完成` | `「脚本名」· 用时 5.1 秒` | `查看日志` + 关闭 | 4 秒后自动隐藏 |
| FAILED | `运行失败` | `「脚本名」· 第 3 行 ReferenceError…` | `查看日志` + 关闭 | 保持到用户关闭或下次运行 |
| STOPPED | `已停止` | `「脚本名」· 用时 8 秒` | `查看日志` + 关闭 | 3 秒后自动隐藏 |

- 定时运行副标题加 `· 定时任务`；看界面（DUMP）不显示首页状态条，编辑页自己已有进度条和日志。
- 计时用 `Handler` 每 1 秒只更新 `runStatusMeta`，不要在每秒文本上设 live region，否则 TalkBack 会持续播报。只在阶段变化时 `announceForAccessibility`：`开始运行`、`运行完成`、`运行失败，第 N 行`、`已停止`。
- Activity 回到前台时：成功/停止结果在 30 秒内恢复显示；失败结果只要用户未关闭就恢复显示，避免漏掉错误。

**4）查看日志**

- `EditorActivity` 增加 `EXTRA_OPEN_LOG = true`：
  - 启动时如果该 extra 为真，`setupTabs` 后直接选中日志 tab。
  - 若脚本已被删除，`MainActivity` 捕获不到（Editor 会 finish）；由状态条点击前检查 `repository.find(scriptId)`，为空时 Snackbar `脚本已删除` 并刷新列表。
- `查看日志` 打开 `EditorActivity` 并带 `EXTRA_SCRIPT_ID` + `EXTRA_OPEN_LOG`；不使用 `singleTop`，避免影响已打开的编辑页状态。

#### 验收标准

1. 从首页行内按钮启动后，状态条在 300ms 内出现，显示脚本名和计时；计时每秒更新，页面滚动/旋转不丢失。
2. 点「停止」后 1 秒内变为 `已停止`，前台服务通知消失，行内进度圈消失。
3. 成功时显示 `运行完成` 和用时，4 秒后自动隐藏；失败时显示 `运行失败` 和错误行摘要，保持到用户关闭；失败状态不会被自动隐藏。
4. 点「查看日志」进入对应脚本的编辑页并选中日志 tab；脚本已删除时给出明确提示。
5. 状态条不遮挡底部导航或最后一行脚本；列表底部 padding 在显示/隐藏时正确恢复。
6. TalkBack 只在开始/成功/失败/停止时各播报一次；不会每秒播报计时。
7. 定时任务在 App 退到后台后完成：成功/停止结果在 30 秒内回到 App 仍能看到，超过 30 秒不显示；失败结果未关闭时仍显示。

#### 改动范围

- **中**。
- 代码：`RunState.kt`、`ScriptService.kt`、`ScriptRunner.kt`、`MainActivity.kt`、`EditorActivity.kt`、`ScheduleReceiver.kt`。
- 布局：`activity_main.xml`、新增 `view_run_status.xml`（或直接内联）。
- 新增矢量图：`ic_stop.xml`；`ic_check.xml` 已有，可直接复用。
- 测试：`RunState` 状态迁移 JVM 测试；`MainActivityTest` 通过直接设置 `RunState` 断言状态条各阶段；旋转/后台恢复手工验收。

---

### R4 日志归属、错误行定位与阅读体验（P1，中）

#### 用户问题

日志是全局「最近一次」，编辑页却把它当成当前脚本的日志展示；教程甚至明确提醒「可能来自另一个脚本」。长 `dump()` 输出时，每次 `RunLog.append` 都会 `fullScroll(FOCUS_DOWN)`，用户向上翻看会被不断拽回底部。JS 报错只有 `[脚本错误] ...` 文本，用户要自己数行号。日志没有复制/清空入口。

#### 方案

**1）日志元信息**

在 `RunLog` 中增加 `meta: StateFlow<RunLogMeta?>`，并持久化到 `SharedPreferences("run_log")`，避免进程重启后只剩一段无主日志：

```kotlin
data class RunLogMeta(
    val scriptId: String?,
    val scriptName: String,
    val source: RunSource,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: RunPhase,
    val errorLine: Int?,
    val errorMessage: String?
)
```

- `ScriptService` 启动时先 `RunLog.clear()` 再 `RunLog.begin(scriptId, scriptName, source)`，结束 `RunLog.complete(success/stopped)`；顺序反了会把刚写入的 meta 清掉。
- `ScriptRunner.handleError` 遇到 `RhinoException` 时：
  - `line = error.lineNumber()`；
  - `source = error.sourceName()`；
  - 只有 `source == "script.js"` 才记录 `errorLine`；`prelude.js` 的错误显示「内置 API 错误」，不提供跳转。
  - `errorMessage = error.details()`。
- 旧版本遗留的 `latest.log` 没有 meta：日志 tab 显示「上次运行日志」+ 「来源未知」，不冒充当前脚本。

**2）日志头部与归属提示**

`activity_editor.xml` 的 `logPage` 内，卡片顶部由单行标题改为：

```
LinearLayout vertical
  LinearLayout horizontal
    TextView logTitle `运行日志`  TitleSmall
    TextView logStatusChip `成功` / `失败` / `运行中` / `已停止`  12sp，带 4dp 圆角淡色背景
  TextView logMeta `「示例：节点树」· 15:57 · 5.1 秒`  BodySmall
  MaterialCardView logForeignBanner（仅当日志不属于当前脚本）
    TextView `这是「B」的运行日志，不是当前脚本`
    MaterialButton `运行当前脚本`
  MaterialCardView errorCard（仅当 errorLine != null）
    TextView `第 3 行 · ReferenceError: foo is not defined`
    MaterialButton `跳到第 3 行`
  FrameLayout / ScrollView（现有日志）
```

- 当 `meta.scriptId != 当前 scriptId` 且不为空时，显示 `logForeignBanner`；点「运行当前脚本」调用当前编辑页的 `btnRun` 逻辑（会先保存）。
- 状态 chip 颜色：成功/运行中用 `colorPrimaryContainer`，失败用 `colorErrorContainer`，停止用 `surfaceContainerHighest`；文字始终完整，不只靠颜色。

**3）错误行跳转**

- 新增 `core/TextLines.kt`：`fun offsetOfLine(text: String, line: Int): Int?`，处理空行、越界、`\r\n`；补 JVM 测试。
- `EditorActivity.jumpToLine(line)`：
  - 切到 `TAB_CODE`；
  - 计算行首 offset，`scriptEditor.setSelection(offset)`；
  - `requestFocus()`；`post { bringPointIntoView(offset) }`；
  - 不自动弹键盘，避免遮挡代码；用户点击编辑器再输入。
- 错误卡整行可点，点击等同「跳到第 N 行」；48dp 高。

**4）日志阅读**

- `logScroll` 增加滚动监听，记录 `logAtBottom = 距离底部 <= 48dp`。
- `RunLog.text.collect` 时先读 `logAtBottom`，再设置文本；只有之前在底部才自动滚到底。
- 不在底部时，日志卡片右下角显示 48dp `回到底部` TextButton（或带向下箭头的 icon button）；点击滚到底并恢复自动跟随。
- 工具行：`复制`、`清空`，均为 48dp TextButton。
  - 复制：`ClipboardManager` 写入全文，Snackbar `日志已复制`。
  - 清空：先确认 `清空日志？`，确认后 `RunLog.clear()`，同时清空 meta；运行中禁用「清空」。
- 日志文本保持可选中；不对整段日志设 live region，避免刷屏。

**5）通知深链**

- `RunNotifications.notifyResult` 增加 `scriptId` 参数：
  - 标题：`MicroAuto · 「脚本名」运行完成` / `运行失败`。
  - 正文：优先 `errorMessage`，否则最后一行非空日志。
  - `PendingIntent` 指向 `EditorActivity`，带 `EXTRA_SCRIPT_ID` + `EXTRA_OPEN_LOG`；`scriptId == null` 或脚本不存在时回退 `MainActivity`。
- `notifySkipped` / `notifyFailure` 保持现有行为，文案中带上脚本名即可。

#### 验收标准

1. 编辑脚本 A 时运行脚本 B，A 的日志 tab 显示「这是「B」的运行日志」；点「运行当前脚本」会先保存 A 再运行。
2. 脚本第 3 行写 `foo();` 运行失败，错误卡显示 `第 3 行 · ReferenceError...`；点「跳到第 3 行」切到编辑 tab，光标落在第 3 行行首。
3. `prelude.js` 内部错误不显示跳转按钮，显示「内置 API 错误」。
4. 长 `dump()` 追加时，用户向上滚动后不再被拽到底部；右下角出现「回到底部」，点击后恢复自动跟随；滚回底部后按钮消失。
5. 「复制」把完整日志写入剪贴板；「清空」确认后日志区显示空状态，运行中「清空」不可用。
6. 失败通知标题包含脚本名和「运行失败」，正文包含错误摘要；点击进入对应脚本日志 tab；脚本已删除时回到首页。
7. 日志 tab 在 2 倍字体下头部不重叠，错误卡按钮仍为 48dp。

#### 改动范围

- **中**。
- 代码：`RunLog.kt`、`ScriptRunner.kt`、`ScriptService.kt`、`RunNotifications.kt`、`EditorActivity.kt`、`ScheduleReceiver.kt`（调用签名）。`ScriptService` 需要同时保存 `runningName` 和 `runningId`，完成通知才能带上脚本 id。
- 新增：`core/TextLines.kt`、`RunLogMeta`（可放在 `RunLog.kt`）。
- 布局：`activity_editor.xml`。
- 测试：`TextLinesTest` JVM；`RhinoExecutionTest` 或新增用例断言错误行号；`EditorActivityTest` 增加日志头部/错误卡断言；通知深链手工验收。

---

### R5 编辑器保存状态与重命名（P1，小）

#### 用户问题

编辑页标题单行截断，保存图标始终可用，用户无法判断当前内容是否已保存；保存成功只有一次短暂 Toast。重命名必须退出编辑页、回首页、打开「⋮ → 重命名」，对于正在整理脚本的用户是多余往返，也有丢失未保存内容的风险。

#### 方案

**1）保存状态副标题**

- `activity_editor.xml` 的 toolbar 高度改为 `64dp`（`minHeight=56dp`），标题使用 `textAppearanceTitleMedium`（16sp），增加 subtitle：
  - `app:subtitleTextAppearance="?attr/textAppearanceBodySmall"`，`subtitleTextColor=?attr/colorOnSurfaceVariant`。
  - 标题 `maxLines=1`、`ellipsize=end`；副标题 `maxLines=1`。
- 状态文案：
  - 初始/保存后：`已保存 · 15:57`
  - 有改动：`未保存的修改`，颜色 `colorPrimary`
  - 刚保存 2 秒内：`已保存 · 15:57`，可加 `ic_check` 前缀（文字优先）
- 保存菜单 `action_save` 的 `isEnabled` 与 dirty 状态绑定：
  - clean：禁用（灰色勾），contentDescription `已保存`
  - dirty：启用，contentDescription `保存`
  - 保存中：禁用 + `保存中…`（保存是同步写文件，通常瞬时；若超过 100ms 再显示）
- dirty 判断：`binding.scriptEditor.text.toString() != lastSavedContent`，在 `afterTextChanged` 中更新；`lastSavedContent` 在加载、保存、恢复版本后同步。
- 保存失败（`repository.save` 返回 false）时：保留 dirty，Snackbar `保存失败，脚本可能已被删除`，不要显示「已保存」。
- 保存成功不再依赖 Toast；保留 `已保存` 副标题即可。TalkBack 在保存成功时 `announceForAccessibility("已保存")`，不要在每次输入时播报「未保存」。

**2）编辑页重命名**

- `menu_editor.xml` 增加菜单项 `action_rename`，标题 `重命名`，`showAsAction=never`。
- 复用 `dialog_rename.xml`：
  - 预填当前名称，全选或光标置尾。
  - 空名称：`TextInputLayout.error = "名称不能为空"`，不关闭对话框，不再用 Toast。实现时 `setPositiveButton` 后要在 `onShow` 里覆盖按钮点击监听，校验失败直接 return，否则系统会默认关闭对话框。
  - 重名：`ScriptRepository.rename` 会自动追加 `(2)`；成功后读取 `repository.find(scriptId)?.name` 更新标题，Snackbar `已重命名为「实际名称」`。
- 运行中允许重命名，但只影响后续运行；通知/状态条继续显示启动时的快照名称，不强行改历史记录。
- 首页的脚本/文件夹重命名对话框后续可复用同一套 inline error；本次不强制改造。

#### 验收标准

1. 打开脚本，副标题显示 `已保存 · HH:mm`，保存图标禁用。
2. 输入任意字符，副标题在 1 帧内变为 `未保存的修改`，保存图标启用；撤销回已保存内容后恢复 `已保存` 并禁用保存。
3. 点保存后副标题更新为 `已保存 · HH:mm`，返回首页能看到修改时间更新；保存失败时保持 `未保存` 并给出错误 Snackbar。
4. 编辑页菜单出现「重命名」；输入空名称时对话框内显示错误、不关闭；重名时显示实际追加后的名称。
5. 重命名后 toolbar 标题、首页列表名称一致；脚本内容和版本历史不受影响。
6. 2 倍字体下标题仍单行省略，副标题不遮挡菜单图标，toolbar 不裁切。
7. TalkBack 保存成功只播报一次「已保存」；连续输入不会反复播报未保存。

#### 改动范围

- **小**。
- 布局：`activity_editor.xml`、`menu_editor.xml`。
- 代码：`EditorActivity.kt`、`strings.xml`。
- 测试：`EditorActivityTest` 增加副标题、保存禁用/启用、重命名断言。

---

### R6 API 速查搜索与插入（P1，中）

#### 用户问题

API tab 目前是 6 组、35 条卡片纵向平铺。没有搜索，用户只能靠记忆滚动；示例代码不能直接落到编辑器，需要长按选择、复制、切 tab、粘贴，移动端操作成本很高。

#### 方案

**1）固定搜索区**

把 `activity_editor.xml` 的 `apiPage` 从 `NestedScrollView` 改为垂直 `LinearLayout`：

```
LinearLayout id=apiPage（weight=1, visibility=gone）
  TextInputLayout apiSearchLayout  Dense Outlined, 8dp 圆角, startIcon=搜索, endIcon=清除
    TextInputEditText apiSearchInput   hint=`搜索 API 或说明`
  TextView apiResultCount  12sp, colorOnSurfaceVariant, visibility=gone
  NestedScrollView（weight=1）
    LinearLayout apiList（现有动态卡片）
```

- 搜索框固定在顶部，滚动卡片时始终可见；左右 12dp，顶部 8dp。
- 输入即过滤，无需防抖（35 条量级，`doAfterTextChanged` 直接刷新）。
- 查询为空：显示全部；查询非空：显示 `找到 N 条`，无结果时显示空状态。
- 过滤规则：对 `section.title`、`entry.signature`、`entry.description`、`entry.example` 做不区分大小写的包含匹配；一个条目命中即保留，空分组标题隐藏。
- 空状态：居中 `ic_search` 52dp + 标题 `没有找到匹配的 API` + 说明 `试试 text、click、findOne，或清空搜索` + TextButton `清空搜索`。
- 可选增强：命中文本用 `Spannable` 加 `colorPrimary` 前景色，成本高可先不做。

**2）每条 API 可插入**

- 在 API 卡片底部增加右侧 `MaterialButton`：
  - 文案 `插入`
  - style `TextButton`，48dp minHeight
  - contentDescription `插入 ${entry.signature} 的示例`
- 点击逻辑：
  - 取 `entry.example ?: entry.signature` 作为插入文本。
  - 若光标前不是换行，前缀补 `\n`；若选区后不是换行，后缀补 `\n`。
  - `undoManager.breakCoalescing()` → `editor.text.replace(start, end, snippet)` → 设置光标到插入内容末尾 → 再 `breakCoalescing()`，保证一次撤销能撤回整段。
  - 切到 `TAB_CODE`，`requestFocus()`，`post { bringPointIntoView(cursor) }`。
  - Snackbar `已插入示例`，操作 `撤销`；点撤销调用 `undoManager.undo()`。
- 插入时如果编辑器有选区，按标准文本替换处理；不额外确认。
- 若插入内容包含多行示例，保留原缩进；不自动格式化用户代码。

**3）搜索状态保持**

- tab 切换时保留查询和滚动位置（View 未销毁，天然保留）；Activity 重建时可选把查询存入 `onSaveInstanceState`，避免旋转后丢失。

#### 验收标准

1. API tab 顶部有固定搜索框；向下滚动卡片时搜索框不移动。
2. 输入 `findOne` 只显示包含该词的条目/分组，结果数量正确；输入 `xyz` 显示空状态和「清空搜索」。
3. 点某条 API 的「插入」，示例按换行边界插入到编辑器光标处，自动切到编辑 tab，光标位于插入内容末尾；一次撤销即可恢复插入前内容。
4. 有选区时插入会替换选区，不产生重复换行或半行拼接。
5. 搜索框、结果数量、插入按钮的 TalkBack 文案完整；结果数量变化用 polite live region，不打断输入。
6. 2 倍字体下搜索框、结果数量和卡片不重叠；插入按钮仍为 48dp。

#### 改动范围

- **中**。
- 布局：`activity_editor.xml`（`apiPage` 结构）。
- 代码：`EditorActivity.kt`（过滤、渲染、插入）、`ApiDocs.kt`（不需要改数据，可加纯函数）、`strings.xml`。
- 新增：`ui/ApiDocsFilter.kt`（纯 Kotlin、不依赖 Android；`ApiEntry` / `ApiSection` 目前在 `ui` 层，避免让 `core` 反向依赖 `ui`）。
- 测试：`ApiDocsFilterTest` 放 `app/src/test/java/com/microauto/ui/`，只测纯函数；`EditorActivityTest` 增加搜索过滤、空状态、插入与撤销断言。

---

### R7 版本预览与空状态发布（P2，中）

#### 用户问题

版本历史列表只有 `v3 / 备注 / 时间`，点击行没有反应；用户要恢复某个版本时看不到内容，只能凭备注猜测。版本为空时页面只有说明文字，没有「发布当前版本」入口，用户必须返回编辑页再走菜单。

#### 方案

**1）版本行可预览**

- `VersionsAdapter` 增加 `onOpen: (ScriptVersion) -> Unit`，整行点击打开预览；右侧「⋮」保留恢复/删除。
- 新增 `VersionPreviewActivity`（或全屏 Dialog，推荐 Activity，便于长代码滚动和 edge-to-edge）：
  - 入参：`scriptId`、`versionId`。
  - 顶部 toolbar：标题 `v3`，返回。
  - 头部卡片：备注（空则 `无备注`）、创建时间、`共 42 行`、`与当前版本相比 +8 −2`（相同则 `与当前内容相同`）。
  - 代码区：`HorizontalScrollView` + 只读 `TextView`，JetBrains Mono 12sp，行号 + 代码；文本可选中复制；背景复用 `bg_log`。
  - 差异着色（可选但推荐）：新增行 `#DCFCE7` / 删除行 `#FEE2E2`（深色模式对应低透明度），左侧 `+` / `−`；不只靠颜色，符号同时存在。
  - 底部操作栏：主按钮 `恢复此版本`，TextButton `关闭`。
  - 当前内容与此版本相同时，`恢复此版本` 禁用，说明 `当前已是此版本`。
  - 版本文件缺失时显示空状态 `版本内容已损坏`，恢复按钮禁用。

**2）差异计算**

- 新增 `core/LineDiff.kt`：
  - `data class DiffLine(val kind: SAME/ADDED/REMOVED, val text: String, val oldLine: Int?, val newLine: Int?)`
  - `data class DiffResult(val lines: List<DiffLine>, val added: Int, val removed: Int)`
  - 使用按行的 LCS；超过 2,000 行时跳过差异计算，返回 `tooLarge = true`，预览只显示原文和 `文件较大，已跳过差异`。
  - 纯 Kotlin，补 JVM 测试：新增、删除、修改、空文件、相同文件、超限。
- 预览默认显示完整 diff；可加 `只看变更` 切换，过滤 SAME 行（保留前后 2 行上下文）。若工期紧，MVP 可只显示 `+N −N` 摘要 + 原文，不显示行级着色。

**3）恢复流程**

- 预览页点「恢复此版本」→ 确认对话框：
  - 标题 `恢复 v3`
  - 正文 `当前脚本内容会被覆盖；恢复前会自动保存一个「恢复前自动快照」。`
  - 主按钮 `恢复`，次按钮 `取消`
- 调用 `repository.restoreVersion(versionId)`，成功后 `setResult(RESULT_OK)` 并 `finish()`。
- `VersionsActivity` 用 `registerForActivityResult` 接收；OK 时刷新列表并 `setResult(RESULT_OK)`，用户返回编辑页时 `reloadScript()` 生效。
- 恢复失败：Snackbar `恢复失败，版本文件可能已损坏`，不关闭预览。

**4）版本空状态直接发布**

- `activity_versions.xml` 的空状态增加 `MaterialButton id=btnPublishVersion` 文案 `发布当前版本`。
- 点击复用 `DialogVersionNoteBinding`：备注可选；确认后 `repository.publishVersion(scriptId, note)`，刷新列表，Toast `已发布 v1`。
- 如果脚本不存在：Snackbar `脚本不存在，无法发布`；内容为空仍允许发布，发布后提示 `当前内容为空`，避免用户以为发布失败。

#### 验收标准

1. 版本列表整行可点，打开预览后显示该版本的真实内容、备注、时间、行数。
2. 与当前内容不同时显示 `+N −N`；相同显示 `与当前内容相同` 且恢复按钮禁用。
3. 差异行同时有 `+`/`−` 符号与背景色；2,000 行以上不卡顿，显示跳过差异提示。
4. 从预览恢复需二次确认，成功后返回版本列表并可回到编辑页看到恢复后的内容；恢复前自动快照存在。
5. 版本为空时，空状态有「发布当前版本」；发布后列表立即出现 v1，无需退出页面。
6. 预览代码可选中复制；TalkBack 能读版本标题、备注、行数和代码；2 倍字体不裁切头部信息。

#### 改动范围

- **中**。
- 新增：`VersionPreviewActivity.kt`、`activity_version_preview.xml`、`core/LineDiff.kt`；在 `AndroidManifest.xml` 注册新 Activity（`exported=false`）。
- 修改：`VersionsActivity.kt`、`VersionsAdapter.kt`、`item_version.xml`、`activity_versions.xml`、`strings.xml`。
- 测试：`LineDiffTest` JVM；`VersionsActivityTest` 增加行点击、空状态发布断言；预览恢复手工验收。

---

### R8 可访问性与大字号适配（P2，小）

#### 用户问题

现有界面已经大量使用 48dp 图标按钮和 contentDescription，但仍有几处具体问题：

- 列表里每一行的「更多操作」都读作同一个「更多操作」，TalkBack 用户分不清是哪个脚本/文件夹/版本。
- 编辑器行号是独立 TextView，TalkBack 会在代码前逐个读行号，干扰代码阅读。
- 脚本名和元信息固定单行，2 倍字体下容易截断；编辑页底部四个等宽文字按钮在 2 倍字体下会挤压或省略。
- 运行开始/成功/失败只有视觉变化，TalkBack 用户可能不知道状态已变化。

#### 方案

**1）上下文语义**

- `LibraryAdapter.ScriptHolder`：
  - `moreButton.contentDescription = "「${meta.name}」的更多操作"`
  - 行内运行按钮（R2）：`运行「${meta.name}」` / `「${meta.name}」正在运行`
- `FolderHolder`：`"「${folder.name}」的文件夹操作"`
- `VersionsAdapter`：`"v${version.version} 的版本操作"`
- 不要把整行 `MaterialCardView` 设成单一 `contentDescription`，否则可能吞掉运行/更多按钮的独立焦点；保留子控件可聚焦。
- `item_script.xml` / `item_folder.xml`：名称 `maxLines=2`、元信息 `maxLines=2`，卡片最小高度 64dp；普通字号仍以一行显示为主，长名称才换行。

**2）编辑器行号与标题**

- `lineNumbers` 增加 `android:importantForAccessibility="no"`，避免 TalkBack 读行号；代码编辑器保持可编辑语义。
- 教程/API 分组标题设置 `accessibilityHeading=true`（`ViewCompat.setAccessibilityHeading`），方便按标题导航。
- 装饰性图标（空状态、状态点）继续 `importantForAccessibility=no`；有语义的状态必须同时有文字。

**3）大字号底部操作栏**

- 当 `resources.configuration.fontScale >= 1.3` 时，编辑页底部四个文字按钮改为 48dp 图标按钮：
  - 运行 `ic_play_arrow`、停止 `ic_stop`、看界面 `ic_visibility`、定时 `ic_clock`
  - 每个按钮保留 `contentDescription`（`运行`、`停止`、`看界面`、`定时`）
  - 图标按钮之间 8dp，仍平均分布；普通字号保持现有文字按钮。
- 实现方式二选一，优先第一种：
  1. 新增 `activity_editor_actions_compact.xml`，`onCreate` 根据 `fontScale` inflate 到同一容器；
  2. 复用现有四个 `MaterialButton`，在 `onCreate` 中根据 `fontScale` 设置 `text=null`、`icon=...`、`iconGravity=ICON_GRAVITY_TEXT_START`（或 `iconGravity=ICON_GRAVITY_ONLY`）。
- 顶部 toolbar、tab、搜索框、API 卡片在 2 倍字体下允许换行/增高，不强行单行。

**4）动态状态播报**

- R3 状态条只在阶段变化时 `announceForAccessibility`，不要给每秒计时文本设 live region。
- R4 错误卡出现时设 `accessibilityLiveRegion=polite`（只出现一次，不会刷屏）；日志正文不设 live region。
- 保存成功（R5）只播报一次「已保存」。

**5）触控区**

- 所有可点击控件 ≥48dp：行内运行、更多、状态条操作、错误卡跳转、API 插入、版本行菜单、搜索清除。
- 图标按钮用 `Widget.Material3.Button.IconButton`，不要用 24dp ImageView 直接当按钮。
- 颜色不是唯一状态载体：定时圆点有 contentDescription；运行状态有图标 + 文案；版本差异有 +/− 符号。

#### 验收标准

1. TalkBack 焦点落在脚本行时，能分别读到脚本名、元信息、`运行「脚本名」`、`「脚本名」的更多操作`；不再出现多个同名「更多操作」。
2. 文件夹和版本行的操作按钮也包含对象名称/版本号。
3. TalkBack 不再逐行读编辑器行号；代码编辑器仍可正常输入、选中、撤销。
4. 2 倍字体、360dp 宽屏幕下：编辑页底部四个操作全部可见且不重叠；脚本名/元信息最多两行，不覆盖运行/更多按钮；toolbar 标题不裁切菜单。
5. 运行开始/成功/失败、保存成功、错误卡出现时，TalkBack 各播报一次，不持续刷屏。
6. 用 Accessibility Scanner 扫描首页、编辑页、日志页、版本页，无「触控目标过小」「缺少内容描述」的新问题。

#### 改动范围

- **小**。
- 布局：`item_script.xml`、`item_folder.xml`、`activity_editor.xml`、可选 `activity_editor_actions_compact.xml`。
- 代码：`LibraryAdapter.kt`、`VersionsAdapter.kt`、`EditorActivity.kt`、`MainActivity.kt`、`ApiDocs` 渲染处。
- 新增矢量图：`ic_visibility.xml`、`ic_clock.xml`（R1 已需要 `ic_clock`，可复用）。
- 测试：Espresso contentDescription 断言；2 倍字体与 TalkBack 手工验收。

---

## 4. 建议实施顺序

### 阶段一：v0.8「第一次就能跑起来」

1. **R1 运行环境引导**（中）
2. **R5 编辑器保存状态与重命名**（小）
3. **R2 首页一键运行**（小）

理由：R1 解决首次使用阻断；R5 是低成本信任修复；R2 把核心动作前置。三项都不依赖新架构，适合作为下一版的主干。

阶段出口：全新安装 → 环境提示 → 开启无障碍 → 首页一键运行 → 保存状态可见，整条链路可手工走通；`MainActivityTest`、`EditorActivityTest` 更新并通过。

### 阶段二：v0.9「运行中看得见，失败后找得到」

4. **R3 全局运行状态条**（中）
5. **R4 日志归属与错误定位**（中）

理由：R3 依赖 R2 已建立的 `RunState.activeScriptId`；R4 复用 R3 的 `RunSnapshot`/`RunPhase` 和 `EXTRA_OPEN_LOG`。两项一起做才能形成「启动 → 进度 → 结果 → 日志 → 错误行」闭环。

阶段出口：手动运行、定时运行、失败、停止、看界面五条路径都有明确状态；失败日志能跳到行；通知能直达日志 tab。

### 阶段三：v0.10「写脚本更省事」

6. **R6 API 速查搜索与插入**（中）
7. **R8 可访问性与大字号适配**（小）

理由：R6 是高频开发者工具；R8 在 R2/R3 新控件完成后统一补语义与大字体重排，避免返工。

阶段出口：API 搜索/插入可用；TalkBack 与 2 倍字体检查通过；Accessibility Scanner 无新增问题。

### 阶段四：v0.11「版本更安心」

8. **R7 版本预览与空状态发布**（中）

理由：独立于运行链路，频率低于前三条主线，适合最后做；也可以与任意阶段并行。

阶段出口：版本可预览、可对比、可恢复；空状态可直接发布。

### 依赖关系图

```
R1 ──> R2 ──> R3 ──> R4
              └────> R8
R5（独立）
R6（独立）
R7（独立）
```

- R3 的 `RunSnapshot` 与 R4 的 `RunLogMeta` 应共用 `RunPhase` / `RunSource` 枚举，避免两套状态定义。
- R8 最后做，确保新加的运行按钮、状态条、错误卡、插入按钮都带上语义。

---

## 5. 通用实施约束

### 5.1 视觉

- 沿用现有 token：8dp 圆角、1dp `colorOutlineVariant` 描边、0 阴影、页面边距 12dp、卡片内边距 12–16dp。
- 字号层级控制在 4 档：12sp（辅助）、14sp（正文/标题 small）、16sp（标题 medium）、22sp（页面大标题，仅首页/空状态）；代码与日志用 12–13sp JetBrains Mono。
- 主色只用于主操作、运行中、可点击文本；失败用 `colorError`；成功不新增绿色 token，用主色 + 对勾/文案区分。
- 不使用渐变、玻璃拟态、强投影；本项目的设计语言是「开发者工具」，不是消费级营销页。
- 所有新卡片复用 `Widget.MicroAuto.Card` / `Widget.MicroAuto.ListCard`，不要复制一套圆角/描边参数。

### 5.2 交互

- 主操作放在拇指区：首页运行按钮在行右侧，状态条操作在底部导航上方，编辑页运行在底部栏。
- 减少交互成本：能从列表直接做的事不强迫进编辑页；能一次点击完成的不做二次确认（删除、恢复、清空等破坏性操作除外）。
- 每个异步动作至少有「进行中 / 成功 / 失败」三态；失败必须给出下一步动作（重试、去设置、查看日志）。
- 空状态必须包含「为什么空 + 一个主操作 + 一个次操作」；不要只放一句说明。
- 动效保持 160ms alpha/位置过渡，不做弹跳、粒子、庆祝动画。

### 5.3 可访问性

- 触控目标 ≥48dp；图标按钮必须有 contentDescription；装饰图标 `importantForAccessibility=no`。
- 状态不能只靠颜色：运行/失败/定时/差异都要有文字或符号。
- 动态状态只在阶段变化时播报；不要给每秒更新的文本设 live region。
- 2 倍字体是验收条件，不是可选项。

### 5.4 技术与测试

- 不引入新依赖；新图标用矢量 XML，新页面用现有 ViewBinding。
- 纯逻辑（`TextLines`、`LineDiff`、`RunState` 迁移）放 `core/` 或 `script/`；`ApiDocsFilter` 依赖 `ui` 层的 `ApiEntry` / `ApiSection`，放 `ui/` 并保持无 Android 依赖，同样补 JVM 测试。
- UI 行为改动用 Espresso 覆盖关键断言；系统设置跳转、TalkBack、大字号、通知深链做手工验收。
- 提交前至少运行：
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:lintDebug`
  - `./gradlew :app:assembleDebug`
- 涉及 Activity/RecyclerView 的改动，再跑 `:app:connectedDebugAndroidTest`（需要模拟器/真机）。
- 更新 `README.md` 与 `docs/tutorial.md` 中受影响的说明；如果新增/调整用户可见文案，保持中文。

---

## 6. 风险与待确认

| 风险 | 影响 | 应对 |
|---|---|---|
| R1 合并菜单项导致现有测试失败 | `MainActivityTest` 断言「无障碍」 | 同步改为「运行环境」；保留无障碍面板内的直达操作 |
| R1 系统设置返回后状态刷新时机 | 提示条可能短暂残留 | `onResume` 重新计算；必要时延迟 300ms 再刷新一次 |
| R3 `RunState` 与 `RunLog` 两套状态不一致 | 状态条与日志状态矛盾 | 共用 `RunPhase` / `RunSource`；以 `ScriptService` 为唯一完成入口 |
| R4 Rhino 错误行号来自 `prelude.js` | 跳到用户脚本错误位置 | 用 `sourceName() == "script.js"` 判定；否则显示「内置 API 错误」 |
| R4 日志每秒追加 + 用户滚动 | 自动跟随与阅读冲突 | 只在接近底部时自动滚动；提供「回到底部」 |
| R6 插入示例包含重复变量名 | 用户脚本出现 `var node` 重复声明 | 插入后 Snackbar 提供「撤销」；文档提示按需修改变量名 |
| R7 大文件 diff 卡顿 | 版本预览打开慢 | 超过 2,000 行跳过 diff，只显示原文和摘要 |
| R7 新 Activity 增加维护面 | 页面数量增加 | 预览页只读、逻辑简单；恢复仍复用 Repository |
| R8 大字号图标按钮降低可发现性 | 文字用户不熟悉图标 | 保留 contentDescription；图标使用通用语义；后续可加长按提示 |
| 通知深链脚本已删除 | 打开空白编辑页 | 点击前检查 Repository；不存在则回首页并提示 |
| 2 倍字体下首页底部导航标签 | 标签可能省略 | 验收时检查；必要时 `labelVisibilityMode=selected` 或缩短文案 |

### 待确认

1. R1 是否接受把「无障碍 / 精确闹钟 / 省电」三个菜单项合并为「运行环境」？如果希望保留直达项，可以保留菜单项，同时把面板作为提示条主体入口。
2. R3 状态条默认全局显示（脚本 / 教程两个 tab 都可见，因为 `bottomStack` 在根布局底部）；如果只想在脚本 tab 显示，可把 `bottomStack` 移入 `scriptsPage`。需要确认哪种更符合预期。
3. R7 是否接受新增一个只读 `VersionPreviewActivity`？如果希望零新页面，可退化为全屏 Dialog，但长代码滚动体验较差。
4. R8 大字号下是否接受图标按钮替代文字按钮？如果不接受，备选方案是底部操作栏改为横向滚动，主操作仍在最左。
