English · [Русский](README.md)

# Gallery

A gallery for Android that replaces the stock one: a photo opened from any third-party app
opens here and can be swiped through its neighbours from the same folder. The timeline zooms
with two fingers from single shots to whole years, and in the viewer everything leaves the
screen — including the clock, the battery and the navigation buttons.

The specification is [`docs/SPEC.md`](docs/SPEC.md): decisions, behaviour, stages and risks.
It is the single source of truth; when a decision changes, it changes there.

## Status

Version 1.2.

All eleven stages are closed. What remains is outside the spec: player conveniences to be
discussed as needed (volume and brightness gestures, touch lock, track selection) and what was
deliberately postponed — the last section of the spec.

Measured on a Galaxy S22 Ultra with a library of ≈5000 files: cold start 603 ms, opening a photo
from another app 137–183 ms, timeline scrolling 13–15 % janky frames at a 9 ms median
(the 120 Hz budget is 8.3 ms), GPU 3 ms.

| Stage | Contents | Done |
|------|-----------|------|
| 0 | Skeleton, theme, four tabs | ✅ |
| 1 | Permissions, MediaStore, library index | ✅ |
| 2 | "Photos" timeline with zoom levels | ✅ |
| 3 | Viewer with disappearing bars | ✅ |
| 4 | Opening from other apps | ✅ |
| 5 | Favourites and trash | ✅ |
| 6 | Albums | ✅ |
| 7 | Settings | ✅ |
| 8 | Hidden albums | ✅ |
| 9 | Editor | ✅ |
| 10 | Polish | ✅ |
| 11 | Video player | ✅ |
| 12 | Formats the phone cannot handle itself | ✅ |
| 13 | A second engine for unsupported containers | ✅ |

## Stack

Kotlin, Jetpack Compose, Material 3. `minSdk 33`, `targetSdk 36`, `compileSdk 37`.
Thumbnails — Coil 3, photo zoom — Telephoto, video — Media3. Settings, albums and the vault
live in DataStore, one JSON document each: there is no Room in the project, and the reasoning
is in the spec.

## Layout

```
app/src/main/java/com/keavors/gallery/
├── data/
│   ├── MediaItem.kt        model, date rules, summary — no android, testable
│   ├── MediaDetails.kt     EXIF reading and formatting of shutter speed, aperture
│   ├── MediaActions.kt     share, copy to clipboard
│   ├── ExternalMedia.kt    finding an incoming file in the library and its neighbours
│   ├── ExternalIntent.kt   recognising a foreign uri
│   ├── MediaWrite.kt       favourites, trash, permanent delete
│   ├── Albums.kt           folders, virtual albums, order
│   ├── AlbumPreferences.kt pinned, hidden, custom albums
│   ├── AlbumStore.kt       their storage
│   ├── Settings.kt         all settings, sorting, filters
│   ├── SettingsStore.kt    their storage and export to a file
│   ├── DeviceLock.kt       fingerprint or device code
│   ├── EditOps.kt          editor operations, sizes, memory limit
│   ├── ImageEditing.kt     decoding, applying, saving
│   ├── FolderOps.kt        moving, copying and renaming folders
│   ├── Search.kt           search by name, folder, date and type
│   ├── MotionPhoto.kt      video hidden inside a photograph
│   ├── Vault.kt            vault entries and the way back
│   └── VaultStore.kt       moving files into the app and back
│   ├── Timeline.kt         zoom levels, grouping by date, row layout
│   ├── MediaAccess.kt      permissions and "manage media"
│   ├── MediaStoreSource.kt reading the library in one query
│   ├── MediaRepository.kt  index, watching for changes
│   ├── Thumbnails.kt       thumbnails via MediaStore rather than decoding
│   └── Format.kt           sizes and numbers
├── ui/
│   ├── theme/              palette, typography, three independent theme knobs
│   ├── permission/         access screen
│   ├── photos/             timeline, tiles, scrollbar, date headers
│   ├── viewer/             full-screen viewing, video, details
│   ├── album/              one folder
│   ├── albums/             album list
│   ├── lock/               lock screen
│   ├── editor/             crop, colour, filters, markup, video trim
│   ├── trash/              deleted items and retention
│   ├── settings/           all settings, search in them, export and import
│   ├── common/             shared composables, pinch gesture
│   ├── Tab.kt              the four sections at the bottom
│   └── GalleryApp.kt       skeleton with bottom navigation
├── GalleryApplication.kt   dependency container
└── MainActivity.kt         the only activity, singleTask
```

## Build

```
./gradlew assembleDebug          # for development
./gradlew installRelease         # for daily use
```

Use the release build, not the debug one. Measured on a Galaxy S22 Ultra on the same timeline:
debug — 23 % janky frames on a fast fling, release — 5.6 %. Compose in debug is roughly ten
times slower: no R8, no profile-guided AOT. The release is signed with the debug key, so it
installs on top and needs no keys or passwords.

Needs JDK 17 or newer. On the development machine the one bundled with Android Studio works:
`JAVA_HOME=D:/AndroidStudio/jbr`.
