package com.microauto.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.R
import com.microauto.TestData
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Before
    fun setUp() {
        TestData.reset()
    }

    @Test
    fun showsToolbarChipsAndFab() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
            onView(withText("脚本")).check(matches(isDisplayed()))
            onView(withId(R.id.chipAccessibility)).check(matches(isDisplayed()))
            onView(withId(R.id.chipAlarm)).check(matches(isDisplayed()))
            onView(withId(R.id.chipSchedule)).check(matches(isDisplayed()))
            onView(withId(R.id.fabNew)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun sampleScriptIsListed() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.scriptList)).check(matches(isDisplayed()))
            onView(withText("示例")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun fabOpensEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabNew)).perform(click())
            Thread.sleep(1500)
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
            pressBack()
        }
    }
}
