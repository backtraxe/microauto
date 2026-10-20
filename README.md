# MicroAuto

一个非 root、纯本机运行的 Android 界面自动化工具。用 JavaScript 写脚本，通过无障碍服务查找控件、点击、滑动和启动应用，适合自动签到这类固定流程。

## 已实现的最小功能

- 无障碍查找与操作：`text` / `textContains` / `textMatches` / `id` / `desc` / `className`
- 控件操作：`findOne`、`exists`、`click`、`longClick`、`setText`、`scrollForward`
- 坐标操作：`click(x, y)`、`longClick(x, y)`、`swipe(...)`、`press(...)`
- 应用与系统：`launchApp`、`currentPackage`、`waitForPackage`、`back`、`home`、`recents`
- 脚本工具：`sleep`、`toast`、`log`、`exit`、`dump`
- 单脚本编辑器、保存、运行、停止、日志
- 每天固定时间定时执行（精确闹钟不可用时自动降级）
- 前台服务运行、开机恢复定时

## 构建

需要 JDK 17 和 Android SDK（platform 35、build-tools 35.0.0）。

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 使用

1. 安装 APK，打开 MicroAuto。
2. 点「无障碍」，在系统设置中开启「MicroAuto 自动化服务」。Android 13+ 侧载应用如果开关是灰的，先到应用信息里允许「受限设置」。
3. 点「省电」，把 MicroAuto 设为不受电池优化限制；并在系统设置里允许自启动（国产 ROM）。
4. 点「闹钟」授予精确闹钟权限（Android 12+），否则定时可能被系统延后。
5. 编辑脚本，点「保存」「运行」。
6. 需要定时签到：填时间（如 `08:05`），打开「启用」，点「保存定时」。

建议使用一台插电、常亮的旧手机，并在开发者选项中开启「充电时不锁定屏幕」。锁屏状态下无法执行 UI 操作。

## 脚本示例

```js
launchApp("com.example.app");
waitForPackage("com.example.app", 10000);
sleep(3000);

if (text("跳过").exists()) {
  text("跳过").findOne(2000).click();
}

var entry = textMatches(/签到|每日签到/).findOne(5000);
if (entry) {
  entry.click();
  var button = textMatches(/立即签到|签到/).findOne(5000);
  if (button) button.click();
  toast("签到完成");
} else {
  toast("未找到签到入口");
}
```

调试时先运行：

```js
dump();
```

日志里会输出当前界面的节点树，包含 `text`、`id`、`desc`、`bounds`，用来编写选择器。

## API 约定

- `findOne(timeout)` 默认等待 10 秒，超时返回 `null`；与 Auto.js 的无限等待不同。
- 选择器链式调用组合多个条件，条件之间是 AND。
- 单击优先使用无障碍 `ACTION_CLICK`，失败后向上找可点击祖先，最后按坐标派发手势。
- 单次脚本最长执行 10 分钟。
- 同一时刻只运行一个脚本。

## 限制

- 非 root，必须依赖无障碍服务；部分应用会检测或拒绝无障碍操作。
- 锁屏、息屏时无法操作界面。
- 定时触发受系统 Doze 和厂商保活策略影响，需要自行加白名单。
- 首版没有 OCR、找图、悬浮窗和可视化选择器。

## 目录

```
app/src/main/java/com/microauto/
  accessibility/   无障碍服务、节点、手势
  api/             脚本可见的原生桥接
  core/            选择器与定时计算（可单测）
  schedule/        闹钟与开机恢复
  script/          Rhino 运行时、前台服务、日志
  ui/              单页界面
app/src/main/assets/prelude.js   Auto.js 风格 API
```
