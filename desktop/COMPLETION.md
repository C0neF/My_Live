# Windows completion tracker

Working branch: `codex/portrait-live-playback`. User requested continued work until the Windows app is complete.
Validation should primarily use CLI builds, automated tests and logs; avoid Computer Use where possible.
Do not equate an individual completed stage with completion of the Windows app.

## Completed foundation

- [x] Shared Android/JVM platform protocols and signing scripts, KMP models, CMP UI foundations.
- [x] Four-platform recommendations, room search, URL/room navigation, pagination.
- [x] Native GPU playback, FLV support, quality/CDN fallback, fullscreen/HiDPI restoration, diagnostics.
- [x] Chat, video danmaku, cancellation and cleanup on room switches.
- [x] Durable local follows/history, aliases, filtering, restart recovery.
- [x] Android-compatible room JSON import/export and basic persistent player preferences.
- [x] Windows portable package, build workflow, Android regression checks.

## Completed implementation and acceptance

- [x] Categories and category rooms; anchor search and navigation (five controller tests).
- [x] Follow live-status refresh, filtering, automatic refresh; multiple groups, pinned follows and notes (five tests).
- [x] Windows-protected persistent credentials, account inspection/renewal and sign-out (8 tests, including actual DPAPI).
- [x] Bilibili QR login; desktop web login for Douyu/Douyin (9 service/controller tests and a real local WebKit/JS runtime test).
- [x] Player quality preference, hardware decoding, scaling, mute, keyboard controls and mini/on-top modes.
- [x] Danmaku size/speed/opacity/area, position controls, duplicate suppression, keyword/user shields and super-chat display.
- [x] Appearance, platform ordering, playback sleep timer and desktop lifecycle/tray behavior.
- [x] Full supported profile migration including tags/shields/preferences.
- [x] WebDAV backup/restore, interoperable local-network synchronization and remote sync rooms.
- [x] Diagnostics/export, version/about/update entry points.
- [x] Installer and portable artifacts, packaged-runtime checks, distribution notices/source information.
- [x] Single-file no-install EXE, cache reuse, concurrent extraction, argument/CWD forwarding and native playback acceptance.
- [x] End-to-end automated functional checks and native media/network evidence, plus Android regression.

Android-only hardware interactions (device rotation, touch brightness gestures, camera-based scanning and Android
permission/PiP services) need desktop equivalents where useful, rather than Android API emulation.
Actual third-party account authentication remains a human action; use isolated test credentials/fixtures for automation.
The user delegated the license choice: GPL-3.0-or-later is recorded in LICENSE/NOTICE.
GitHub publication, branch merging and pushing remain the user's decision. Prepare and validate locally.

## Evidence

Desktop optimization on 2026-11-12: session browse caches, restored list/category state, automatic pagination,
responsive search, resizable chat, mini-player actions, playback intent, a shared danmaku clock, bounded image
caches, parallel initialization and hidden startup are implemented. Tray and exit ownership live at the
application level so hidden windows can initialize and exit. Offline/unit/Compose checks and packaged
background self-checks passed; foreground window-switch acceptance remains incomplete and is disabled at
the user's request. Current scope and receipts are recorded in VERIFICATION.md.

The current local release candidate is tracked in RELEASE-ACCEPTANCE.md. It includes the
Android-shared application icon, a sidebar search page, a cards-only home page, 1318 passing
JUnit checks, an MSI-specific argument contract, pinned native source materials and release
bundle validation. Publication and branch merging remain the user's decision.

UI/UX follow-up on 2026-10-26: adaptive navigation rail/sidebar and room entry, responsive browse
and library tools, saved navigation/scroll state, mouse/focus feedback, scrollbars and recovery
states, idle player chrome, compact player menus, responsive chat sheets and uninterrupted chat
reading. Ctrl+L and Enter entry flows are covered. Windows checks pass 368 cases (218 core,
126 desktop, 21 UI, 3 native), with six opt-in network cases skipped. Current UI evidence and
delivery artifacts: ignored `build/windows-ui-ux-20261026/`.

Follow-up optimization on 2026-10-26: busy-room scrolling messages safely reuse lanes with equal
pixel velocity, long text enters from the right, fixed messages use expiry timers, and total
visible messages are bounded at 120. Hidden playback metrics stop polling. Single-file QA now
cleans its owned runtime copies on success and failure, preserving small evidence files.
The Windows checks pass 356 cases (218 core, 126 desktop, 9 UI, 3 native), with six opt-in online
cases skipped. The updated EXE and receipts are in ignored `build/windows-optimization-20261026/`.

Completed 2026-10-26. See `VERIFICATION.md` and ignored `build/windows-desktop-qa/final-2.1.1/`.
941 Android + 218 core + 121 desktop + 3 UI + 3 native tests pass (1286). Six opt-in online cases
are skipped in the normal run; separate real-platform runs pass 12 catalog/media/chat checks.
Android lint, QA build and instrumentation source compilation pass.

Final portable ZIP and administratively extracted MSI both pass runtime self-checks. Their app JARs
match the tested build. Actual packaged 2K120 and 4K60 window flows pass fullscreen/restore,
mini/restore, on-top, tray visibility and preserving user pause. Third-party authorization remains
a human action; actual paid/high-frame-rate account permissions were not claimed as verified.
The Windows workflow is configured; no remote publication was performed.

Single-file follow-up: `MyLive-SingleFile-2.1.1.exe` is 318449467 bytes and contains the same tested
application JAR. Its self-contained .NET runtime and apphost are pinned to 10.0.13. Four isolated
launch checks pass: cold start (7.9 s), warm start (3.4 s), and two concurrent cold starts (5.6 s each),
including Unicode/space/ampersand paths, relative media paths, disabled installed Java/.NET locations,
bundled notices and preserving a profile fixture. Actual single-file 2K120 and 4K60 window flows pass.
