package com.microauto.ui

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.R
import com.microauto.TestData
import com.microauto.script.ScriptRepository
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VersionsActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TestData.reset()
    }

    @Test
    fun showsPublishedVersions() {
        val repository = ScriptRepository(context)
        val script = repository.create("版本脚本", "log(1);")
        repository.publishVersion(script.id, "第一版")
        repository.save(script.id, "log(2);")
        repository.publishVersion(script.id, "第二版")

        launchVersions(script.id).use {
            onView(withText("v2")).check(matches(isDisplayed()))
            onView(withText("v1")).check(matches(isDisplayed()))
            onView(withText("第二版")).check(matches(isDisplayed()))
            onView(withContentDescription("v1 的版本操作")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun clickingVersionRowOpensPreview() {
        val repository = ScriptRepository(context)
        val script = repository.create("版本脚本", "a\nb")
        repository.publishVersion(script.id, "第一版")

        launchVersions(script.id).use {
            onView(
                allOf(
                    withId(R.id.versionCard),
                    hasDescendant(withText("v1")),
                )
            ).perform(click())

            onView(withId(R.id.previewToolbar)).check(matches(isDisplayed()))
            onView(withId(R.id.previewCode)).check(matches(isDisplayed()))
            onView(withText("恢复此版本")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun moreButtonOpensMenuWithoutOpeningPreview() {
        val repository = ScriptRepository(context)
        val script = repository.create("菜单脚本", "a\nb")
        repository.publishVersion(script.id, "第一版")

        launchVersions(script.id).use {
            onView(withContentDescription("v1 的版本操作")).perform(click())
            onView(withText("删除此版本")).check(matches(isDisplayed()))
            onView(withId(R.id.previewToolbar)).check(doesNotExist())
            pressBack()
        }
    }

    @Test
    fun emptyStatePublishButtonCreatesVersion() {
        val repository = ScriptRepository(context)
        val script = repository.create("空版本脚本", "log(1);")

        launchVersions(script.id).use {
            onView(withId(R.id.btnPublishVersion))
                .check(matches(isDisplayed()))
                .perform(click())
            onView(withId(R.id.versionNoteInput)).perform(replaceText("空状态发布"))
            closeSoftKeyboard()
            onView(withText("发布")).perform(click())

            awaitCondition { repository.versions(script.id).size == 1 }
            onView(withText("v1")).check(matches(isDisplayed()))
            onView(withId(R.id.emptyState)).check(matches(not(isDisplayed())))
        }
        assertEquals("空状态发布", repository.versions(script.id).single().note)
    }

    @Test
    fun restoreFromPreviewRefreshesVersionsAndContent() {
        val repository = ScriptRepository(context)
        val script = repository.create("回滚脚本", "a\nb")
        val version = repository.publishVersion(script.id, "第一版")
            ?: error("发布失败")
        repository.save(script.id, "a\nc")

        launchVersions(script.id).use { scenario ->
            onView(
                allOf(
                    withId(R.id.versionCard),
                    hasDescendant(withText("v1")),
                )
            ).perform(click())
            onView(withId(R.id.btnRestoreVersion)).check(matches(isEnabled()))

            repository.publishVersion(script.id, "第二版")

            onView(withId(R.id.btnRestoreVersion)).perform(click())
            onView(withText("恢复 v1")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("恢复")).inRoot(isDialog()).perform(click())

            awaitCondition { repository.read(script.id) == "a\nb" }
            awaitCondition { hasVersionNote(scenario, "第二版") }
            assertTrue(version.id.isNotBlank())
        }
    }

    private fun hasVersionNote(
        scenario: ActivityScenario<VersionsActivity>,
        note: String,
    ): Boolean {
        var found = false
        scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.versionList)
            found = (0 until list.childCount).any { index ->
                list.getChildAt(index)
                    .findViewById<TextView>(R.id.versionNote)
                    ?.text
                    ?.toString() == note
            }
        }
        return found
    }

    private fun launchVersions(scriptId: String): ActivityScenario<VersionsActivity> {
        val intent = Intent(context, VersionsActivity::class.java)
            .putExtra(VersionsActivity.EXTRA_SCRIPT_ID, scriptId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ActivityScenario.launch(intent)
    }

    private fun awaitCondition(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("条件未在 ${timeoutMs}ms 内满足")
    }
}
