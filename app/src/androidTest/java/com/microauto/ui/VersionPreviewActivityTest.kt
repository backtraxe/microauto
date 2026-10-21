package com.microauto.ui

import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.microauto.R
import com.microauto.TestData
import com.microauto.core.DateFormats
import com.microauto.script.ScriptPaths
import com.microauto.script.ScriptRepository
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VersionPreviewActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TestData.reset()
    }

    @Test
    fun showsNoteTimeLineCountAndDiff() {
        val repository = ScriptRepository(context)
        val script = repository.create("预览脚本", "a\nb")
        val version = repository.publishVersion(script.id, "第一版")
            ?: error("发布失败")
        repository.save(script.id, "a\nc")

        launchPreview(script.id, version.id).use { scenario ->
            onView(withText("v1")).check(matches(isDisplayed()))
            onView(withId(R.id.previewNote)).check(matches(withText("第一版")))
            onView(withId(R.id.previewTime))
                .check(matches(withText(DateFormats.format(version.createdAt))))
            onView(withId(R.id.previewLineCount)).check(matches(withText("共 2 行")))
            onView(withId(R.id.previewDiffSummary)).check(matches(withText("+1 −1")))

            scenario.onActivity { activity ->
                val code = activity.findViewById<TextView>(R.id.previewCode)
                val text = code.text.toString()
                assertTrue(text.contains("  1 │ a"))
                assertTrue(text.contains("- 2 │ c"))
                assertTrue(text.contains("+ 2 │ b"))
            }
        }
    }

    @Test
    fun sameContentDisablesRestore() {
        val repository = ScriptRepository(context)
        val script = repository.create("相同脚本", "a\nb")
        val version = repository.publishVersion(script.id, "第一版")
            ?: error("发布失败")

        launchPreview(script.id, version.id).use {
            onView(withId(R.id.previewDiffSummary))
                .check(matches(withText("与当前内容相同")))
            onView(withId(R.id.previewHint))
                .check(matches(withText("当前已是此版本")))
            onView(withId(R.id.btnRestoreVersion)).check(matches(not(isEnabled())))
        }
    }

    @Test
    fun crlfOnlyDifferenceCountsAsSameContent() {
        val repository = ScriptRepository(context)
        val script = repository.create("换行脚本", "a\nb")
        val version = repository.publishVersion(script.id, "第一版")
            ?: error("发布失败")
        repository.save(script.id, "a\r\nb")

        launchPreview(script.id, version.id).use {
            onView(withId(R.id.previewDiffSummary))
                .check(matches(withText("与当前内容相同")))
            onView(withId(R.id.btnRestoreVersion)).check(matches(not(isEnabled())))
        }
    }

    @Test
    fun missingVersionFileShowsCorruptedState() {
        val repository = ScriptRepository(context)
        val script = repository.create("损坏脚本", "a\nb")
        val version = repository.publishVersion(script.id, "第一版")
            ?: error("发布失败")
        val versionFile = ScriptPaths.versionFile(
            File(context.filesDir, "scripts/versions"),
            script.id,
            version.id,
        ) ?: error("版本文件路径无效")
        assertTrue(versionFile.delete())

        launchPreview(script.id, version.id).use {
            onView(withId(R.id.previewEmpty)).check(matches(isDisplayed()))
            onView(withText("版本内容已损坏")).check(matches(isDisplayed()))
            onView(withId(R.id.btnRestoreVersion)).check(matches(not(isEnabled())))
        }
    }

    @Test
    fun emptyVersionShowsZeroLinesAndEmptyHint() {
        val repository = ScriptRepository(context)
        val script = repository.create("空版本", "")
        val version = repository.publishVersion(script.id, "空内容")
            ?: error("发布失败")
        repository.save(script.id, "a\nb")

        launchPreview(script.id, version.id).use {
            onView(withId(R.id.previewLineCount)).check(matches(withText("共 0 行")))
            onView(withId(R.id.previewHint)).check(matches(withText("当前版本内容为空")))
            onView(withId(R.id.btnRestoreVersion)).check(matches(isEnabled()))
        }
    }

    private fun launchPreview(
        scriptId: String,
        versionId: String,
    ): ActivityScenario<VersionPreviewActivity> {
        val intent = Intent(context, VersionPreviewActivity::class.java)
            .putExtra(VersionPreviewActivity.EXTRA_SCRIPT_ID, scriptId)
            .putExtra(VersionPreviewActivity.EXTRA_VERSION_ID, versionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ActivityScenario.launch(intent)
    }
}
