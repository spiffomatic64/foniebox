# Architecture

Foniebox is a single-module native Android app written in Kotlin. It uses Android
framework APIs directly and has no declared application library dependencies.

| Component | Responsibility | Authoritative source |
| --- | --- | --- |
| `MainActivity` | Programmatic UI, folder permission, settings, status display | `app/src/main/java/com/example/foniebox/MainActivity.kt` |
| `Mp3Scanner` / `Mp3Track` | Recursive document-tree traversal and track records | `app/src/main/java/com/example/foniebox/MainActivity.kt` |
| `PlaybackService` | Shuffled queue, media player, foreground notification, fade timer | `app/src/main/java/com/example/foniebox/PlaybackService.kt` |

The activity starts the service with explicit intents. The service broadcasts
status to the app's package. Folder access uses Android's Storage Access
Framework and a persisted read permission. Preferences named `foniebox` store
the folder URI and the two duration values. There is no database or backend.

Audio playback uses `MediaPlayer`; a main-thread handler updates the timer and
volume once per second. The foreground service keeps playback visible through
a notification with a Stop action. The UI scans folders on a single-thread
executor; the service scans the selected folder when starting playback.

The manifest defines permissions and component visibility. Gradle defines
`com.example.foniebox` as both namespace and application ID, minimum SDK 26,
compile/target SDK 35, and Java/Kotlin target 21.
