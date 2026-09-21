// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 equwal
package dev.equwal.inkrecents

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

/** The apps on this device, as the recents list needs them. */
object Apps {

    data class App(val label: String, val pkg: String, val activity: String) {
        val component: ComponentName get() = ComponentName(pkg, activity)
    }

    /** One entry of the recent-apps list. [taskId] is known only with shell access. */
    data class Recent(val app: App, val taskId: Int?)

    /** Everything with a launcher icon, alphabetical, this app included. */
    fun all(c: Context): List<App> {
        val pm = c.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.pkg + "/" + it.activity }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(c: Context, app: App): Boolean = runCatching {
        c.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
        true
    }.getOrDefault(false)

    // ---- the usage log -----------------------------------------------------

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    /** Newest first, from the usage log. It needs the user's usage-access grant. */
    fun recentFromUsage(c: Context, limit: Int = 12): List<App> {
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3L * 24 * 60 * 60 * 1000, now)
        val last = LinkedHashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[e.packageName] = e.timeStamp
        }
        return rank(c, open(last, closedAt(c)), limit)
    }

    /**
     * The packages to show, newest first. An app that the user closed stays off
     * the list until the user opens that app again.
     */
    fun open(lastUsed: Map<String, Long>, closedAt: Map<String, Long>): List<String> =
        lastUsed.entries
            .filter { (pkg, at) -> at > (closedAt[pkg] ?: Long.MIN_VALUE) }
            .sortedByDescending { it.value }
            .map { it.key }

    private const val PREFS = "inkrecents_closed"

    private fun closedAt(c: Context): Map<String, Long> =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (pkg, at) -> (at as? Long)?.let { pkg to it } }.toMap()

    /**
     * Closes [pkg] without shell access. Android ends the background processes
     * of the app, and the app leaves the list of this screen.
     */
    fun closeWithoutShell(c: Context, pkg: String) {
        if (pkg == c.packageName) return
        runCatching { c.getSystemService(android.app.ActivityManager::class.java)?.killBackgroundProcesses(pkg) }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(pkg, System.currentTimeMillis()).apply()
    }

    // ---- the real task list ------------------------------------------------

    /**
     * The tasks in the output of `dumpsys activity recents`, newest first, as
     * package to task id.
     *
     * A task line names its app as `A=<uid>:<package>` or, for some tasks, as
     * `I=<package>/<class>`. Only a task of type `standard` counts: the others
     * are the home screen, the recents screen and the dream. A package that has
     * more than one task keeps the newest task id.
     */
    fun parseRecents(dump: String): Map<String, Int> {
        val task = Regex("""Recent #\d+: Task\{\S+ #(\d+) type=(\w+) (?:A=\d+:|I=)([^\s/}]+)""")
        val ids = LinkedHashMap<String, Int>()
        task.findAll(dump)
            .filter { it.groupValues[2] == "standard" }
            .forEach { m ->
                // A number too large for an Int is not a task id. Skip that line.
                m.groupValues[1].toIntOrNull()?.let { ids.putIfAbsent(m.groupValues[3], it) }
            }
        return ids
    }

    /** Newest first, from the real task list of the system. It needs shell access. */
    fun recentFromShell(c: Context, limit: Int = 12, done: (List<Recent>?) -> Unit) {
        if (!Shell.ready) return done(null)
        Shell.run("dumpsys activity recents") { r ->
            if (!r.ok) return@run done(null)
            val ids = parseRecents(r.output)
            done(rank(c, ids.keys.toList(), limit).map { Recent(it, ids[it.pkg]) })
        }
    }

    /**
     * The picture that the system keeps of a task. The files belong to `system`,
     * so this works only where the shell is root, or can use `su`. It gives null
     * in every other case.
     */
    fun snapshot(taskId: Int, done: (android.graphics.Bitmap?) -> Unit) {
        val dir = "/data/system_ce/0/snapshots/"
        val pick = "f=$dir${taskId}_reduced.jpg; [ -e \$f ] || f=$dir$taskId.jpg; "
        Shell.run(pick + "base64 -w 0 \$f 2>/dev/null || su 0 base64 -w 0 \$f 2>/dev/null") { r ->
            val bytes = runCatching {
                android.util.Base64.decode(r.output.trim(), android.util.Base64.DEFAULT)
            }.getOrNull()
            done(bytes?.takeIf { it.size > 100 }?.let {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            })
        }
    }

    /** Keeps the apps that have a launcher icon, and drops this app and the home screens. */
    private fun rank(c: Context, pkgs: List<String>, limit: Int): List<App> {
        val byPkg = all(c).associateBy { it.pkg }
        val home = homePackages(c)
        return pkgs.distinct()
            .filter { it != c.packageName && it !in home }
            .mapNotNull { byPkg[it] }
            .take(limit)
    }

    private fun homePackages(c: Context): Set<String> =
        c.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        ).map { it.activityInfo.packageName }.toSet()
}
