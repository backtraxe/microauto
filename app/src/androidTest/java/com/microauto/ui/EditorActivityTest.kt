package com.microauto.ui

import android.app.AlarmManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.EditText
import android.widget.ScrollView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.microauto.R
import com.microauto.TestData
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.core.ScriptLimits
import com.microauto.script.RunLog
import com.microauto.script.RunPhase
import com.microauto.script.RunSource
import com.microauto.script.RunState
import com.microauto.script.ScriptRepository
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
class EditorActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scriptId: String

    @Before
    fun setUp() {
        TestData.reset()
        scriptId = ScriptRepository(context).create("UI 测试脚本", "toast('hello');").id
    }

    @After
    fun tearDown() {
        RunState.finish()
        RunLog.clear()
    }

    private fun launch(openLog: Boolean = false): ActivityScenario<EditorActivity> {
        val intent = Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_SCRIPT_ID, scriptId)
            .putExtra(EditorActivity.EXTRA_OPEN_LOG, openLog)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ActivityScenario.launch(intent)
    }

    @Test
    fun showsScriptAndScheduleSheet() {
        launch().use {
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
            onView(withText("UI 测试脚本")).check(matches(isDisplayed()))
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
            onView(withId(R.id.lineNumbers)).check(matches(isDisplayed()))
            onView(withId(R.id.btnSchedule)).perform(click())
            onView(withId(R.id.scheduleTime)).check(matches(isDisplayed()))
            closeSoftKeyboard()
            pressBack()
        }
    }

    @Test
    fun runWithoutAccessibilityShowsExplanationDialog() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(context))
        launch().use {
            onView(withId(R.id.btnRun)).perform(click())
            onView(withText("需要开启无障碍服务")).check(matches(isDisplayed()))
            onView(
                withText(
                    "MicroAuto 通过无障碍服务读取界面和执行点击。开启后返回即可运行当前脚本。"
                )
            ).check(matches(isDisplayed()))
            onView(withText("去开启")).check(matches(isDisplayed()))
            onView(withText("取消")).perform(click())
            onView(withText("需要开启无障碍服务")).check(doesNotExist())
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun scheduleSheetWarnsWhenExactAlarmMissing() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val manager = context.getSystemService(AlarmManager::class.java)
        Assume.assumeFalse(manager?.canScheduleExactAlarms() == true)
        launch().use {
            onView(withId(R.id.btnSchedule)).perform(click())
            onView(withId(R.id.scheduleSwitch)).perform(click())
            onView(withId(R.id.alarmWarningRow)).check(matches(isDisplayed()))
            onView(withText("精确闹钟未授权，定时可能延迟")).check(matches(isDisplayed()))
            onView(withText("去授权")).check(matches(isDisplayed()))

            onView(withId(R.id.scheduleSwitch)).perform(click())
            onView(withId(R.id.alarmWarningRow)).check(matches(not(isDisplayed())))
            pressBack()
        }
    }

    @Test
    fun tabsSwitchBetweenEditorAndLog() {
        launch().use {
            onView(withId(R.id.editorTabs)).check(matches(isDisplayed()))
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
            onView(withText(R.string.editor_tab_log)).perform(click())
            onView(withId(R.id.logPage)).check(matches(isDisplayed()))
            onView(withId(R.id.logScroll)).check(matches(isDisplayed()))
            onView(withText(R.string.editor_tab_code)).perform(click())
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun apiTabShowsFunctionDocs() {
        launch().use {
            onView(withText(R.string.editor_tab_api)).perform(click())
            onView(withId(R.id.apiPage)).check(matches(isDisplayed()))
            onView(withText("text(value)")).check(matches(isDisplayed()))
            onView(withText("选择器")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun oversizedScriptIsBlockedWithMessage() {
        val large = (1..ScriptLimits.MAX_SCRIPT_LINES + 1).joinToString("\n") { "x" }
        ScriptRepository(context).save(scriptId, large)

        launch().use {
            onView(withText("无法打开脚本")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(
                withText(containsString("脚本超过 20,000 行"))
            ).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("知道了")).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun characterOversizedScriptIsBlockedWithMessage() {
        val large = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
        ScriptRepository(context).save(scriptId, large)

        launch().use {
            onView(withText("无法打开脚本")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(containsString("脚本超过 128 KB")))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText("知道了")).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun oversizedSaveKeepsDirtyAndDoesNotWrite() {
        val original = ScriptRepository(context).read(scriptId)!!
        val large = "a".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
        launch().use { scenario ->
            setTextIgnoringFilters(scenario, large)
            onView(withId(R.id.action_save)).perform(click())
            Thread.sleep(200)

            onView(withText(containsString("保存失败：脚本超过 128 KB")))
                .check(matches(isDisplayed()))
            assertEquals(original, ScriptRepository(context).read(scriptId))
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("未保存的修改", toolbar.subtitle.toString())
                assertTrue(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }
        }
    }

    @Test
    fun largeProgrammaticTextDoesNotCrashOnStop() {
        val largeLength = ScriptLimits.MAX_SCRIPT_BYTES + 1
        launch().use { scenario ->
            setTextIgnoringFilters(scenario, "a".repeat(largeLength))
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.scriptEditor)
                assertEquals(largeLength, editor.text.length)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.scriptEditor)
                assertEquals(largeLength, editor.text.length)
            }
        }
    }

    private fun setTextIgnoringFilters(
        scenario: ActivityScenario<EditorActivity>,
        text: String
    ) {
        scenario.onActivity { activity ->
            val editor = activity.findViewById<EditText>(R.id.scriptEditor)
            val filters = editor.filters
            editor.filters = emptyArray()
            editor.setText(text)
            editor.filters = filters
        }
    }

    @Test
    fun normalTextSurvivesRecreate() {
        launch().use { scenario ->
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('rotate');"))
            scenario.recreate()
            onView(withId(R.id.scriptEditor)).check(matches(withText("log('rotate');")))
        }
    }

    @Test
    fun rejectedPasteOverSelectionKeepsOriginalText() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.scriptEditor)
                editor.setText("abcdef")
                editor.setSelection(2, 4)

                editor.text.replace(
                    2,
                    4,
                    "x".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
                )

                assertEquals("abcdef", editor.text.toString())
            }
        }
    }

    @Test
    fun rejectedPureInsertionKeepsOriginalText() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.scriptEditor)
                editor.setText("abcdef")
                editor.setSelection(6)

                editor.text.replace(
                    6,
                    6,
                    "x".repeat(ScriptLimits.MAX_SCRIPT_BYTES + 1)
                )

                assertEquals("abcdef", editor.text.toString())
            }
        }
    }

    @Test
    fun inputFilterAllowsDeletion() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.scriptEditor)
                editor.setText("abcdef")
                editor.setSelection(2, 4)

                editor.text.replace(2, 4, "")

                assertEquals("abef", editor.text.toString())
            }
        }
    }

    @Test
    fun lineNumbersFollowContent() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText("a\nb\nc"))
            onView(withId(R.id.lineNumbers)).check(matches(withText("1\n2\n3")))
        }
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
    fun saveMenuPersistsContent() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText("toast('saved');"))
            onView(withId(R.id.action_save)).perform(click())
            awaitCondition {
                ScriptRepository(context).read(scriptId) == "toast('saved');"
            }
            assertEquals("toast('saved');", ScriptRepository(context).read(scriptId))
        }
    }

    @Test
    fun subtitleAndSaveMenuFollowDirtyState() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertTrue(toolbar.subtitle.toString().startsWith("已保存 · "))
                assertFalse(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }

            onView(withId(R.id.scriptEditor)).perform(replaceText("log('changed');"))
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("未保存的修改", toolbar.subtitle.toString())
                assertTrue(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }

            onView(withId(R.id.action_save)).perform(click())
            awaitCondition {
                ScriptRepository(context).read(scriptId) == "log('changed');"
            }
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertTrue(toolbar.subtitle.toString().startsWith("已保存 · "))
                assertFalse(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }
            assertEquals("log('changed');", ScriptRepository(context).read(scriptId))
        }
    }

    @Test
    fun saveFailureKeepsDirtyState() {
        launch().use { scenario ->
            ScriptRepository(context).delete(scriptId)
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('unsaved');"))
            onView(withId(R.id.action_save)).perform(click())
            Thread.sleep(300)
            onView(withText("保存失败，脚本可能已被删除")).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("未保存的修改", toolbar.subtitle.toString())
                assertTrue(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }
        }
    }

    @Test
    fun runDoesNotStartWhenSaveFails() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(context))
        launch().use {
            ScriptRepository(context).delete(scriptId)
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('new');"))
            onView(withId(R.id.btnRun)).perform(click())
            Thread.sleep(300)

            onView(withText("保存失败，脚本可能已被删除")).check(matches(isDisplayed()))
            onView(withText("需要开启无障碍服务")).check(doesNotExist())
        }
    }

    @Test
    fun scheduleSheetDoesNotOpenWhenSaveFails() {
        launch().use {
            ScriptRepository(context).delete(scriptId)
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('new');"))
            onView(withId(R.id.btnSchedule)).perform(click())
            Thread.sleep(300)

            onView(withText("保存失败，脚本可能已被删除")).check(matches(isDisplayed()))
            onView(withId(R.id.scheduleTime)).check(doesNotExist())
        }
    }

    @Test
    fun publishDoesNotCreateVersionWhenSaveFails() {
        launch().use {
            ScriptRepository(context).delete(scriptId)
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('new');"))
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("发布版本")).perform(click())
            onView(withId(R.id.versionNoteInput)).perform(replaceText("失败版本"))
            onView(withText("发布")).perform(click())
            Thread.sleep(400)

            onView(withText("保存失败，脚本可能已被删除")).check(matches(isDisplayed()))
            assertTrue(ScriptRepository(context).versions(scriptId).isEmpty())
        }
    }

    @Test
    fun exitConfirmationKeepsEditorWhenSaveFails() {
        launch().use {
            ScriptRepository(context).delete(scriptId)
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('new');"))
            closeSoftKeyboard()
            pressBack()
            onView(withText("保存并退出")).perform(click())
            Thread.sleep(400)

            onView(withText("保存失败，脚本可能已被删除")).check(matches(isDisplayed()))
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun renamesFromEditorMenuWithInlineValidation() {
        launch().use { scenario ->
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("重命名")).perform(click())
            onView(withId(R.id.renameInput)).perform(replaceText(""))
            closeSoftKeyboard()
            onView(withText("确定")).perform(click())
            onView(withText("名称不能为空")).check(matches(isDisplayed()))

            onView(withId(R.id.renameInput)).perform(replaceText("新名称"))
            closeSoftKeyboard()
            onView(withText("确定")).perform(click())

            onView(withText("新名称")).check(matches(isDisplayed()))
            assertEquals("新名称", ScriptRepository(context).find(scriptId)?.name)
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("新名称", toolbar.title.toString())
            }
        }
    }

    @Test
    fun emptyRenameKeepsDialogOpenAndTitleUnchanged() {
        launch().use { scenario ->
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("重命名")).perform(click())
            onView(withId(R.id.renameInput)).perform(replaceText("   "))
            closeSoftKeyboard()
            onView(withText("确定")).perform(click())

            onView(withText("名称不能为空")).check(matches(isDisplayed()))
            onView(withId(R.id.renameInput)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("UI 测试脚本", toolbar.title.toString())
            }
            assertEquals("UI 测试脚本", ScriptRepository(context).find(scriptId)?.name)
            pressBack()
        }
    }

    @Test
    fun renameToExistingNameAppendsSuffix() {
        ScriptRepository(context).create("已有名称", "log('other');")
        launch().use { scenario ->
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("重命名")).perform(click())
            onView(withId(R.id.renameInput)).perform(replaceText("已有名称"))
            closeSoftKeyboard()
            onView(withText("确定")).perform(click())

            assertEquals("已有名称 (2)", ScriptRepository(context).find(scriptId)?.name)
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("已有名称 (2)", toolbar.title.toString())
            }
        }
    }

    @Test
    fun dirtyStateClearsWhenContentRevertsToSaved() {
        launch().use { scenario ->
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('changed');"))
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("未保存的修改", toolbar.subtitle.toString())
            }

            onView(withId(R.id.scriptEditor)).perform(replaceText("toast('hello');"))
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertTrue(toolbar.subtitle.toString().startsWith("已保存 · "))
                assertFalse(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }
        }
    }

    @Test
    fun undoAndRedoRestoreEditorContent() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText("abc"))
            onView(withId(R.id.action_undo)).perform(click())
            onView(withId(R.id.scriptEditor)).check(matches(withText("toast('hello');")))
            onView(withId(R.id.action_redo)).perform(click())
            onView(withId(R.id.scriptEditor)).check(matches(withText("abc")))
        }
    }

    @Test
    fun findReplaceAllUpdatesScriptContent() {
        launch().use { scenario ->
            onView(withId(R.id.scriptEditor)).perform(replaceText("cat cat"))
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("查找替换")).perform(click())
            onView(withId(R.id.findInput)).perform(replaceText("cat"))
            onView(withId(R.id.replaceInput)).perform(replaceText("dog"))
            closeSoftKeyboard()
            onView(withId(R.id.btnReplaceAll)).perform(click())
            scenario.onActivity { activity ->
                val editor = activity.findViewById<android.widget.EditText>(R.id.scriptEditor)
                assertEquals("dog dog", editor.text.toString())
            }
            pressBack()
        }
    }

    @Test
    fun publishesVersionFromMenu() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText("log('v1');"))
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("发布版本")).perform(click())
            onView(withId(R.id.versionNoteInput)).perform(replaceText("第一版"))
            onView(withText("发布")).perform(click())
            awaitCondition {
                ScriptRepository(context).versions(scriptId).isNotEmpty()
            }
            val versions = ScriptRepository(context).versions(scriptId)
            assertEquals(1, versions.size)
            assertEquals("第一版", versions[0].note)
        }
    }

    @Test
    fun openLogExtraSelectsLogTab() {
        launch(openLog = true).use {
            onView(withId(R.id.logPage)).check(matches(isDisplayed()))
            onView(withId(R.id.logTitle)).check(matches(withText("运行日志")))
        }
    }

    @Test
    fun logHeaderShowsStatusMetaAndForeignBanner() {
        val otherId = ScriptRepository(context).create("另一个脚本", "log('b');").id
        RunLog.begin(otherId, "另一个脚本", RunSource.MANUAL)
        RunLog.append("hello from b")

        launch(openLog = true).use {
            onView(withId(R.id.logStatusChip)).check(matches(withText("运行中")))
            onView(withId(R.id.logMeta)).check(
                matches(withText(containsString("「另一个脚本」")))
            )
            onView(withId(R.id.logForeignBanner)).check(matches(isDisplayed()))
            onView(withText("这是「另一个脚本」的运行日志，不是当前脚本"))
                .check(matches(isDisplayed()))
            onView(withText("运行当前脚本")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun sameScriptLogDoesNotShowForeignBanner() {
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.append("hello")

        launch(openLog = true).use {
            onView(withId(R.id.logForeignBanner)).check(matches(not(isDisplayed())))
            onView(withId(R.id.logMeta)).check(
                matches(withText(containsString("「UI 测试脚本」")))
            )
        }
    }

    @Test
    fun errorCardJumpsToLine() {
        ScriptRepository(context).save(scriptId, "line1\nline2\nfoo();")
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.recordError(3, "ReferenceError: foo is not defined")
        RunLog.complete(RunPhase.FAILED)

        launch(openLog = true).use { scenario ->
            onView(withId(R.id.errorCard)).check(matches(isDisplayed()))
            onView(
                withText("第 3 行 · ReferenceError: foo is not defined")
            ).check(matches(isDisplayed()))
            onView(withText("跳到第 3 行")).perform(click())

            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.scriptEditor)
                assertEquals("line1\nline2\n", editor.text.substring(0, editor.selectionStart))
                assertEquals(12, editor.selectionStart)
            }
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun builtinApiErrorHasNoJumpButton() {
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.recordError(null, "内置 API 错误：TypeError: cannot read property")
        RunLog.complete(RunPhase.FAILED)

        launch(openLog = true).use {
            onView(withId(R.id.errorCard)).check(matches(isDisplayed()))
            onView(withText("内置 API 错误：TypeError: cannot read property"))
                .check(matches(isDisplayed()))
            onView(withId(R.id.btnJumpToLine)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun copyLogWritesFullTextToClipboard() {
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.append("copy-me-1")
        RunLog.append("copy-me-2")

        launch(openLog = true).use {
            onView(withText("复制")).perform(click())
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val copied = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            assertTrue(copied.contains("copy-me-1"))
            assertTrue(copied.contains("copy-me-2"))
        }
    }

    @Test
    fun clearLogRequiresConfirmationAndEmptiesLog() {
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.append("clear-me")

        launch(openLog = true).use {
            onView(withText("清空")).perform(click())
            onView(withText("清空日志？")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("清空")).inRoot(isDialog()).perform(click())

            onView(withId(R.id.logText)).check(matches(withText("")))
            onView(withId(R.id.logEmpty)).check(matches(isDisplayed()))
            onView(withId(R.id.logStatusChip)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun clearIsDisabledWhileRunning() {
        RunState.start(scriptId, null, "UI 测试脚本", RunSource.MANUAL)

        launch(openLog = true).use {
            onView(withId(R.id.btnLogClear)).check(matches(not(isEnabled())))
        }
    }

    @Test
    fun foreignFailedLogHasNoJumpButton() {
        val otherId = ScriptRepository(context).create("另一个脚本", "log('b');").id
        RunLog.begin(otherId, "另一个脚本", RunSource.MANUAL)
        RunLog.recordError(3, "ReferenceError: foo is not defined")
        RunLog.complete(RunPhase.FAILED)

        launch(openLog = true).use {
            onView(withId(R.id.errorCard)).check(matches(isDisplayed()))
            onView(
                withText("「另一个脚本」· 第 3 行 · ReferenceError: foo is not defined")
            ).check(matches(isDisplayed()))
            onView(withId(R.id.btnJumpToLine)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun metaOnlyLogCanBeCleared() {
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.complete(RunPhase.SUCCESS)

        launch(openLog = true).use {
            onView(withId(R.id.btnLogClear)).check(matches(isEnabled())).perform(click())
            onView(withText("清空日志？")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("清空")).inRoot(isDialog()).perform(click())

            onView(withId(R.id.logStatusChip)).check(matches(not(isDisplayed())))
            onView(withId(R.id.logEmpty)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun missingScriptDeepLinkFallsBackToMain() {
        val intent = Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_SCRIPT_ID, "missing-script-id")
            .putExtra(EditorActivity.EXTRA_OPEN_LOG, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<EditorActivity>(intent).use {
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun backToBottomButtonRestoresAutoFollow() {
        RunLog.begin(scriptId, "UI 测试脚本", RunSource.MANUAL)
        RunLog.append((1..200).joinToString("\n") { "line $it" })

        launch(openLog = true).use { scenario ->
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.logScroll)
                scroll.scrollTo(0, 0)
            }
            Thread.sleep(200)
            RunLog.append("appended-while-reading")

            awaitCondition {
                scrollIsNotAtBottom(scenario)
            }
            onView(withId(R.id.btnLogBottom)).check(matches(isDisplayed())).perform(click())
            awaitCondition {
                scrollIsAtBottom(scenario)
            }
            onView(withId(R.id.btnLogBottom)).check(matches(not(isDisplayed())))
        }
    }

    private fun scrollIsNotAtBottom(scenario: ActivityScenario<EditorActivity>): Boolean {
        var result = false
        scenario.onActivity { activity ->
            val scroll = activity.findViewById<ScrollView>(R.id.logScroll)
            val content = scroll.getChildAt(0)
            result = content.height - scroll.height - scroll.scrollY > 48
        }
        return result
    }

    private fun scrollIsAtBottom(scenario: ActivityScenario<EditorActivity>): Boolean {
        var result = false
        scenario.onActivity { activity ->
            val scroll = activity.findViewById<ScrollView>(R.id.logScroll)
            val content = scroll.getChildAt(0)
            result = content.height - scroll.height - scroll.scrollY <= 48
        }
        return result
    }
}
