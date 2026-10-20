package com.microauto.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.TestData
import com.microauto.script.ScriptRepository
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

        val intent = Intent(context, VersionsActivity::class.java)
            .putExtra(VersionsActivity.EXTRA_SCRIPT_ID, script.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<VersionsActivity>(intent).use {
            onView(withText("v2")).check(matches(isDisplayed()))
            onView(withText("v1")).check(matches(isDisplayed()))
            onView(withText("第二版")).check(matches(isDisplayed()))
        }
    }
}
