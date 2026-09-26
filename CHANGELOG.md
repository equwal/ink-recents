# Changelog

## 0.1.2

- The APK is smaller: R8 removes the code that the app does not use.

## 0.1.1

- Settings has a "Buy me a coffee" row. It opens ko-fi.com/truex. A tip unlocks nothing.

## 0.1.0

First release.

- Recent apps as cards, one card for each swipe, drawn for e-ink.
- Tap a card to open the app.
- Swipe a card up to close that app.
- Swipe a card down to close all the other apps.
- Two buttons: "Close all but <app>" and "Close all".
- A strip of icons shows every recent app. A tap on an icon brings its card to
  the middle.
- Two modes: usage access, or Shizuku for the real task list.
- A quick settings tile, and the intent action `dev.equwal.inkrecents.OPEN`.
- A settings screen with the state of each mode and the setup steps.
