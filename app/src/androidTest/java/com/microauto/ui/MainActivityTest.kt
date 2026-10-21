package com.microauto.ui

import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.microauto.R
import com.microauto.TestData
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.schedule.SchedulePrefs
import com.microauto.script.BuiltinScripts
import com.microauto.script.RunPhase
import com.microauto.script.RunSource
import com.microauto.script.RunState
import com.microauto.script.ScriptRepository
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Before
    fun setUp() {
        TestData.reset()
    }

    @After
    fun tearDown() {
        RunState.finish()
    }

    @Test
    fun showsToolbarAndNav() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
            onView(allOf(withText("自动化"), isDescendantOfA(withId(R.id.toolbar))))
                .check(matches(isDisplayed()))
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText(containsString("运行环境"))).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun homeSummaryReflectsScheduleAndRunState() {
        val script = ScriptRepository(TestData.context()).create("摘要脚本", "log(1);")
        SchedulePrefs.save(TestData.context(), script.id, true, 8, 30)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.homeDate)).check(matches(withText(containsString("月"))))
            onView(withId(R.id.homeGreeting)).check(matches(withText(containsString("好"))))
            onView(withId(R.id.homeAvatar)).check(matches(isDisplayed()))
            onView(withId(R.id.summaryScheduleTitle)).check(matches(withText("1 个定时")))
            onView(withId(R.id.summaryScheduleHint))
                .check(matches(withText(containsString("下一个 08:30"))))
            onView(withId(R.id.summaryRunningTitle)).check(matches(withText("空闲")))
            onView(withId(R.id.scriptCountText)).check(matches(withText(containsString("个脚本"))))

            scenario.onActivity {
                RunState.start(script.id, null, "摘要脚本", RunSource.MANUAL)
                RunState.markRunning()
            }
            awaitCondition {
                textOf(scenario, R.id.summaryRunningTitle) == "1 个运行中"
            }
            onView(withId(R.id.summaryRunningHint))
                .check(matches(withText(containsString("已运行 00:"))))
        }
    }

    @Test
    fun scriptRowsShowIconTileAndChips() {
        ScriptRepository(TestData.context()).create("卡片脚本", "log(1);")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.scriptList)
                val holder = findHolder(list, "卡片脚本") ?: error("脚本行未显示")
                val tile = holder.itemView.findViewById<TextView>(R.id.iconTile)
                val scheduleChip = holder.itemView.findViewById<TextView>(R.id.scheduleChip)
                val statusChip = holder.itemView.findViewById<TextView>(R.id.statusChip)
                assertEquals("卡", tile.text.toString())
                assertEquals("手动", scheduleChip.text.toString())
                assertTrue(statusChip.text.toString().contains("月"))
            }
        }
    }

    private fun textOf(scenario: ActivityScenario<MainActivity>, id: Int): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.findViewById<TextView>(id).text.toString()
        }
        return text
    }

    @Test
    fun builtinScriptsAreListed() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.scriptList)).check { view, _ ->
                val recycler = view as RecyclerView
                assertEquals(BuiltinScripts.all.size, recycler.adapter?.itemCount)
            }
        }
    }

    @Test
    fun centerActionOpensEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navCreateFab)).check(matches(isDisplayed())).perform(click())
            onView(allOf(withText("新建脚本"), isDisplayed())).perform(click())
            Thread.sleep(1500)
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun centerActionCreatesFolder() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navCreateFab)).check(matches(isDisplayed())).perform(click())
            onView(allOf(withText("新建文件夹"), isDisplayed())).perform(click())
            onView(withId(R.id.renameInput)).perform(replaceText("Nav Folder"))
            onView(withText("确定")).perform(click())
            onView(withText("Nav Folder")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tutorialTabOpens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(allOf(withText("教程"), isDisplayed())).perform(click())
            onView(withText("快速开始")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun searchFiltersScripts() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.searchInput)).perform(replaceText("节点"))
            Thread.sleep(400)
            onView(withId(R.id.scriptList)).check { view, _ ->
                val recycler = view as RecyclerView
                assertEquals(1, recycler.adapter?.itemCount)
            }
        }
    }

    @Test
    fun createsFolderAndOpensIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("新建文件夹")).perform(click())
            onView(withId(R.id.renameInput)).perform(replaceText("工作"))
            onView(withText("确定")).perform(click())
            onView(withText("工作")).check(matches(isDisplayed())).perform(click())
            onView(withText("这个文件夹是空的")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun environmentSheetShowsAllRows() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("运行环境")).perform(click())
            onView(withText("无障碍服务（必须）")).check(matches(isDisplayed()))
            onView(withText("通知权限（建议）")).check(matches(isDisplayed()))
            onView(withText("精确闹钟（定时需要）")).check(matches(isDisplayed()))
            onView(withText("电池优化（定时建议）")).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun setupBannerShowsWhenAccessibilityDisabled() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(TestData.context()))
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.setupBanner)).check(matches(isDisplayed()))
            onView(withText("无障碍服务未开启")).check(matches(isDisplayed()))
            onView(withText("开启后才能查找控件、点击和运行脚本")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun inlineRunButtonExplainsMissingAccessibility() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(TestData.context()))
        val script = ScriptRepository(TestData.context()).create("行内运行测试", "log(1);")
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("运行「行内运行测试」"))
                .check(matches(isDisplayed()))
                .perform(click())
            onView(withText("需要开启无障碍服务")).check(matches(isDisplayed()))
            onView(withText("取消")).perform(click())
            onView(withId(R.id.scriptList)).check(matches(isDisplayed()))
            onView(withId(R.id.scriptEditor)).check(doesNotExist())
            assertEquals("log(1);", ScriptRepository(TestData.context()).read(script.id))
        }
    }

    @Test
    fun runningStateShowsSpinnerAndDisablesOtherRows() {
        val repository = ScriptRepository(TestData.context())
        repository.create("空闲脚本", "log(2);")
        val script = repository.create("运行态测试", "log(1);")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { RunState.start(script.id) }
            Thread.sleep(300)
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.scriptList)
                val runningHolder = findHolder(list, "运行态测试") ?: error("运行行未显示")
                val spinner = runningHolder.itemView.findViewById<View>(R.id.runSpinner)
                val runningButton = runningHolder.itemView.findViewById<View>(R.id.btnRunScript)
                assertTrue(spinner.isShown)
                assertFalse(runningButton.isShown)
                assertTrue(spinner.contentDescription.toString().contains("运行态测试"))

                val idleHolder = findHolder(list, "空闲脚本") ?: error("空闲行未显示")
                val idleButton = idleHolder.itemView.findViewById<View>(R.id.btnRunScript)
                assertFalse(idleButton.isEnabled)
                assertEquals("已有脚本在运行", idleButton.contentDescription.toString())
            }
            scenario.onActivity { RunState.finish() }
        }
    }

    @Test
    fun environmentSheetStatusMatchesSystem() {
        val context = TestData.context()
        val accessibilityEnabled = AccessibilityUtils.isEnabled(context)
        val notificationSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notificationGranted = !notificationSupported || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        val alarmSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val alarmAvailable = alarmSupported &&
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        val ignoringBattery = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

        ActivityScenario.launch(MainActivity::class.java).use {
            openActionBarOverflowOrOptionsMenu(context)
            onView(withText("运行环境")).perform(click())

            onView(withId(R.id.accessibilityStatus)).check(
                matches(withText(if (accessibilityEnabled) "已开启" else "未开启"))
            )
            onView(withId(R.id.notificationStatus)).check(
                matches(
                    withText(
                        when {
                            !notificationSupported -> "无需设置"
                            notificationGranted -> "已允许"
                            else -> "未允许"
                        }
                    )
                )
            )
            onView(withId(R.id.alarmStatus)).check(
                matches(
                    withText(
                        when {
                            !alarmSupported -> "无需授权"
                            alarmAvailable -> "可用"
                            else -> "未授权"
                        }
                    )
                )
            )
            onView(withId(R.id.batteryStatus)).check(
                matches(withText(if (ignoringBattery) "已允许" else "未允许"))
            )

            onView(withId(R.id.btnAccessibility)).check(
                matches(if (accessibilityEnabled) not(isEnabled()) else isEnabled())
            )
            onView(withId(R.id.btnNotification)).check(
                matches(
                    if (notificationSupported && !notificationGranted) {
                        isEnabled()
                    } else {
                        not(isEnabled())
                    }
                )
            )
            onView(withId(R.id.btnAlarm)).check(
                matches(if (alarmSupported) isEnabled() else not(isEnabled()))
            )
            pressBack()
        }
    }

    @Test
    fun setupBannerOpensEnvironmentSheet() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(TestData.context()))
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.setupBanner)).perform(click())
            onView(withText("无障碍服务（必须）")).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun menuOnlyExposesUnifiedEnvironmentEntry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                val titles = (0 until toolbar.menu.size())
                    .map { toolbar.menu.getItem(it).title.toString() }
                assertTrue(titles.contains("运行环境"))
                assertTrue(titles.any { it.startsWith("定时") })
                assertFalse(titles.any { it.startsWith("无障碍") })
                assertFalse(titles.any { it.startsWith("精确闹钟") })
                assertFalse(titles.any { it.startsWith("省电") })
            }
        }
    }

    @Test
    fun runStatusShowsRunningTimerAndExpandsListPadding() {
        val script = ScriptRepository(TestData.context()).create("状态条测试", "log(1);")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start(script.id, null, "状态条测试", RunSource.MANUAL)
                RunState.markRunning()
            }
            awaitCondition { cardVisible(scenario) }

            onView(withId(R.id.runStatusCard)).check(matches(isDisplayed()))
            onView(withId(R.id.runStatusTitle))
                .check(matches(withText("正在运行「状态条测试」")))
            onView(withId(R.id.runStatusMeta))
                .check(matches(withText(containsString("已运行"))))
            onView(withId(R.id.runStatusAction)).check(matches(withText("停止")))
            awaitCondition {
                listPaddingPx(scenario) == expectedListPadding(scenario, statusVisible = true)
            }
            assertTrue(listPaddingPx(scenario) >= stackHeightPx(scenario))

            val firstMeta = readMeta(scenario)
            awaitCondition(2_500) { firstMeta != readMeta(scenario) }
        }
    }

    @Test
    fun scheduledRunMentionsScheduleSource() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start("id", null, "定时脚本", RunSource.SCHEDULE)
                RunState.markRunning()
            }
            awaitCondition { cardVisible(scenario) }

            onView(withId(R.id.runStatusMeta))
                .check(matches(withText(containsString("定时任务"))))
            onView(withId(R.id.runStatusMeta))
                .check(matches(withText(containsString("已运行 00:"))))
        }
    }

    @Test
    fun failedRunStaysUntilClosed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start("id", null, "失败脚本", RunSource.MANUAL)
                RunState.markRunning()
                RunState.recordError(3, "ReferenceError: foo is not defined")
                RunState.complete(RunPhase.FAILED)
            }
            awaitCondition { cardVisible(scenario) }

            onView(withId(R.id.runStatusTitle)).check(matches(withText("运行失败")))
            onView(withId(R.id.runStatusMeta))
                .check(matches(withText(containsString("第 3 行"))))
            onView(withId(R.id.runStatusAction)).check(matches(withText("查看日志")))

            Thread.sleep(1_000)
            onView(withId(R.id.runStatusCard)).check(matches(isDisplayed()))

            onView(withId(R.id.runStatusClose)).perform(click())
            awaitCondition { !cardVisible(scenario) }
            onView(withId(R.id.runStatusCard)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun failedStatusSurvivesRestoreWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val now = System.currentTimeMillis()
                RunState.failStart(
                    message = "ReferenceError: foo is not defined",
                    scriptId = "id",
                    scriptName = "旧失败",
                    source = RunSource.MANUAL,
                    startedAt = now - 40_000,
                    finishedAt = now - 31_000,
                )
            }
            awaitCondition { cardVisible(scenario) }

            onView(withId(R.id.runStatusTitle)).check(matches(withText("运行失败")))
            onView(withId(R.id.runStatusMeta))
                .check(matches(withText(containsString("ReferenceError"))))
            Thread.sleep(500)
            onView(withId(R.id.runStatusCard)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun successStatusAutoHidesAndRestoresListPadding() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start("id", null, "成功脚本", RunSource.MANUAL)
                RunState.markRunning()
                RunState.complete(RunPhase.SUCCESS)
            }
            awaitCondition { cardVisible(scenario) }

            onView(withId(R.id.runStatusTitle)).check(matches(withText("运行完成")))
            onView(withId(R.id.runStatusMeta))
                .check(matches(withText(containsString("用时"))))

            awaitCondition(6_000) { !cardVisible(scenario) }
            onView(withId(R.id.runStatusCard)).check(matches(not(isDisplayed())))
            awaitCondition {
                listPaddingPx(scenario) == expectedListPadding(scenario, statusVisible = false)
            }
        }
    }

    @Test
    fun staleSuccessAndStoppedResultsAreNotRestored() {
        val now = System.currentTimeMillis()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start(
                    scriptId = "id",
                    scriptName = "过期成功",
                    source = RunSource.MANUAL,
                    startedAt = now - 40_000,
                )
                RunState.complete(RunPhase.SUCCESS, finishedAt = now - 31_000)
            }
            awaitCondition { RunState.snapshot.value == null }
            onView(withId(R.id.runStatusCard)).check(matches(not(isDisplayed())))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start(
                    scriptId = "id",
                    scriptName = "过期停止",
                    source = RunSource.MANUAL,
                    startedAt = now - 40_000,
                )
                RunState.complete(RunPhase.STOPPED, finishedAt = now - 31_000)
            }
            awaitCondition { RunState.snapshot.value == null }
            onView(withId(R.id.runStatusCard)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun dumpRunNeverShowsHomeStatusBar() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start(null, null, "看界面", RunSource.DUMP)
                RunState.markRunning()
            }
            awaitCondition { RunState.snapshot.value?.phase == RunPhase.RUNNING }
            onView(withId(R.id.runStatusCard)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun statusRestoresAfterRecreateWhileRunning() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start("id", null, "旋转脚本", RunSource.MANUAL)
                RunState.markRunning()
            }
            awaitCondition { cardVisible(scenario) }
            scenario.recreate()
            awaitCondition { cardVisible(scenario) }

            onView(withId(R.id.runStatusCard)).check(matches(isDisplayed()))
            onView(withId(R.id.runStatusTitle))
                .check(matches(withText("正在运行「旋转脚本」")))
        }
    }

    private fun readMeta(scenario: ActivityScenario<MainActivity>): String {
        var meta = ""
        scenario.onActivity { activity ->
            meta = activity.findViewById<TextView>(R.id.runStatusMeta).text.toString()
        }
        return meta
    }

    private fun cardVisible(scenario: ActivityScenario<MainActivity>): Boolean {
        var visible = false
        scenario.onActivity { activity ->
            visible = activity.findViewById<View>(R.id.runStatusCard).isShown
        }
        return visible
    }

    private fun listPaddingPx(scenario: ActivityScenario<MainActivity>): Int {
        var padding = 0
        scenario.onActivity { activity ->
            padding = activity.findViewById<RecyclerView>(R.id.scriptList).paddingBottom
        }
        return padding
    }

    private fun stackHeightPx(scenario: ActivityScenario<MainActivity>): Int {
        var height = 0
        scenario.onActivity { activity ->
            height = activity.findViewById<View>(R.id.bottomStack).height
        }
        return height
    }

    private fun expectedListPadding(
        scenario: ActivityScenario<MainActivity>,
        statusVisible: Boolean,
    ): Int {
        var padding = 0
        scenario.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            val stackHeight = activity.findViewById<View>(R.id.bottomStack).height
            val floor = ((if (statusVisible) 176 else 104) * density).toInt()
            val gap = (24 * density).toInt()
            padding = maxOf(floor, stackHeight + gap)
        }
        return padding
    }

    private fun awaitCondition(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("条件未在 ${timeoutMs}ms 内满足")
    }

    @Test
    fun scriptRowsExposeContextualActionDescriptions() {
        val repository = ScriptRepository(TestData.context())
        repository.create("语义脚本", "log(1);")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.scriptList)
                val holder = findHolder(list, "语义脚本") ?: error("脚本行未显示")
                val more = holder.itemView.findViewById<View>(R.id.moreButton)
                val run = holder.itemView.findViewById<View>(R.id.btnRunScript)
                assertEquals("「语义脚本」的更多操作", more.contentDescription.toString())
                assertEquals("运行「语义脚本」", run.contentDescription.toString())
            }
        }
    }

    @Test
    fun folderRowsExposeContextualActionDescription() {
        val repository = ScriptRepository(TestData.context())
        repository.createFolder("语义文件夹")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.scriptList)
                val holder = findFolderHolder(list, "语义文件夹") ?: error("文件夹行未显示")
                val more = holder.itemView.findViewById<View>(R.id.folderMore)
                assertEquals(
                    "「语义文件夹」的文件夹操作",
                    more.contentDescription.toString(),
                )
            }
        }
    }

    @Test
    fun tutorialHeadingsAreAccessible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                listOf(
                    R.id.tutorialQuickStartTitle,
                    R.id.tutorialFirstScriptTitle,
                    R.id.tutorialScheduleTitle,
                    R.id.tutorialFolderTitle,
                    R.id.tutorialVersionsTitle,
                    R.id.tutorialSearchTitle,
                    R.id.tutorialImportExportTitle,
                    R.id.tutorialLimitsTitle,
                    R.id.tutorialBuiltinsTitle,
                ).forEach { id ->
                    assertTrue(
                        "教程标题 $id 未标记为 heading",
                        ViewCompat.isAccessibilityHeading(activity.findViewById(id)),
                    )
                }
            }
        }
    }

    @Test
    fun runStatusExpandsEmptyStatePadding() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                RunState.start("id", null, "空状态", RunSource.MANUAL)
                RunState.markRunning()
            }
            awaitCondition {
                emptyPaddingPx(scenario) == expectedEmptyPadding(scenario, statusVisible = true)
            }
        }
    }

    private fun emptyPaddingPx(scenario: ActivityScenario<MainActivity>): Int {
        var padding = 0
        scenario.onActivity { activity ->
            padding = activity.findViewById<View>(R.id.emptyState).paddingBottom
        }
        return padding
    }

    private fun expectedEmptyPadding(
        scenario: ActivityScenario<MainActivity>,
        statusVisible: Boolean,
    ): Int {
        var padding = 0
        scenario.onActivity { activity ->
            val density = activity.resources.displayMetrics.density
            val stackHeight = activity.findViewById<View>(R.id.bottomStack).height
            val floor = ((if (statusVisible) 176 else 120) * density).toInt()
            val gap = (24 * density).toInt()
            padding = maxOf(floor, stackHeight + gap)
        }
        return padding
    }

    private fun findFolderHolder(
        list: RecyclerView,
        name: String,
    ): RecyclerView.ViewHolder? {
        val adapter = list.adapter ?: return null
        for (position in 0 until adapter.itemCount) {
            val holder = list.findViewHolderForAdapterPosition(position) ?: continue
            val title = holder.itemView.findViewById<TextView>(R.id.folderName)
                .text
                ?.toString()
            if (title == name) return holder
        }
        return null
    }

    private fun findHolder(
        list: RecyclerView,
        name: String
    ): RecyclerView.ViewHolder? {
        val adapter = list.adapter ?: return null
        for (position in 0 until adapter.itemCount) {
            val holder = list.findViewHolderForAdapterPosition(position) ?: continue
            val title = holder.itemView.findViewById<TextView>(R.id.nameText)
                .text
                ?.toString()
            if (title == name) return holder
        }
        return null
    }
}
