# Live Room Performance Baseline

This document defines the minimum measurement plan that must accompany further
hot-path changes. Architectural seams from the C1–C5 deepening work exist so
these measurements can drive real modules instead of Screen glue.

## Goals

- Establish a repeatable baseline for enter-room, danmaku load, and lifecycle
  transitions.
- Record whether a change improved, regressed, or did not move the needle.
- Prefer driving [LivePlaybackSession] and [LiveDanmakuReplaySource] over full UI
  orchestration when possible.

## Tools

| Tool | Use |
|---|---|
| Android Studio Profiler / Perfetto | Main-thread, RenderThread, CPU, jank frames |
| `adb shell dumpsys gfxinfo <pkg> framestats` | Quick frame buckets on device |
| [LiveRoomFrameMetricsRecorder] | In-process counters for scripted/local checks |
| Existing unit tests | Regression safety for policy and pure schedule logic |

Macrobenchmark is wired in `:settings.gradle.kts` so measurements can be reproduced from the same
revision as the app. Device execution remains manual, and performance claims still require the
evidence below.

## Required scenarios

1. **Cold enter live room** (home → first room, 5s)
2. **Steady danmaku / chat** (30–60s in a busy room, or replay fixture)
3. **Background / foreground** with background play off and on
4. **Portrait ↔ landscape / fullscreen**
5. **Leave room → Index return** (Index presence policy)

## Capture commands (device)

```powershell
# Replace SERIAL if needed
$pkg = "com.mylive.app"
adb shell dumpsys gfxinfo $pkg reset
# ... run scenario ...
adb shell dumpsys gfxinfo $pkg framestats
```

Perfetto:

1. Start system trace (app + frame + binder categories).
2. Run one scenario only.
3. Save `.perfetto-trace` outside the repo (or under a local ignored path).

## Recording template

```text
Date:
Device / API:
Build: versionName / commit
Scenario:
Baseline or change:
Janky frames %:
P50 / P95 frame time:
Notes (audio glitch, black frame, chat freeze):
```

## Acceptance for claiming a performance win

A PR may claim FPS/jank improvement only when:

1. Before/after numbers exist for the same device + scenario.
2. The changed code path is covered by a unit/instrumented test when feasible.
3. Evidence is linked (trace file path, framestats output, or metrics snapshot).

Structural refactors alone are **not** performance claims.

## Startup timing and sustained playback (2026-10-09)

The playback diagnostics now show two distinct clocks:

- **进房首帧（含请求）**: from `LiveRoomViewModel.openRoute` to Media3 rendering the first
  video frame, including room lookup, quality selection, source lookup and playback setup.
- **播放器首帧**: from preparing the current media source to its first rendered video frame.

Both use `elapsedRealtime`. The end timestamp comes from Media3's `renderTimeMs`, rather than
the time its callback is delivered. Media generations reject callbacks from a replaced source;
automatic retries before the first frame remain part of the room clock. Surface reattachment
does not replace an already recorded first-frame measurement. See the
[Media3 AnalyticsListener contract](https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener#onRenderedFirstFrame(androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,java.lang.Object,long)).

`liveRoomEnterExitFrames` waits for `player_video_rendered` before leaving the room.
`liveRoomSteadyPlaybackFrames` waits for the first frame, records a steady interval, and verifies
that a rendered source is still present at its end. Its default measured interval is 60 seconds;
`mylivePlaybackDurationMs` accepts 1 second through 30 minutes. Configure a currently live room
with `myliveLiveRoomRoute`; the historical default Bilibili room 6 may be offline.

Example for an already selected adb target (PowerShell):

```powershell
.\gradlew.bat :app:assembleBenchmark :macrobenchmark:assembleBenchmark
adb -s SERIAL install -r app/build/outputs/apk/benchmark/app-benchmark.apk
adb -s SERIAL install -r macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk
adb -s SERIAL shell pm grant com.mylive.app android.permission.POST_NOTIFICATIONS
adb -s SERIAL shell am instrument -w -r `
  -e class 'com.mylive.app.macrobenchmark.MyLiveMotionBenchmark#liveRoomSteadyPlaybackFrames' `
  -e mylivePlaybackDurationMs 900000 `
  -e myliveLiveRoomRoute '/room/detail/ROOM_ID?siteId=bilibili' `
  com.mylive.app.macrobenchmark/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL pull /sdcard/Android/media/com.mylive.app.macrobenchmark build/performance-run
```

The compilation warmup and setup may make wall-clock runtime longer than the measured interval.
Do not run another UIAutomator session while the benchmark owns the accessibility connection.
Emulator-only workflow verification may explicitly pass
`-e androidx.benchmark.suppressErrors EMULATOR`; do not apply that override to physical-device
acceptance or interpret emulator results as device improvements.

On 2026-10-09, a read-only Pixel 10 Pro XL emulator (API 37, SwiftShader) verified the QA
diagnostics and the benchmark flow using Bilibili room 5050. One QA startup measured 1451 ms
for the room clock and 430 ms for the source clock at 1280 × 720. A 60-second benchmark capture
recorded 1889 app/UI frames, with CPU frame duration P50 12.9 ms and P95 18.7 ms. These are
app/UI frame metrics, not the video's frame rate. The accepted capture and its JSON are stored
under `build/v3-phase2-20261009/emulator/benchmark-clean/`. This is a measurement baseline
for this environment, not a before/after comparison or a long-session acceptance result. Real
device weak-network, lifecycle, 15–30 minute playback and matched-version comparison remain open.

## Physical-device closure update (2026-10-23)

The later API 31 physical-device round completed natural PiP, screen-off recovery, both
background-play policies and a controlled twelve-second WiFi interruption. The final QA
played a Douyu 2K120 selection for 942.167 seconds and 2K60 for 349.629 seconds; session-end
diagnostics recorded no playback-phase rebuffer, decoder drop or audio incident in those
sessions. A separate 959.368-second 2K60 session belongs to the earlier QA build.

Decoder submission, danmaku and window presentation were checked separately. The 2K120
traces handled about 120 decoder outputs per second and released about 60.1 per second in
the selected 60 Hz display mode. A separate overlapping SurfaceFlinger sample covered
33.250 seconds at 59.459 main-window presentations per second, with a maximum interval of
33.833 ms. That window includes UI and TextureView composition and does not establish
unique video-frame cadence. Toggling danmaku proved that an earlier roughly 38 fps
SurfaceView sample was the danmaku layer; the original toggle state was restored.

These checks close the specified session and lifecycle cases, not a matched before/after
performance comparison. Deferred 4K playback, simultaneous physical devices and sustained
bandwidth congestion remain outside the tested scope. See the [V3 verification record](v3-progress.md).
