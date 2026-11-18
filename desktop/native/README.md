# Windows OpenGL video transport

The JNI sources in `mediamp/` are based on MediaMP commit
`d7d4d9e81979cb0549e55805adb64f3cb1c1e689` (Apache-2.0).
MyLive adds `wgl_video_ring` and the GPU transport exports in
`render_opengl_win.cpp`. The original copy-back path remains available when
`WGL_ARB_render_texture` is unavailable. The producer context does not depend on
the Compose window and survives fullscreen and mini-player changes.

Each player has three RGBA8 pbuffers. Only the producer renders. A consumer claims
a completed buffer, binds its storage to a Skia texture, and returns it after a
GPU fence completes. Resizing retires the old buffers until the consumer releases
them. Regular playback never reads these rendered frames back into CPU memory;
explicit screenshots request a readback on the producer thread.

The `include/mpv` headers describe the bundled mpv ABI, including MediaMP's D3D11
render extension. Khronos GL/WGL headers retain their original license notices.
Build through `:desktop:buildWindowsVideoBridge`; this requires Visual Studio C++
build tools and the same JDK headers used for the desktop application.
