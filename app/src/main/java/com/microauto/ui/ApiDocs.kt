package com.microauto.ui

data class ApiEntry(
    val signature: String,
    val description: String,
    val example: String? = null
)

data class ApiSection(
    val title: String,
    val entries: List<ApiEntry>
)

object ApiDocs {
    val sections: List<ApiSection> = listOf(
        ApiSection(
            "选择器",
            listOf(
                ApiEntry(
                    "text(value)",
                    "按控件文本精确匹配，返回选择器。",
                    "text(\"登录\").click();"
                ),
                ApiEntry(
                    "textContains(value)",
                    "文本包含指定内容即匹配。",
                    "textContains(\"确定\").exists();"
                ),
                ApiEntry(
                    "textMatches(re)",
                    "用正则匹配文本，re 为 JavaScript RegExp。",
                    "textMatches(/登录|登陆/).findOne();"
                ),
                ApiEntry(
                    "id(value)",
                    "按完整资源 id 精确匹配。",
                    "id(\"com.android.settings:id/search\").click();"
                ),
                ApiEntry(
                    "idContains(value)",
                    "资源 id 包含指定片段即匹配。",
                    "idContains(\":id/btn\").findAll();"
                ),
                ApiEntry(
                    "desc(value)",
                    "按 contentDescription 精确匹配。",
                    "desc(\"返回\").click();"
                ),
                ApiEntry(
                    "descContains(value)",
                    "contentDescription 包含指定内容即匹配。",
                    "descContains(\"关闭\").findOne();"
                ),
                ApiEntry(
                    "className(value)",
                    "按控件类名精确匹配。",
                    "className(\"android.widget.EditText\").findOne();"
                )
            )
        ),
        ApiSection(
            "选择器方法",
            listOf(
                ApiEntry(
                    ".text(v)  .textContains(v)  .textMatches(re)\n" +
                        ".id(v)  .idContains(v)  .desc(v)  .descContains(v)\n" +
                        ".className(v)  .clickable(bool)  .enabled(bool)  .selected(bool)",
                    "在选择器上继续筛选，返回新的选择器。",
                    "className(\"android.widget.Button\")\n" +
                        "    .clickable(true)\n" +
                        "    .textContains(\"签到\")\n" +
                        "    .findOne();"
                ),
                ApiEntry(
                    ".findOne(timeout = 10000)",
                    "轮询等待第一个匹配控件，超时返回 null。",
                    "var node = text(\"确定\").findOne(5000);\nif (node) node.click();"
                ),
                ApiEntry(
                    ".findAll()",
                    "返回所有匹配控件，最多 100 个。",
                    "var nodes = className(\"android.widget.TextView\")\n" +
                        "    .findAll();\n" +
                        "log(nodes.length);"
                ),
                ApiEntry(
                    ".exists()",
                    "立即判断控件是否存在，不等待。",
                    "if (text(\"完成\").exists()) toast(\"已就绪\");"
                ),
                ApiEntry(
                    ".waitFor(timeout = 10000)",
                    "等待控件出现，返回 true / false。",
                    "if (text(\"加载完成\").waitFor(3000)) log(\"ok\");"
                )
            )
        ),
        ApiSection(
            "控件对象",
            listOf(
                ApiEntry(
                    ".click()",
                    "点击该控件（无障碍 ACTION_CLICK）。",
                    "text(\"确定\").findOne().click();"
                ),
                ApiEntry(
                    ".longClick()",
                    "长按该控件。",
                    "text(\"删除\").findOne().longClick();"
                ),
                ApiEntry(
                    ".setText(value)",
                    "向输入框写入文本。",
                    "id(\"com.example:id/input\")\n" +
                        "    .findOne()\n" +
                        "    .setText(\"hello\");"
                ),
                ApiEntry(
                    ".scrollForward()",
                    "向前滚动该控件。",
                    "text(\"列表\").findOne().scrollForward();"
                ),
                ApiEntry(
                    ".text()  .desc()  .id()  .className()\n" +
                        ".clickable()",
                    "读取控件属性。",
                    "var node = textContains(\"订单\").findOne();\n" +
                        "if (node) log(node.text());"
                )
            )
        ),
        ApiSection(
            "坐标获取",
            listOf(
                ApiEntry(
                    ".centerX()  .centerY()",
                    "返回控件中心点的屏幕坐标，可直接传给 click(x, y)；读不到时返回 0。",
                    "var node = text(\"签到\").findOne();\n" +
                        "if (node) {\n" +
                        "  click(node.centerX(), node.centerY());\n" +
                        "}"
                ),
                ApiEntry(
                    ".bounds()  .left()  .top()  .right()  .bottom()",
                    "返回控件边界的屏幕像素。bounds() 为 { left, top, right, bottom }，读不到时各值为 0。",
                    "var b = text(\"签到\").findOne().bounds();\n" +
                        "click((b.left + b.right) / 2,\n" +
                        "      (b.top + b.bottom) / 2);"
                ),
                ApiEntry(
                    "dump()",
                    "把当前界面的节点树写入运行日志，每行包含 bounds=[左,上][右,下]。编辑页点「看界面」会自动执行。",
                    "dump();"
                )
            )
        ),
        ApiSection(
            "手势与按键",
            listOf(
                ApiEntry("click(x, y)", "点击屏幕坐标。", "click(540, 1200);"),
                ApiEntry("longClick(x, y)", "长按屏幕坐标。", "longClick(540, 1200);"),
                ApiEntry(
                    "press(x, y[, duration])",
                    "按住坐标指定时长，默认 500ms。",
                    "press(540, 1200, 1000);"
                ),
                ApiEntry(
                    "swipe(x1, y1, x2, y2[, duration])",
                    "滑动，默认 300ms。",
                    "swipe(540, 1800, 540, 600, 400);"
                ),
                ApiEntry("back()", "返回键。", "back();"),
                ApiEntry("home()", "回到桌面。", "home();"),
                ApiEntry("recents()", "打开最近任务。", "recents();")
            )
        ),
        ApiSection(
            "应用与系统",
            listOf(
                ApiEntry(
                    "launchApp(pkg)",
                    "启动指定包名的应用，返回是否成功。",
                    "launchApp(\"com.tencent.mm\");"
                ),
                ApiEntry(
                    "currentPackage()",
                    "返回当前窗口的包名，无法读取时返回 null。",
                    "log(currentPackage());"
                ),
                ApiEntry(
                    "waitForPackage(pkg[, timeout])",
                    "等待应用出现在前台，默认 10000ms。",
                    "waitForPackage(\"com.tencent.mm\", 5000);"
                ),
                ApiEntry("sleep(ms)", "暂停脚本指定毫秒。", "sleep(1000);"),
                ApiEntry("toast(message)", "显示一条短提示。", "toast(\"任务完成\");"),
                ApiEntry("log(message)", "写入运行日志。", "log(\"当前页面：\" + currentPackage());"),
                ApiEntry("exit()", "立即结束脚本。", "if (!text(\"确定\").exists()) exit();")
            )
        )
    )
}
