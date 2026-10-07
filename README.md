# Tumbalata

[![Build](https://github.com/leknatz-dev/tumbalata-main/actions/workflows/build.yml/badge.svg)](https://github.com/leknatz-dev/tumbalata-main/actions/workflows/build.yml)

A 2D top-down arcade sports game for local multiplayer, based on the Filipino street game Tumba Preso. One player throws a slipper at a can, the other guards it, and roles swap on a hit or a tag. Built with Java 21 and LibGDX, played with up to four USB controllers.

See [docs/PRD.md](docs/PRD.md) for the full design, rules, architecture and roadmap.

## Quick start

Requires JDK 21+ and internet on the first run (Gradle downloads itself and the libraries).

```
.\gradlew.bat lwjgl3:run      # play
.\gradlew.bat build           # compile and run tests
.\gradlew.bat lwjgl3:jar      # runnable jar in lwjgl3/build/libs
```

Every push to `main` and every pull request is built by GitHub Actions. The runnable jar from each run is attached to the run page under **Artifacts**.

## Repository layout

| Path | What goes here |
| --- | --- |
| `core/` | All game code (`ph.tumbalata.game`) |
| `lwjgl3/` | Desktop launcher and packaging (Construo) |
| `assets/` | Only files the game loads at runtime. Names are case-sensitive. |
| `art-src/` | Source files that are not shipped: `.ase`, `.psd`, raw audio, the Tiled project |
| `docs/` | PRD, rule decisions, playtest notes |

## Git LFS

Source art (`*.ase`, `*.aseprite`, `*.psd`, `*.kra`) and anything under `art-src/` that is a PNG or raw audio is stored with Git LFS. Runtime files in `assets/` stay in normal git, so building never needs LFS. Run this once per machine:

```
git lfs install
```

## Workflow

- `main` always builds. Work on a short branch (`feat/…`, `fix/…`, `art/…`, `chore/…`) and merge through a pull request.
- Commit messages start with a type: `feat:`, `fix:`, `art:`, `audio:`, `docs:`, `chore:`, `refactor:`, `test:`.

## Editing the map

Open `art-src/tumbalata.tiled-project` in Tiled. It shows the `assets/` folder; edit `MAPCOLLISION.tmx` there. Collision layers are `collision1` and `collision2`, and layer offsets must stay at 0.
