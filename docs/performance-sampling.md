# My_Live Performance Sampling

## Profile GPU Rendering

1. Install the benchmarkable build:

   ```powershell
   .\gradlew.bat :app:installBenchmark
   ```

2. On the device, open developer options and enable:

   `Profile GPU Rendering` -> `On screen as bars`

3. Exercise these flows:

   - Bottom tabs: `首页` -> `关注` -> `分类` -> `我的` -> `首页`
   - Enter a live room, then press back.
   - Live room tabs: `聊天` -> `关注` -> `设置`
   - Home/search/category site chips.

4. Read the bars:

   - The green horizontal line is the 16.67 ms frame budget for 60 Hz.
   - Repeated bars above the line indicate visible jank.
   - Occasional isolated bars can come from network/image work; repeated bars during the same transition are the target.

## Macrobenchmark

Run all motion benchmarks:

```powershell
.\gradlew.bat :macrobenchmark:connectedCheck
```

Run one benchmark:

```powershell
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mylive.app.macrobenchmark.MyLiveMotionBenchmark#bottomTabSwitchFrames
```

For emulator smoke tests only, suppress the emulator accuracy error at the command line:

```powershell
.\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.mylive.app.macrobenchmark.MyLiveMotionBenchmark#startupFrames" "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR"
```

Benchmark outputs are written under:

```text
macrobenchmark/build/outputs/connected_android_test_additional_output/
```

Use `FrameTimingMetric` P90/P95/P99 and the generated Perfetto traces to compare animation changes. Run on the same device, refresh rate, battery mode, and network condition when comparing two revisions.
