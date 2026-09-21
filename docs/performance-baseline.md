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
