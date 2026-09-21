// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 equwal
package dev.equwal.inkrecents

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import dev.equwal.inkrecents.Ui.button
import dev.equwal.inkrecents.Ui.header
import dev.equwal.inkrecents.Ui.note
import dev.equwal.inkrecents.Ui.row

/**
 * The two ways this app can read the recent apps, and what the app is.
 *
 * Usage access is enough to put the apps in order of last use. Shizuku gives
 * shell access, which gives the real task list and a real close.
 */
class SettingsActivity : Activity() {

    private val redraw: () -> Unit = { if (!isFinishing) build() }

    override fun onResume() {
        super.onResume()
        Shell.onChange(redraw)
        Shell.connect(this)
        build()
    }

    override fun onPause() {
        super.onPause()
        Shell.removeOnChange(redraw)
    }

    private fun build() {
        val col = Ui.page(this, "Settings")

        col.header("Usage access")
        col.note("It puts your apps in order of last use.")
        col.row("State", null, enabled = false, state = if (Apps.hasUsageAccess(this)) "On" else "Off")
        col.button("Open usage access") { start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        col.header("Shizuku")
        col.note("It gives shell access. Then the cards are the real tasks, and a card closes its task.")
        col.row("State", null, enabled = false, state = Shell.describe(this))
        if (Shell.state(this) != Shell.State.READY) steps(col)

        col.header("About")
        col.row("Version", null, enabled = false, state = BuildConfig.VERSION_NAME)
        col.row("Licence", null, enabled = false, state = "GPL-3.0-or-later")
        col.row("Source code", REPO) { start(Intent(Intent.ACTION_VIEW, Uri.parse(REPO))) }
        col.row("Buy me a coffee", TIP.removePrefix("https://")) { start(Intent(Intent.ACTION_VIEW, Uri.parse(TIP))) }
    }

    /** The four steps to shell access, from the device itself. */
    private fun steps(col: LinearLayout) {
        val state = Shell.state(this)
        col.note("1. Install Shizuku. It is free.")
        if (state == Shell.State.NOT_INSTALLED) {
            col.button("Get Shizuku") {
                if (!start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Shell.SHIZUKU_PACKAGE)))) {
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                }
            }
        }

        col.note("2. Turn on wireless debugging in Developer options.")
        col.button("Open Developer options") {
            if (!start(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) {
                start(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }
        }

        col.note("3. In Shizuku, pair with the code, then press Start.")
        if (state != Shell.State.NOT_INSTALLED) {
            col.button("Open Shizuku") {
                packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let { start(it) }
            }
        }

        col.note("4. Let Ink Recents use Shizuku.")
        if (state == Shell.State.NO_PERMISSION) {
            col.button("Ask for permission") { Shell.requestPermission() }
        }
    }

    private fun start(i: Intent): Boolean =
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)

    private companion object {
        const val REPO = "https://github.com/equwal/ink-recents"
        const val TIP = "https://ko-fi.com/truex"
    }
}
