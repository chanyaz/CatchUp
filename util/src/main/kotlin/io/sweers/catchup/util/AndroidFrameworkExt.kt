/*
 * Copyright (c) 2017 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE")

package io.sweers.catchup.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.UiThread
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import io.reactivex.Observable
import java.io.File
import java.io.IOException

/*
 * Android framework extension functions for things like Context, Activity, Resources, etc
 */

fun Context.clearCache() = cleanDir(applicationContext.cacheDir)

private fun cleanDir(dir: File): Long {
  var bytesDeleted: Long = 0
  dir.listFiles()
      .forEach {
        val length = it.length()
        try {
          if (it.delete()) {
            d { "Deleted file" }
            bytesDeleted += length
          }
        } catch (e: IOException) {
          // Ignore these for now
        }
      }
  return bytesDeleted
}

/**
 * Attempt to launch the supplied [Intent]. Queries on-device packages before launching and
 * will display a simple message if none are available to handle it.
 */
fun Context.maybeStartActivity(intent: Intent): Boolean = maybeStartActivity(intent, false)

/**
 * Attempt to launch Android's chooser for the supplied [Intent]. Queries on-device
 * packages before launching and will display a simple message if none are available to handle
 * it.
 */
fun Context.maybeStartChooser(intent: Intent): Boolean = maybeStartActivity(intent, true)

private fun Context.maybeStartActivity(inputIntent: Intent,
    chooser: Boolean): Boolean {
  var intent = inputIntent
  return if (hasHandler(intent)) {
    if (chooser) {
      intent = Intent.createChooser(intent, null)
    }
    startActivity(intent)
    true
  } else {
    Toast.makeText(this, R.string.no_intent_handler,
        LENGTH_LONG).show()
    false
  }
}

/**
 * Queries on-device packages for a handler for the supplied [Intent].
 */
private fun Context.hasHandler(intent: Intent) =
    !packageManager.queryIntentActivities(intent, 0).isEmpty()

private val typedValue = TypedValue()

@ColorInt
@UiThread
fun Context.resolveAttributeColor(@AttrRes resId: Int): Int {
  theme.resolveAttribute(resId, typedValue, true)
  if (typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
    return typedValue.data
  }
  val colorRes = if (typedValue.resourceId != 0) {
    typedValue.resourceId
  } else {
    typedValue.data
  }
  return ContextCompat.getColor(this, colorRes)
}

fun Context.isInNightMode(): Boolean {
  val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
  return when (currentNightMode) {
    Configuration.UI_MODE_NIGHT_NO -> false // Night mode is not active, we're in day time
    Configuration.UI_MODE_NIGHT_YES -> true // Night mode is active, we're at night!
    else -> false // We don't know what mode we're in, assume notnight
  }
}

/**
 * Determine if the navigation bar will be on the bottom of the screen, based on logic in
 * PhoneWindowManager.
 */
fun Context.isNavBarOnBottom(): Boolean {
  val res = resources
  val cfg = resources.configuration
  val dm = res.displayMetrics
  val canMove = dm.widthPixels != dm.heightPixels && cfg.smallestScreenWidthDp < 600
  return !canMove || dm.widthPixels < dm.heightPixels
}

inline fun Context.dp2px(dipValue: Float) = resources.dp2px(dipValue)

inline fun Resources.dp2px(dipValue: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, displayMetrics)

fun Context.registerReceiver(intentFilter: IntentFilter): Observable<Intent> {
  return Observable.create { emitter ->
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        emitter.onNext(intent)
      }
    }

    registerReceiver(receiver, intentFilter)

    emitter.setCancellable { unregisterReceiver(receiver) }
  }
}
