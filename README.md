# SpiderCards

Two-suit Spider Solitaire for Android. No ads, no internet permission, no tracking.

## Install on your phone

1. On your phone, open the [latest release](../../releases/latest) and download the `.apk` file.
2. Open the downloaded file. If Android asks, allow your browser (or Files app) to install unknown apps.
3. To update later, download the newest release and open it the same way. Your saved game and stats are kept.

## How to play

- **Tap** a card to send it (and the cards on top of it) to the best spot.
- **Drag** a card to choose where it goes.
- **Hold** any card to see its whole column spread out. Tapping a card that can't move does the same for a moment.
- **Tap the stock** (bottom right) to deal a new row. Every column must have a card first.
- A full King-to-Ace run in one suit is cleared automatically. Clear all 8 to win.

Scoring: you start on 500, lose 1 point per move (dealing and undo count as moves) and gain 100 per cleared run.
The Stats screen keeps games played and won, your best time with the date you set it (and every earlier record),
and your top 10 scores.

## Project layout

- `game/` is the rules, scoring, stats and save format in plain Kotlin, with unit tests (`./gradlew :game:test`).
- `app/` is the Android app (Jetpack Compose).
- `.github/workflows/build.yml` tests and builds a signed APK on every push, and publishes a release from `main`.

The signing key in `app/spidercards.keystore` is committed on purpose. The app is only sideloaded, and Android
installs an update only if it is signed with the same key as the copy already on the phone.
