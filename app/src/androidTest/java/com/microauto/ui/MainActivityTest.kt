package com.microauto.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import com.microauto.R
import com.microauto.TestData
import com.microauto.script.BuiltinScripts
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
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
            onView(withId(R.id.chipBattery)).check(matches(isDisplayed()))
            onView(withId(R.id.chipSchedule)).check(matches(isDisplayed()))
            onView(withId(R.id.fabNew)).check(matches(isDisplayed()))
        }
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
    fun fabOpensEditor() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabNew)).perform(click())
            onView(allOf(withText("新建脚本"), isDisplayed())).perform(click())
            Thread.sleep(1500)
            onView(withId(R.id.scriptEditor)).check(matches(isDisplayed()))
            pressBack()
        }
    }

    @Test
    fun fabCreatesFolder() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabNew)).perform(click())
            onView(allOf(withText("新建文件夹"), isDisplayed())).perform(click())
            onView(withId(R.id.renameInput)).perform(replaceText("FAB Folder"))
            onView(withText("确定")).perform(click())
            onView(withText("FAB Folder")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tutorialOpens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText("使用教程")).perform(click())
            onView(withText("快速开始")).check(matches(isDisplayed()))
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
}
