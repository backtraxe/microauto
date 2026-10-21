package com.microauto.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.microauto.R
import com.microauto.TestData
import com.microauto.script.ScriptRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun launch(): ActivityScenario<EditorActivity> {
        val intent = Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_SCRIPT_ID, scriptId)
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
    fun lineNumbersFollowContent() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText("a\nb\nc"))
            onView(withId(R.id.lineNumbers)).check(matches(withText("1\n2\n3")))
        }
    }

    @Test
    fun saveMenuPersistsContent() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText("toast('saved');"))
            onView(withId(R.id.action_save)).perform(click())
            Thread.sleep(300)
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
            onView(withText("保存失败，脚本可能已被删除")).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                assertEquals("未保存的修改", toolbar.subtitle.toString())
                assertTrue(toolbar.menu.findItem(R.id.action_save).isEnabled)
            }
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
            val versions = ScriptRepository(context).versions(scriptId)
            assertEquals(1, versions.size)
            assertEquals("第一版", versions[0].note)
        }
    }
}
