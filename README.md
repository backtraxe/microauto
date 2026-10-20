# MicroAuto

[![Android CI](https://github.com/backtraxe/microauto/actions/workflows/android.yml/badge.svg)](https://github.com/backtraxe/microauto/actions/workflows/android.yml)

一个非 root、纯本机运行的 Android 界面自动化工具。用 JavaScript 写脚本，通过无障碍服务查找控件、点击、滑动和启动应用，适合自动签到这类固定流程。

完整教程见 [docs/tutorial.md](docs/tutorial.md)，包含安装、权限、脚本 API、自动签到实战和常见问题。

## 已实现的最小功能

- 无障碍查找与操作：`text` / `textContains` / `textMatches` / `id` / `desc` / `className`
- 控件操作：`findOne`、`exists`、`click`、`longClick`、`setText`、`scrollForward`
- 坐标操作：`click(x, y)`、`longClick(x, y)`、`swipe(...)`、`press(...)`
- 应用与系统：`launchApp`、`currentPackage`、`waitForPackage`、`back`、`home`、`recents`
- 脚本工具：`sleep`、`toast`、`log`、`exit`、`dump`
- 多脚本文件管理：新建、导入、重命名、删除；支持一级文件夹、移动脚本，删除文件夹时脚本回到根目录
- 内置 5 个示例脚本与使用教程（底部“教程”tab），可从首页菜单恢复示例
- 每个脚本独立编辑，支持行号、等宽字体、JS 语法高亮、常用符号栏、撤销/重做、查找替换、保存、运行、停止和日志
- 脚本版本管理：发布快照（可写备注）、查看历史、恢复、删除
- 每天固定时间定时执行（每个脚本可单独定时，精确闹钟不可用时自动降级；息屏/锁屏时会在解锁后补跑）
- 运行结果通知（成功/失败/跳过）与首页列表直接运行、复制脚本
- 备份/恢复全部脚本、文件夹和版本历史（zip）
- 前台服务运行、开机恢复定时
- Material 3 界面：品牌配色取自图标（靛蓝 `#4338CA` + 天蓝 `#0EA5E9`）、浅色/深色主题、edge-to-edge、底部悬浮圆角导航（脚本 / 新建 / 教程）、圆角搜索框、脚本卡片、右上角权限菜单、定时底部 sheet

## 构建

需要 JDK 17 和 Android SDK（platform 36、build-tools 36.0.0）。

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/MicroAuto-<版本号>.apk`，例如本地开发构建为 `MicroAuto-0.3.2-dev.apk`，tag 构建为 `MicroAuto-0.3.2.apk`。

## 测试

JVM 单元测试（91 个）：

```bash
./gradlew :app:testDebugUnitTest
```

仪器化测试（36 个，需要模拟器或真机）：

```bash
./gradlew :app:connectedDebugAndroidTest
```

macOS Apple Silicon 本地一键模拟器测试（安装 API 35 arm64 镜像、创建 AVD、启动、跑测试）：

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./scripts/run-emulator-tests.sh
```

## 界面预览

<img src="docs/screenshots/home.png" width="260" alt="首页" />

截图：[首页](docs/screenshots/home.png) · [编辑页](docs/screenshots/editor.png) · [使用教程](docs/screenshots/tutorial.png) · [版本历史](docs/screenshots/versions.png) · [定时](docs/screenshots/schedule.png) · [运行日志](docs/screenshots/run-log.png)

## CI/CD

GitHub Actions 配置在 `.github/workflows/android.yml`：

- push 到 `main` 或提 PR：自动运行单元测试、Android Lint、构建 debug APK，并在 API 35 `default` 模拟器上运行仪器化测试；APK 作为 Actions artifact 上传。
- push `v*` 标签：`build` 和 `ui-test` 都通过后自动创建 GitHub Release，并附带 `MicroAuto-<版本号>.apk`。

版本号由 Git 自动生成：`versionName` 取最近 tag（当前提交不在 tag 上时追加 `-dev`），`versionCode` 取提交数。发布一个版本只需打 tag：

```bash
git tag v0.4.0
git push origin v0.4.0
```

## 签名

- **Debug**：仓库内固定 `signing/debug.keystore`（alias `androiddebugkey`，密码 `android`）。本地与 CI 的 debug APK 签名一致，可直接覆盖安装。
- **Release**：`~/.microauto-signing/microauto-release.jks`，本地通过根目录 `keystore.properties` 引用（已 gitignore）。
- **CI**：通过 GitHub Secrets 注入 `RELEASE_KEYSTORE_BASE64`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`；打 `v*` tag 时构建并校验 release 签名，再创建 Release。
- **务必备份** keystore 与密码；丢失后无法对已发布应用做覆盖升级。

本地构建 release APK：

```bash
cp keystore.properties.example keystore.properties  # 填入实际路径和密码
./gradlew :app:assembleRelease
```

校验签名：

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/MicroAuto-*.apk
```

## 使用

1. 安装 APK，打开 MicroAuto。
2. 点右上角菜单里的「无障碍」，在系统设置中开启「MicroAuto 自动化服务」。Android 13+ 侧载应用如果开关是灰的，先到应用信息里允许「受限设置」。
3. 点右上角菜单里的「省电」，把 MicroAuto 设为不受电池优化限制；并在系统设置里允许自启动（国产 ROM）。
4. 点右上角菜单里的「精确闹钟」授予权限（Android 12+），否则定时可能被系统延后。
5. 点底部导航中间的「新建」，选择「新建脚本」或「新建文件夹」；也可以点右上角菜单 →「导入脚本」从文件选择器导入 `.js` / `.txt` 文件。
6. 点脚本卡片进入编辑页，保存后运行。
7. 需要定时签到：在编辑页点「设置」，选择时间并启用，保存定时。

建议使用一台插电、常亮的旧手机，并在开发者选项中开启「充电时不锁定屏幕」。锁屏状态下无法执行 UI 操作；定时任务触发时若未解锁，会在 1 小时内每 2 分钟重试，解锁后补跑。

## 脚本管理

- 首页列表按修改时间排序。
- 新建：创建一个「新脚本」并进入编辑页。
- 运行/复制：列表行右侧「⋮」→「运行」或「复制」，不需要先进入编辑页。
- 导入：从系统文件选择器导入文本脚本；同名会自动追加 `(2)`。
- 导出：列表行右侧「⋮」→「导出」，保存为 `.js` 文件。
- 重命名：列表行右侧「⋮」→「重命名」。
- 删除：列表行右侧「⋮」→「删除」；如果删的是当前定时脚本，会同时取消定时。
- 备份/恢复：首页菜单 →「备份全部脚本」导出 zip；「恢复备份」会覆盖当前脚本、文件夹和版本历史。
- 每个脚本保存为应用内部独立文件。
- 索引文件损坏时会自动备份原文件并尽量恢复有效记录，启动后弹窗提示。
- 每个脚本可以单独设置定时；修改一个脚本的定时不会影响其他脚本。
- 定时触发时如果屏幕未点亮或未解锁，会在 1 小时内每 2 分钟重试；超过窗口会在通知栏提示已跳过。

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

调试时在编辑页点「看界面」，它会在 5 秒后 dump，给你时间切到目标界面。等价脚本：

```js
toast("5 秒内切到目标界面");
sleep(5000);
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
