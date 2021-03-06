package com.droidfeed.util.extension

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.droidfeed.util.AnimUtils

/**
 * Hides the soft keyboard.
 */
fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.fadeIn(duration: Long = AnimUtils.SHORT_ANIM_DURATION) {
    if (isVisible && alpha != 1f) {
        animate().apply {
            this.duration = duration
            alpha(1f)
            start()
        }
    }
}

fun View.fadeOut(
    toAlpha: Float = 0f,
    duration: Long = AnimUtils.SHORT_ANIM_DURATION
) {
    if (isVisible && alpha != toAlpha) {
        animate().apply {
            this.duration = duration
            alpha(toAlpha)
            start()
        }
    }
}