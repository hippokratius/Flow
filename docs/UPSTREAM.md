# Staying mergeable with upstream Flow

TubeHub is a fork of [Flow](https://github.com/A-EDev/Flow), which is actively developed. Upstream
owns the YouTube extraction stack — InnerTube, PoToken/BotGuard, signature deobfuscation, SABR —
and that is exactly the code that breaks when YouTube changes something. Staying able to merge
upstream fixes is therefore not housekeeping; it is what keeps the YouTube half of the app working.

## Remote setup

```bash
git remote add upstream https://github.com/A-EDev/Flow.git
git fetch upstream
```

## Merging

```bash
git fetch upstream
git switch -c merge/upstream-$(date +%F) main
git merge upstream/main        # merge, never rebase — rebase rewrites the fork's history
# resolve, build, test
git switch main && git merge --ff-only merge/upstream-$(date +%F)
```

Record the last merged upstream commit below when you merge.

| Date | Upstream commit | Notes |
|---|---|---|
| — | — | fork point |

## The rule that keeps this cheap

> **New files are free. Edits to existing upstream files are debt, repaid at every future merge.**

TubeHub code lives in packages upstream will never create, so it can never conflict:

- `io.github.aedev.flow.data.source` — the content-source abstraction
- `io.github.aedev.flow.data.source.youtube` — adapter onto the existing `YouTubeRepository`
- `io.github.aedev.flow.data.source.peertube` — PeerTube source
- `io.github.aedev.flow.fediverse` — Misskey/ActivityPub interaction
- `io.github.aedev.flow.player.source` — playback for non-YouTube sources
- `ui/components/VideoSourceBadge.kt`, `ui/screens/home/HomeFeedFederatedMerge.kt`,
  `ui/screens/settings/PeerTubeInstancesScreen.kt`
- `.github/workflows/tubehub-ci.yml`, `NOTICE.md`, `docs/UPSTREAM.md`, `res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`

Where an edit to an upstream file is unavoidable, it must be a **dispatch, not a rewrite**: one
branch, one binding, one appended field. Anything larger belongs in a new file that the upstream
file calls into.

### Deliberate non-changes

Three things look like obvious fork changes and are deliberately *not* done:

- **The package namespace stays `io.github.aedev.flow`.** Renaming it to `de.tubehub.app` would
  change the `package` line of all 727 source files, making every subsequent upstream merge
  conflict on every file upstream touches. Only `applicationId` changes — in Android the two are
  independent, and only `applicationId` is user-visible.
- **`YouTubeRepository` is not refactored.** Its public API leaks NewPipe types and ~30 files
  depend on that. `YouTubeContentSource` adapts it from the outside instead.
- **The InnerTube-versus-NewPipe race in `VideoPlayerViewModel` is not generalised.**
  `YouTubeContentSource.resolvePlayback` returns null on purpose; YouTube keeps its bespoke
  playback path and only new sources use `PlaybackSpec`. The abstraction is asymmetric by design.

## Current upstream edits

Keep this list short. If it grows, the seam has drifted.

| File | Edit |
|---|---|
| `app/build.gradle.kts` | `applicationId`, version reset, blanked Discord application id |
| `settings.gradle.kts` | `rootProject.name` |
| `README.md` | fork attribution banner |
| `app/src/main/res/values/strings.xml` | `app_name`, `app_name_uppercase`, appended TubeHub strings |
| `data/model/Models.kt` | two defaulted fields each on `Video` and `Channel` |
| `data/local/entity/VideoEntity.kt` | recompute `source`/`instanceHost` in `toDomain()` |
| `ui/screens/home/HomeViewModel.kt` | registry parameter, one `async` lane, two merge calls, three source guards |
| `ui/screens/player/VideoPlayerViewModel.kt` | dispatch branch for federated ids, history thumbnail guard |
| `player/EnhancedPlayerManager.kt` | SponsorBlock and `ServiceList.YouTube` guards |
| `utils/ThumbnailUrlResolver.kt` | non-YouTube ids keep their raw thumbnail |
| `ui/screens/settings/SettingsScreen.kt` | nav lambda, list row, search entry |
| `ui/FlowNavigation.kt` | nav lambda and one `composable` route |
| `ui/components/VideoCard.kt` | source badge in each of the four cards |
| `ui/components/VideoPlayerComponents.kt` | instance name under the subscriber count |
| `ui/screens/library/LibraryShelfCards.kt` | source badge |
| `ui/screens/history/HistoryScreen.kt` | source badge, kind derived from the id |
| `ui/NavigationDestinations.kt` | `youtubeChannelUrl` returns null for non-YouTube ids |
| `ui/screens/player/content/VideoInfoContent.kt` | Fediverse action bar for federated videos |
| `MainActivity.kt` | one early-return for the MiAuth callback |
| `AndroidManifest.xml` | MiAuth intent filter, backup exclusion rules |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | `androidx.browser` for Custom Tabs |
| `app/build.gradle.kts`, `.gitignore`, `app/debug.keystore` | fixed debug signing key |
| `res/values/strings.xml`, `res/values/colors.xml` | brand-name and icon-picker keys, plate colour |
| 8 Kotlin files | product name and icon-picker labels moved to untranslatable keys |
| 14 drawables under `res/drawable/` | upstream brand mark replaced by a neutral glyph |

### Why the debug key is committed

Gradle otherwise falls back to `~/.android/debug.keystore`, which is generated per machine. Every CI
runner is fresh, so each artifact would be signed with a different key and none would install over
the one before it. The committed key is Android's standard debug credential and carries no security
meaning; the release keystore stays ignored.

### Why the brand name lives in its own key

`app_name` and `app_name_uppercase` are translated in 23 and 18 locales. Changing only the default
leaves the upstream name showing on every non-English device, and editing locale files is both
forbidden here and reverted by Weblate. `app_brand_name` and `app_brand_name_uppercase` are declared
`translatable="false"` instead; the originals stay declared so their translations do not become
extra-translation lint errors. The three icon-picker labels that named the upstream project were
handled the same way.

### Why the artwork changed

Forking GPL code grants no rights to the project's trademark. The upstream mark appeared in fourteen
drawables — launcher variants, splash screens, the playback notification and the in-app badge — so
the app identified itself as Flow at every start. All now carry a plain play glyph. The
`activity-alias` entries and the icon picker are untouched: swapping the glyph covers every variant
at once and leaves no dead UI.

### Why those guards exist

Four upstream spots silently assume every video id is a YouTube id. They are cheap to fence off and
expensive to discover later, so they are guarded rather than worked around:

- `enrichChannelMetadataIfMissing` treats any channel id without a `UC` prefix as incomplete, and is
  invoked per rendered card — every federated card would fire a YouTube lookup on each scroll.
- `saveHistoryEntry` and `ThumbnailUrlResolver` fabricate an `i.ytimg.com` URL from the id when no
  thumbnail is known, writing a permanently broken link into the watch history.
- Graph seeds from the watch history are passed to YouTube's related-videos endpoint.
- `setStreams` requested SponsorBlock segments for every id, which for a federated video disclosed
  the instance host to a third-party server.

## Build

Flow declares `productFlavors { github, foss }`, so unqualified tasks do not exist:

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:compileFossDebugKotlin
./gradlew :app:lintGithubDebug      # lint has abortOnError = false — read the HTML report
```
