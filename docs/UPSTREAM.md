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
- `.github/workflows/tubehub-ci.yml`, `NOTICE.md`, `docs/UPSTREAM.md`

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
| `app/src/main/res/values/strings.xml` | `app_name`, `app_name_uppercase` |
| `data/model/Models.kt` | two defaulted fields each on `Video` and `Channel` |
| `data/local/entity/VideoEntity.kt` | recompute `source`/`instanceHost` in `toDomain()` |

## Build

Flow declares `productFlavors { github, foss }`, so unqualified tasks do not exist:

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:compileFossDebugKotlin
./gradlew :app:lintGithubDebug      # lint has abortOnError = false — read the HTML report
```
