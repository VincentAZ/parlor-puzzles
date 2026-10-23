package com.parlor.puzzles

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parlor.core.platform.ads.*

/** Dedicated bottom slot outside scrolling content. A loading/failed banner occupies no space. */
@Composable
fun AdBanner(ads: AndroidAds, activity: Activity, placement: AdPlacement) {
    val enabled by ads.enabled.collectAsStateWithLifecycle()
    val current by ads.currentPlacement.collectAsStateWithLifecycle()
    if (!enabled || !placement.banner || current != placement) return
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt()
        var banner by remember(ads, activity, placement, width) { mutableStateOf<AndroidAds.BannerHandle?>(null) }
        DisposableEffect(ads, activity, placement, width) {
            val created = ads.banner(activity, placement, width)
            banner = created
            onDispose { created?.close() }
        }
        banner?.let { ad ->
            val height by ad.height.collectAsStateWithLifecycle()
            AndroidView(factory = { ad.view }, modifier = Modifier.width(width.dp).height(height.dp))
        }
    }
}
