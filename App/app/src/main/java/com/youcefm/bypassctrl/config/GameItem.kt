package com.youcefm.bypassctrl.config

import android.graphics.drawable.Drawable

data class GameItem(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    var isSelected: Boolean = false
)
