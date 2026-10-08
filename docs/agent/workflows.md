# Workflows

## Startup and folder selection

`MainActivity.onCreate()` loads the saved folder URI, builds the UI, requests
notification permission where applicable, and calls `refreshFolderState()`.
The picker uses `ACTION_OPEN_DOCUMENT_TREE`; `onActivityResult()` persists read
permission and the URI. The scanner executor calls `Mp3Scanner.findMp3s()`, which
recurses through document-tree children and accepts MP3 names or `audio/mpeg`
MIME types. The count controls whether Start is enabled.

## Playback and fade-out

`MainActivity.startPlayback()` saves the two durations and starts
`PlaybackService` with `ACTION_START`. `startFromIntent()` scans tracks, creates
a shuffled queue, starts the foreground notification, and calls `playNext()`.
The player prepares asynchronously and advances on completion or error.
When the queue empties, the service reshuffles all tracks.

The timer calls `updateFadeOrStop()` every second. Volume stays at full level
for the playback duration, then decreases over the additional fade duration.
At the end, `stopPlayback()` releases the player and stops the service.

## Status and stopping

The activity registers a status receiver while resumed and requests current
status through `ACTION_STATUS_REQUEST`. The service's `broadcastStatus()` sends
a package-scoped status intent containing mode and remaining time. The app's
Stop button and the notification's Stop action both send `ACTION_STOP`.

## Build and publication

Configure JDK 21 and the Android SDK locally, then run the Gradle wrapper with
`assembleDebug lintDebug`. APKs and reports go under `app/build/` and are ignored.
The Unix launcher needs executable permission in Git. Before publishing,
review `git ls-files --cached --others --exclude-standard` and rescan those
files for personal paths and credentials. Do not stage ignored local settings
or signing files with `git add -f`.
