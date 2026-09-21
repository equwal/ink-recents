# Privacy

Ink Recents collects nothing.

The app has no INTERNET permission. It cannot send data anywhere. There are no
advertisements and no analytics.

## What the permissions do

**Usage access (PACKAGE_USAGE_STATS).** The app reads the usage log to put your
apps in order of last use. It reads the log on the device, and it shows the
result on the screen. It stores nothing from the log.

**Close background processes (KILL_BACKGROUND_PROCESSES).** The app ends the
background processes of an app that you close.

**Shizuku (moe.shizuku.manager.permission.API_V23).** If you run Shizuku, the
app asks the shell for the task list, and closes a task that you close. The
commands are `dumpsys activity recents`, `am stack remove` and `am kill`.

## What the app stores

The app stores one thing on the device: the time at which you closed each app.
That is how a closed app stays off the list until you open it again. Android
deletes it when you uninstall the app.

## Contact

truex@equwal.com
