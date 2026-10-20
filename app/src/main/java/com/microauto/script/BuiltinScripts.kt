package com.microauto.script

object BuiltinScripts {

    data class Item(val name: String, val content: String)

    val all: List<Item> = listOf(
        Item(
            "示例：节点树",
            """
            // 示例：打印当前界面节点树
            toast("MicroAuto 已运行");
            log("前台包名: " + currentPackage());
            dump();
            """.trimIndent()
        ),
        Item(
            "示例：按文本点击",
            """
            // 示例：打开设置并点击一个文本按钮
            launchApp("com.android.settings");
            sleep(1200);

            var target = text("网络和互联网").findOne(5000);
            if (target) {
              target.click();
              log("已点击目标控件");
            } else {
              log("没有找到目标控件");
            }
            """.trimIndent()
        ),
        Item(
            "示例：滑动与等待",
            """
            // 示例：连续上滑三次
            for (var i = 0; i < 3; i++) {
              swipe(540, 1600, 540, 600, 400);
              sleep(600);
            }
            toast("滑动完成");
            """.trimIndent()
        ),
        Item(
            "示例：定时签到模板",
            """
            // 定时签到模板：把包名和按钮文字换成目标应用
            var pkg = "com.example.app";

            if (currentPackage() != pkg) {
              launchApp(pkg);
              sleep(2000);
            }

            var button = text("签到").findOne(8000);
            if (button) {
              button.click();
              toast("签到完成");
            } else {
              toast("未找到签到按钮");
            }
            """.trimIndent()
        ),
        Item(
            "示例：常用工具函数",
            """
            // 示例：封装一个等待并点击的小工具
            function waitAndClick(label, timeout) {
              var node = text(label).findOne(timeout || 5000);
              if (!node) {
                log("未找到: " + label);
                return false;
              }
              node.click();
              log("已点击: " + label);
              return true;
            }

            waitAndClick("确定");
            """.trimIndent()
        )
    )
}
