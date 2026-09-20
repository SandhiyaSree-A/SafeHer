package com.safeher.app.ui.map

import com.safeher.app.BuildConfig

object MapConfig {
    /** MapTiler Streets style – works online; tiles are also cached automatically by MapLibre. */
    val styleUrl: String
        get() = "https://api.maptiler.com/maps/streets-v2/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
}
