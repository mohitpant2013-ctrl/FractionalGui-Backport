# Fractional GUI Scale (Forge 1.16.5)

## Use
- **Slider** "GUI Scale" in the top-right of Video Settings: Auto, then 0.50x - 8.00x in 0.05 steps.
- **Keys** (in-game): `=` / `-` change by 0.25x, `\` resets to Auto. An action-bar message shows the value.
- **Config** `config/fracgui-client.toml` (live reload: save the file and it applies instantly):
  - `guiScale`        0 = Auto, otherwise 0.5 - 8.0
  - `smartAutoHeight` with guiScale = 0: scale = window height / this value (e.g. 540 -> 2.0x at 1080p, 4.0x at 4K). 0 = off
  - `excludedScreens` screens that use the vanilla integer scale while open
- **Broken modded GUI?** Open it and press `Ctrl+Alt+G`; press again to undo.

## Compatibility / safety
- Hooks `MainWindow.setGuiScale`, so vanilla and mods that set the scale themselves can't fight it.
- Works in windowed, fullscreen and borderless: the scale is recomputed on every framebuffer size change,
  plus a per-tick watchdog for mods that resize the window without firing vanilla's callback.
- Never crashes the game: mixin is optional (falls back to a tick-based method if it can't apply),
  config reads are guarded, and any internal error drops the mod to safe mode (vanilla scale) with a log entry.
- Capped so the scaled screen stays >= 320x240, like vanilla.

## Build
`./gradlew build` (needs network). Run: `./gradlew runClient`

## Borderless fullscreen mods
Tested design-wise against the usual 1.16.5 Forge ones (Borderless Window, Fullscreen Windowed (Borderless),
Borderless Mining): they only change the window (undecorated + monitor-sized), never the GUI scale, so they
don't touch the hook this mod uses. Every window/framebuffer size change re-runs the scale, and a watchdog
re-runs it if a mod resizes the window without vanilla's callback. Test: toggle F11 a few times and confirm the
slider value is unchanged.

## Build on GitHub
1. Create a new repo and push this folder as the repo root (build.gradle must be at the top level).
2. Open the **Actions** tab: the "Build" workflow runs on every push and uploads the jar as an artifact
   (`fracgui-jar`, the file in `build/libs/`; use the one without `-sources`).
3. To make a release: `git tag v1.0.0 && git push origin v1.0.0` - the jar is attached to a GitHub Release.

Local build (optional): install Gradle 7.6.x + JDK 8, then `gradle build`.
