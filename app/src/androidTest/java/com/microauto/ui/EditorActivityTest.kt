package com.microauto.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.R
import com.microauto.TestData
import com.microauto.script.ScriptRepository
import org.junit.Assert.assertEquals
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
            onView(withId(R.id.symbolBar)).check(matches(isDisplayed()))
            onView(withId(R.id.btnSchedule)).perform(click())
            onView(withId(R.id.scheduleTime)).check(matches(isDisplayed()))
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
    fun symbolBarInsertsPair() {
        launch().use {
            onView(withId(R.id.scriptEditor)).perform(replaceText(""))
            onView(withText("{")).perform(click())
            onView(withId(R.id.scriptEditor)).check(matches(withText("{}")))
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
}
