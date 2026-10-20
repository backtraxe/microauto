package com.microauto.api

import com.microauto.core.Selector
import java.util.regex.Pattern

class UiSelector internal constructor(internal val selector: Selector) {

    constructor() : this(Selector())

    fun text(value: String) = UiSelector(selector.copy(text = value))

    fun textContains(value: String) = UiSelector(selector.copy(textContains = value))

    fun textMatches(source: String, flags: String = "") =
        UiSelector(selector.copy(textPattern = compile(source, flags)))

    fun id(value: String) = UiSelector(selector.copy(id = value))

    fun idContains(value: String) = UiSelector(selector.copy(idContains = value))

    fun desc(value: String) = UiSelector(selector.copy(desc = value))

    fun descContains(value: String) = UiSelector(selector.copy(descContains = value))

    fun className(value: String) = UiSelector(selector.copy(className = value))

    fun clickable(value: Boolean) = UiSelector(selector.copy(clickable = value))

    fun enabled(value: Boolean) = UiSelector(selector.copy(enabled = value))

    fun selected(value: Boolean) = UiSelector(selector.copy(selected = value))

    private fun compile(source: String, flags: String): Pattern {
        var patternFlags = 0
        if (flags.contains("i")) patternFlags = patternFlags or Pattern.CASE_INSENSITIVE
        if (flags.contains("m")) patternFlags = patternFlags or Pattern.MULTILINE
        return Pattern.compile(source, patternFlags)
    }
}
