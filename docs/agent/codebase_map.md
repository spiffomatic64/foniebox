# Codebase map

| Task | Location |
| --- | --- |
| UI, settings, folder picker, MP3 scanning | `app/src/main/java/com/example/foniebox/MainActivity.kt` |
| Playback, shuffling, timing, fade, notification | `app/src/main/java/com/example/foniebox/PlaybackService.kt` |
| Android permissions and components | `app/src/main/AndroidManifest.xml` |
| App label and theme | `app/src/main/res/values/` |
| Drawable and launcher icons | `app/src/main/res/drawable/`, `app/src/main/res/mipmap-*/` |
| SDK levels, app identity, JVM targets | `app/build.gradle.kts` |
| Android/Kotlin plugin versions | `build.gradle.kts` |
| Module inclusion and dependency repositories | `settings.gradle.kts` |
| Gradle distribution and launchers | `gradle/wrapper/`, `gradlew`, `gradlew.bat` |
| Setup and usage | `README.md` |
| Publication exclusions and line endings | `.gitignore`, `.gitattributes` |

`MainActivity` and `PlaybackService` are the runtime entrypoints. `Mp3Scanner`
and `Mp3Track` are declared after `MainActivity` in the same Kotlin file.
Settings are Android shared preferences; there are no schema, database, API,
authentication, or automated test directories.

Usually ignore `app/build/`, `.gradle/`, `.kotlin/`, `.idea/`, and
`local.properties` during source review. These contain generated or local state
and are excluded from Git. Root PNGs are source artwork; the drawable copy is
used by the app and the head image is used by the README.
