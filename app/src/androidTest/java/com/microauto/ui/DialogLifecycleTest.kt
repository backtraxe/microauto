package com.microauto.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.R
import com.microauto.TestData
import com.microauto.accessibility.AccessibilityUtils
import com.microauto.script.ScriptRepository
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogLifecycleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scriptId: String

    @Before
    fun setUp() {
        TestData.reset()
        scriptId = ScriptRepository(context).create("生命周期测试", "log(1);").id
    }

    @Test
    fun environmentSheetDoesNotOutliveActivity() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openActionBarOverflowOrOptionsMenu(context)
            onView(withText("运行环境")).perform(click())
            onView(withText("无障碍服务（必须）")).check(matches(isDisplayed()))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.searchInput)).check(matches(isDisplayed()))
            openActionBarOverflowOrOptionsMenu(context)
            onView(withText("运行环境")).perform(click())
            onView(withText("无障碍服务（必须）")).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun mainRunInterceptionDialogDoesNotOutliveActivity() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(context))

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("运行「生命周期测试」")).perform(click())
            onView(withText("需要开启无障碍服务")).check(matches(isDisplayed()))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("需要开启无障碍服务")).check(doesNotExist())
        }
    }

    @Test
    fun editorRunInterceptionDialogDoesNotOutliveActivity() {
        Assume.assumeFalse(AccessibilityUtils.isEnabled(context))

        editorScenario().use {
            onView(withId(R.id.btnRun)).perform(click())
            onView(withText("需要开启无障碍服务")).check(matches(isDisplayed()))
        }

        editorScenario().use {
            onView(withText("需要开启无障碍服务")).check(doesNotExist())
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun scheduleSheetIsDismissedWithActivity() {
        editorScenario().use {
            onView(withId(R.id.btnSchedule)).perform(click())
            onView(withId(R.id.scheduleTime)).check(matches(isDisplayed()))
        }

        editorScenario().use {
            onView(withId(R.id.scheduleTime)).check(doesNotExist())
        }
    }

    private fun editorScenario(): ActivityScenario<EditorActivity> {
        val intent = Intent(context, EditorActivity::class.java)
            .putExtra(EditorActivity.EXTRA_SCRIPT_ID, scriptId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ActivityScenario.launch(intent)
    }
}
