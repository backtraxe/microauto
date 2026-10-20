# MicroAuto 使用教程

从安装、开启无障碍，到写出第一个自动签到脚本。

> 本教程基于 v0.2.0。不同 Android 版本、厂商 ROM 和目标 App 可能有差异，遇到问题先看第 11 节。

## 0. 五分钟快速开始

1. 从 [Releases](https://github.com/backtraxe/microauto/releases) 下载 `app-debug.apk` 并安装。
2. 打开 MicroAuto，依次点「无障碍」「省电」「闹钟」，按提示授权。
3. 在首页点「新建」，进入脚本编辑页。保持屏幕点亮并解锁，点「运行」。示例脚本会弹 Toast，并在日志里打印 MicroAuto 自己的包名和节点树。
4. 要查看目标 App 的界面节点：点「看界面」，在 5 秒内切到目标 App 的签到页，日志里会输出它的节点树，用 `text` 或 `id` 找到签到按钮。
5. 按第 8 节改写脚本，先手动运行成功，再在编辑页配置「每天定时」。

## 1. 这是什么

MicroAuto 是一个非 root、纯本机运行的 Android 界面自动化工具。

- 用 JavaScript 写脚本。
- 通过无障碍服务读取界面节点，执行点击、长按、滑动、文本输入。
- 可以按包名启动 App、返回、回主页、打开最近任务。
- 支持多脚本文件管理：新建、导入、重命名、删除。
- 支持每天固定时间自动执行（同一时间绑定一个脚本）。
- 不抓包、不注入、不需要电脑、不需要 root。

适合：

- 自己账号的每日签到、领积分。
- 固定流程的重复点击、翻页、巡检。
- 学习 Android 无障碍自动化。

不适合：

- 需要 OCR、找图找色、验证码识别的场景。
- 依赖加密接口签名的批量操作。
- 违反目标 App 服务条款的刷量、薅羊毛。
- 银行、支付、政务等高风险账号。

## 2. 先看限制

- 必须开启无障碍服务，这是非 root 下跨 App 模拟点击的唯一通道。
- 执行时屏幕必须点亮且已解锁，锁屏状态无法操作。
- 单次脚本最长运行 10 分钟，同一时刻只允许一个脚本。
- 只能操作暴露了无障碍节点的界面。纯 Canvas、部分游戏、部分 WebView 可能读不到内容。
- 部分 App 会检测无障碍或模拟点击，可能拒绝操作或触发风控。
- 定时触发受系统 Doze、厂商保活策略和精确闹钟权限影响。
- v0.2.0 没有 OCR、找图、悬浮窗和可视化节点选择器。

## 3. 安装

### 从 Release 下载

打开 [Releases](https://github.com/backtraxe/microauto/releases)，下载最新的 `app-debug.apk`，传到手机安装。

如果覆盖安装提示「签名不一致」，先卸载旧版再装。当前 Release 使用 debug 签名，不同 CI 构建环境生成的签名可能不同。

### 自己构建

需要 JDK 17 和 Android SDK（platform 35、build-tools 35.0.0）：

```bash
git clone https://github.com/backtraxe/microauto.git
cd microauto
./gradlew :app:assembleDebug
```

APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

## 4. 开启权限

### 4.1 无障碍

打开 MicroAuto，点「无障碍」，在系统设置里找到「MicroAuto 自动化服务」并开启。

Android 13 及以上，侧载应用的开关可能是灰的。这时到「设置 → 应用 → MicroAuto → 右上角菜单 → 允许受限设置」，再回来开启无障碍。

### 4.2 省电与自启动

点「省电」，把 MicroAuto 设为「不受限制」或加入电池优化白名单。

国产 ROM 还需要在系统设置里允许 MicroAuto 自启动、后台运行，并在最近任务里锁定它，否则定时任务可能被杀。

### 4.3 通知

首次启动会申请通知权限。允许后，脚本运行时通知栏会显示「正在运行脚本」，并提供停止按钮。拒绝也不影响运行，但看不到运行状态。

### 4.4 精确闹钟

Android 12 及以上，点「闹钟」授予「闹钟和提醒」权限。没有这个权限时，定时会降级为非精确闹钟，可能延迟很久才触发。

### 4.5 保持屏幕常亮

建议使用一台插电的旧手机，在开发者选项里开启「充电时不锁定屏幕」，并把屏幕超时设为最长。

锁屏或息屏时，脚本会直接终止，并在日志里记录原因。

## 5. 第一个脚本

首次启动会创建一个示例脚本；从旧版本升级时，原来的单脚本会自动迁移成「默认脚本」。

在首页点脚本名称进入编辑页，示例脚本内容：

```js
// 示例：打印当前界面节点树
toast("MicroAuto 已运行");
log("前台包名: " + currentPackage());
dump();
```

点「运行」后：

- 屏幕上出现 Toast。
- 日志里出现当前前台包名。此时前台是 MicroAuto，所以会看到 `com.microauto`。
- 日志里输出当前界面的节点树。

要查看目标 App 的界面，请用「看界面」按钮：点它之后有 5 秒时间切到目标 App，随后自动 dump。

再看一个最小示例：

```js
toast("Hello MicroAuto");
log("当前包名: " + currentPackage());
```

「保存」只保存脚本，不运行；「运行」会先保存再执行；「停止」会中断当前脚本。

### 5.1 脚本管理

- 首页列表按修改时间排序。
- 「新建」创建一个新脚本并进入编辑页。
- 「导入」从系统文件选择器导入 `.js`、`.txt` 等文本脚本；同名会自动追加 `(2)`；文件上限 1 MB，二进制文件会被拒绝。
- 每行右侧「⋮」可以重命名、删除，或导出为 `.js` 文件。
- 脚本保存在应用内部，每个脚本一个独立文件。
- 删除脚本时，如果它是当前定时脚本，会同时取消定时。
- 定时任务同一时间只绑定一个脚本；在某个脚本里保存定时会接管全局定时。

## 6. 看懂界面节点树

调试脚本最重要的一步，是 dump 目标界面的节点树。在脚本编辑页点「看界面」，它会等待 5 秒再执行 dump；你有 5 秒时间切到目标 App 的目标页面。

等价脚本：

```js
toast("5 秒内切到目标界面");
sleep(5000);
dump();
```

注意：如果直接在 MicroAuto 前台运行 `dump()`，拿到的是 MicroAuto 自己的界面，不是目标 App。

日志里会出现类似下面的内容：

```
android.widget.FrameLayout id=- text=- desc=- clickable=false bounds=[0,0][1080,2400]
  android.widget.Button id=com.example.app:id/sign text=签到 desc=- clickable=true bounds=[360,1800][720,1920]
```

常用字段：

- `id`：控件的 resource-id，最稳定，优先使用。
- `text`：显示文字。
- `desc`：contentDescription，图标按钮常用这个。
- `className`：控件类型。
- `clickable`：是否可点击。
- `bounds`：屏幕坐标 `[左,上][右,下]`。

调试步骤：

1. 在 MicroAuto 的脚本编辑页点「看界面」。
2. 5 秒内切到目标 App，手动走到签到页，等待 dump 完成。
3. 回到 MicroAuto，在日志里搜索「签到」「立即签到」等文字。
4. 记录对应的 `id` 或 `text`。
5. 回到脚本，用选择器定位并点击。

注意：日志只保留最近约 20,000 个字符。界面特别大时，`dump()` 的结果可能被截断，可以先进入更具体的页面再 dump。

## 7. 选择器与常用 API

### 7.1 选择器

```js
text("签到");
textContains("签到");
textMatches(/签到|每日签到/);
id("com.example.app:id/sign");
idContains("sign");
desc("关闭");
descContains("关闭");
className("android.widget.Button");
```

选择器可以链式组合，多个条件之间是 AND：

```js
var node = text("签到").clickable(true).findOne(5000);
```

### 7.2 查找

```js
// 轮询等待，默认 10 秒，超时返回 null
var node = text("签到").findOne(5000);

// 立即检查，不等待
if (text("签到").exists()) { ... }

// 等待出现，返回 true/false
if (text("签到").waitFor(5000)) { ... }

// 立即返回所有匹配项
var list = text("签到").findAll();
log("匹配数量: " + list.length);
```

安全写法是先接住返回值，再判断：

```js
var node = text("签到").findOne(5000);
if (node) {
  node.click();
} else {
  toast("没有找到签到按钮");
}
```

### 7.3 控件操作

```js
node.click();          // 点击，返回 true/false
node.longClick();      // 长按
node.setText("hello"); // 输入文本
node.scrollForward();  // 向前滚动
node.text();           // 读取文字
node.desc();           // 读取 contentDescription
node.id();             // 读取 resource-id
node.className();      // 读取类名
```

点击策略是：先尝试无障碍 `ACTION_CLICK`，失败后向上找可点击的父控件，最后按控件中心坐标派发手势。

`setText` 前建议先点一下输入框：

```js
var input = id("com.example.app:id/input").findOne(5000);
if (input) {
  input.click();
  input.setText("hello");
}
```

### 7.4 坐标手势

```js
click(540, 1600);              // 点击坐标
longClick(540, 1600);          // 长按坐标
press(540, 1600, 800);         // 按住 800ms
swipe(540, 1800, 540, 600, 400); // 从下往上滑，400ms
```

坐标来自 `dump()` 的 `bounds`，或者系统开发者选项里的「指针位置」。

### 7.5 应用与系统

```js
launchApp("com.example.app");              // 按包名启动
currentPackage();                          // 当前前台包名
waitForPackage("com.example.app", 10000);  // 等待前台切换
back();                                    // 返回
home();                                    // 回主页
recents();                                 // 最近任务
```

`launchApp` 只接受包名，不接受应用名称。不知道包名时，先打开目标 App，运行：

```js
log(currentPackage());
```

### 7.6 工具函数

```js
sleep(2000);       // 阻塞当前脚本 2 秒
toast("完成");      // 屏幕提示
log("调试信息");    // 写入日志
exit();            // 立即结束脚本
dump();            // 输出当前界面节点树
```

脚本采用 ES5 风格，建议使用 `var` 和 `function`，避免过新的 JavaScript 语法。

## 8. 实战：自动签到

下面是一个通用模板。把包名和选择器换成你自己的目标 App。

```js
var PKG = "com.example.app";

function closePopups() {
  var popup = textMatches(/跳过|关闭|知道了|以后再说/).findOne(1000);
  if (popup) popup.click();
}

function hasSigned() {
  return textMatches(/已签到|今日已签到|签到成功/).exists();
}

// 1. 启动
launchApp(PKG);
if (!waitForPackage(PKG, 10000)) {
  toast("应用没有启动");
  log("当前包名: " + currentPackage());
  exit();
}

// 2. 等首页加载，关掉可能的弹窗
sleep(3000);
closePopups();

// 3. 如果今天已经签过，直接结束
if (hasSigned()) {
  toast("今天已经签过了");
  exit();
}

// 4. 进入签到页
var entry = textMatches(/签到|每日签到|领积分/).findOne(5000);
if (!entry) {
  toast("没有找到签到入口");
  dump();
  exit();
}
entry.click();
sleep(1500);
closePopups();

// 5. 点击签到按钮
var button = textMatches(/立即签到|签到|领取/).findOne(5000);
if (!button) {
  toast("没有找到签到按钮");
  dump();
  exit();
}
button.click();
sleep(1000);

// 6. 校验结果
if (hasSigned()) {
  toast("签到完成");
  log("签到成功");
} else {
  toast("请检查日志");
  dump();
}
```

使用前需要改的地方：

- `PKG`：目标 App 的包名。
- 「签到入口」的正则：改成目标 App 首页上的实际文字。
- 「签到按钮」的正则：改成签到页按钮的实际文字。
- 弹窗关键词：按目标 App 常见的弹窗调整。

建议先手动运行一次，确认每一步的日志和点击都正常，再配置定时。

## 9. 定时执行

1. 在首页点开要定时的脚本，进入编辑页。
2. 在时间框里填 `HH:mm`，例如 `08:05`。
3. 打开「启用」。
4. 点「保存定时」。

MicroAuto 会注册一个每天重复的闹钟，触发时执行这个脚本，执行完自动停止。同一时间只绑定一个脚本；在另一个脚本里保存定时会接管全局定时。

注意事项：

- **精确闹钟**：Android 12+ 需要点「闹钟」授权。没有授权时会使用非精确闹钟，可能延迟。
- **屏幕状态**：定时触发时如果锁屏或息屏，脚本会终止，并在日志里记录「屏幕已锁定」或「屏幕未点亮」。
- **保活**：把 MicroAuto 加入电池优化白名单、允许自启动，并在最近任务里锁定。
- **重启**：手机重启后会重新注册定时，但需要先解锁一次，让 App 能正常启动。
- **强制停止**：在系统设置里「强行停止」MicroAuto 会清除闹钟，需要重新打开 App 并保存定时。
- **时区**：修改系统时间或时区后，会自动重新计算下一次触发时间。
- **脚本变更**：重命名不影响定时；删除定时脚本会同时取消定时。
- **日志**：编辑页显示的是最近一次运行日志，可能来自另一个脚本。

定时任务执行后，打开 MicroAuto 就能在日志区看到本次运行结果。

## 10. 调试技巧

1. **先手动，后定时**。手动跑通再交给定时。
2. **每一步都打日志**：

   ```js
   log("步骤 1：启动应用");
   launchApp(PKG);
   log("步骤 2：等待首页");
   sleep(3000);
   ```

3. **找不到控件时先 dump**，不要猜选择器。
4. **等待动画**：页面切换、弹窗出现都需要时间，适当加 `sleep` 或增大 `findOne` 超时。
5. **优先用 id**，其次用 text/desc，最后才用坐标。
6. **正则覆盖变体**：例如 `/签到|每日签到|立即签到/`，减少文案变化导致的失效。
7. **点击返回 false**：说明控件不可点击或已经消失。用 `dump()` 看最新界面，必要时改用坐标点击。
8. **输入框**：先 `click()` 聚焦，再 `setText()`。
9. **列表**：先 `swipe()` 滑动，再在新的界面里查找。
10. **失败时 dump**：在 `else` 分支里调用 `dump()`，日志会保留失败时的界面结构。

## 11. 常见问题

### 无障碍开关是灰色的

Android 13+ 对侧载应用默认限制无障碍。到「设置 → 应用 → MicroAuto → 右上角菜单 → 允许受限设置」，然后再开启。

### 点运行没有反应

依次检查：

- 无障碍服务是否真的开启了。
- 屏幕是否点亮并解锁。
- 日志区是否有脚本错误。
- 前台服务通知是否出现。

### 找不到控件

- 运行 `dump()`，确认控件有 `text`、`id` 或 `desc`。
- 页面可能还没加载完，增加 `sleep` 或 `findOne` 超时。
- 目标界面可能是 WebView、Canvas 或游戏，没有无障碍节点。这种情况只能尝试坐标点击，稳定性很差。
- 控件可能在滚动区域外，先滑动再查找。

### 点击返回 false

- 控件可能不可点击，脚本会自动尝试父控件和坐标手势。
- 控件可能已经消失，重新 `findOne`。
- 坐标可能被其他窗口遮挡。

### `launchApp` 失败

- 包名写错。先打开 App，用 `currentPackage()` 确认。
- App 没有安装。
- 系统限制了后台启动 Activity。确保无障碍服务处于连接状态；部分 ROM 还需要额外的后台弹出界面权限。

### 定时不触发

- 检查「闹钟」权限是否授予。
- 检查电池优化和自启动设置。
- 是否在系统设置里强行停止过 App。
- 手机是否重启后从未解锁。
- 定时绑定的脚本是否已被删除；删除脚本会取消定时。
- 打开 MicroAuto 看日志里有没有「屏幕已锁定」「屏幕未点亮」。
- 非精确闹钟可能延迟，尽量授予精确闹钟权限。

### 覆盖安装提示签名不一致

当前 Release 是 debug 签名，不同 CI 构建环境可能不同。卸载旧版再安装即可。要稳定升级，需要配置正式签名。

### 脚本运行超过 10 分钟被停止

这是内置保护。拆分成更小的步骤，或减少等待时间。

### 目标 App 更新后脚本失效

界面文字、id 或布局变了。重新 `dump()`，更新选择器。这是 UI 自动化的常态，优先用稳定的 id。

### 日志太长或看不到开头

日志保留最近约 20,000 字符。先进入目标页面再 `dump()`，或者把大页面拆成多个步骤。

## 12. 安全与合规

- 只自动化你自己的账号。
- 遵守目标 App 的用户协议。自动签到本身可能违反协议，账号风险由使用者承担。
- 不要用于银行、支付、政务、医疗等高风险账号。
- 脚本在本机运行，MicroAuto 不上传脚本和日志。
- 不要把包含 Cookie、Token、手机号等敏感信息的脚本发到公开仓库。

## 13. 二次开发

项目结构：

```
app/src/main/java/com/microauto/
  accessibility/   无障碍服务、节点、手势
  api/             脚本可见的原生桥接
  core/            选择器、脚本命名与定时计算（可单测）
  schedule/        闹钟与开机恢复
  script/          Rhino 运行时、脚本仓库、前台服务、日志
  ui/              脚本列表与编辑页
app/src/main/assets/prelude.js   Auto.js 风格 API
```

新增一个脚本 API 的步骤：

1. 在 `AutomationBridge` 里加一个 public 方法。
2. 在 `prelude.js` 里加对应的全局函数或选择器方法。
3. 在 `app/src/test` 里补单元测试。
4. 运行：

   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug
   ```

提交后 GitHub Actions 会自动跑测试、Lint 和构建。

## 14. API 速查

| API | 说明 |
|---|---|
| `text(v)` / `textContains(v)` / `textMatches(re)` | 文本选择器 |
| `id(v)` / `idContains(v)` | resource-id 选择器 |
| `desc(v)` / `descContains(v)` | contentDescription 选择器 |
| `className(v)` | 类名选择器 |
| `.clickable(bool)` / `.enabled(bool)` / `.selected(bool)` | 状态条件 |
| `.findOne(timeout)` | 等待并返回一个控件，默认 10 秒 |
| `.findAll()` | 立即返回所有匹配控件 |
| `.exists()` | 立即判断是否存在 |
| `.waitFor(timeout)` | 等待出现 |
| `node.click()` / `longClick()` / `setText(v)` / `scrollForward()` | 控件操作 |
| `node.text()` / `desc()` / `id()` / `className()` | 读取控件信息 |
| `node.bounds()` | 控件屏幕坐标 |
| `click(x, y)` / `longClick(x, y)` / `press(x, y, ms)` | 坐标点击 |
| `swipe(x1, y1, x2, y2, ms)` | 滑动 |
| `sleep(ms)` | 等待 |
| `toast(msg)` / `log(msg)` / `exit()` | 提示、日志、退出 |
| `launchApp(pkg)` / `currentPackage()` / `waitForPackage(pkg, ms)` | 应用控制 |
| `back()` / `home()` / `recents()` | 系统导航 |
| `dump()` | 输出当前界面节点树 |

## 15. 相关链接

- 仓库：https://github.com/backtraxe/microauto
- Releases：https://github.com/backtraxe/microauto/releases
- Actions：https://github.com/backtraxe/microauto/actions
- 问题反馈：https://github.com/backtraxe/microauto/issues
