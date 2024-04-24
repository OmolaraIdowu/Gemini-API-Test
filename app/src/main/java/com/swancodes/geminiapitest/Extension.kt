package com.swancodes.geminiapitest

import android.view.View

fun View.gone() {
    if (visibility == View.VISIBLE) {
        visibility = View.GONE
    }
}

fun View.show() {
    visibility = View.VISIBLE
}