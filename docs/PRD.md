# Tumbalata – Product Requirements & Handover Document

As of Oct 5, 2026 · Owner: @AYSENGHAD

## Overview

Tumbalata is a 2D top-down arcade sports game for local multiplayer, built in Java with LibGDX and based on the Filipino street game Tumba Preso (Tumbalata). One player throws a slipper at a can, the other guards it, and roles swap when a throw or a tag succeeds. It runs on Windows desktop, played with up to four USB retro (Nintendo classic style) controllers, with a keyboard fallback for testing.

| Item | Detail |
| --- | --- |
| Name / package | Tumbalata, `ph.tumbalata.game` |
| Repository | github.com/leknatz-dev/tumbalata-main |
| Language / stack | Java 21, LibGDX 1.14.2, gdx-controllers 2.2.4, Tiled (TMX) maps |
| Build | Gradle wrapper 9.7.1, modules `core` and `lwjgl3` |
| Platform | Desktop (LWJGL3), window starts at 700 x 500 and is resizable |
| Players | 2 playable roles today (Thrower and Taya); 3P and 4P can be chosen but have no gameplay yet |
| Screens | Main menu, player count, character select, game, victory |
| Art status | Court, ring, walk sheets, buttons, title and transition are real; character cards and several headings are placeholders |

The goal of this document is to let anyone pick the project up without the original chat history: what the game is, what works, how the code is organised, and what to build next.

## Game concept and rules

The Thrower tries to knock down a can with a slipper (the pambato); Taya guards the can. A match ends on a timer and the highest score wins.

**Roles**

- **Thrower.** Stays behind the throw line while roaming. Picks up the slipper, aims, sets power and throws it at the can. After the throw, the Thrower must get the slipper back and return behind the line.
- **Taya (the guard, "it").** Roams the arena and guards the can on its base marker. When the can is hit, Taya picks it up, carries it back to the base and stands it upright.

**Round flow and role swaps**

1. Thrower throws. If the slipper lands within 25 px of the can, the can is knocked down and both players scramble: Taya returns the can to its base while the Thrower fetches the slipper.
2. If the slipper misses, Taya picks up the can, aims, sets power and throws it at the slipper on the ground. A can that lands within 30 px of the slipper swaps the roles.
3. A can that misses the slipper starts the retrieval phase: Taya puts the can back on its base while the Thrower tries to get home with the slipper.
4. Taya can tag the Thrower only when the can stands upright on its base (within 10 px) and the Thrower is past the throw line. A tag is within 30 px and swaps the roles.
5. The round resets when the Thrower is back behind the throw line holding the slipper.

**Interaction distances (current values)**

| Action | Distance |
| --- | --- |
| Pick up the slipper | 45 px |
| Pick up the can | 35 px |
| Place the can on its base | 35 px |
| Slipper hits the can | 25 px |
| Can lands on the slipper | 30 px |
| Tag | 30 px |

**Match and scoring**

A countdown timer ends the match and opens the victory screen, where the highest score stands in the middle of the podium, largest. The timer is 40 seconds for testing. There is no scoring system yet: the victory screen shows random placeholder scores (see Known issues and Open questions for what to decide).

## Current status

The 2-player game loop and all menu screens work. The newest piece, the 4-slot controller layer, is delivered but not yet confirmed on the owner's machine, and everything for 3 to 4 players is still to build.

| Area | Status | Notes |
| --- | --- | --- |
| Core 2-player loop (throw, scramble, tag, role swap) | Working | Keyboard-tested; state machine in the Gameplay systems section |
| Map, collision layers, upper ring | Working | TMX with collision1 and collision2; ring drawn over the players |
| Shadows (players, can) | Working | Soft ellipses; blending enabled |
| Can and slipper physics and wall collision | Working | Swept collision, exact can landing, tuned throw distance |
| Main menu with live background | Working | Real map with 4 characters walking behind the menu |
| Player count screen (3P / 4P) | Working | Real buttons; choice only affects the victory screen today |
| Character select (4 cards) | Working, placeholder art | Only Player 1's colour tint is applied in the game |
| Victory podium | Working, placeholder art | Uses random scores; winner in the middle and largest |
| Match timer | Working | 40 s test value |
| Screen transition (can sweep) | Working | Mask wipe, 8-frame sheet, 1.4 s |
| Fullscreen and window handling | Working | F11, size only changed when the player has not resized it |
| Controller layer (4 slots, all screens) | Delivered, unverified | Blocked by the owner's VS Code / Gradle import problems |
| Gameplay for 3 and 4 players | Not started | Rules undefined |
| Scoring system | Not started | Points rules undefined |
| Audio | Not started |  |
| Thrower to Taya swap animation | Not started | Currently an instant swap |
| Particles / game-screen effects | Not started |  |
| Controller disconnect notice | Not started | A player with no pad simply stops moving |

## Screens and game flow

Players move forward through three menu screens into the game; the timer ends the match and the victory screen returns to the main menu.

&#91;embedded content: screen flow · 5 screens\]

Every change goes through `TumbalataGame.changeScreen()`: the old screen's last frame is captured, the new screen is switched in underneath, and the can sheet sweeps across right to left. The old screen stays visible to the left of the can and the new screen appears behind it. The window is resized only after the sweep, and only if the player has not resized it.

| Screen | Class | Window / view | Notes |
| --- | --- | --- | --- |
| Main menu | `MainMenuScreen` | 700 x 500 | Live map background, title, Start and Exit with the slipper pointer |
| Player count | `PlayerSelectScreen` | 700 x 500 | 3P and 4P buttons; passes the count on |
| Character select | `CharacterSelectScreen` | 700 x 500 | Four cards; Player 1's pick is passed on |
| Game | `GameScreen` | 1280 x 698 window, 1408 x 768 view | Match with a timer |
| Victory | `VictoryScreen` | 700 x 500 | Podium: 2nd, 1st (middle, largest), 3rd, 4th; blocks rise, characters pop in |

## Controls and input

All input goes through one `InputManager`, created once by `TumbalataGame` and updated once per frame before the screen renders. It has four player slots (Player 1 to 4). Each slot merges a keyboard scheme and that slot's controller, so game code only reads one `PlayerInput` object per player.

**Gameplay buttons**

| Logical button | Keyboard P1 | Keyboard P2 | Retro pad | Used for |
| --- | --- | --- | --- | --- |
| Move | W A S D | Arrow keys | D-pad | Walk (diagonals normalised) |
| A | Space | Space, Enter | Button 0 | Start aiming, lock angle, lock power and throw |
| B | E | E, Right Shift | Button 1 | Pick up slipper or can, place can on base |
| Select | R | R | Button 8 | Reset the round |
| Start | none | none | Button 9 | Confirm in menus |

Space and E also work on the second keyboard scheme, so one person can still test both roles alone. Actions are tied to the player whose turn it is: Thrower phases read the Thrower's input, Taya phases read Taya's.

**Menus**

Any controller or the keyboard can drive any menu. D-pad or WASD or arrows move the selection; A, Start, Enter, Space or E confirm; B, Select or Esc go back; F11 toggles fullscreen. The mouse also works (hover to select, click to confirm). Menu input is ignored while a screen transition is playing.

**Controller slots**

- Controllers take the first free slot as they connect, or in the order the system lists them at startup. Identical pads work because slots follow plug-in order, not the pad's name.
- Unplugging frees the slot; the player there stops moving until a pad is plugged back in.
- A `Player` owns its `PlayerInput`, so the input stays with the character when the Thrower and Taya swap roles.
- Slots 3 and 4 have no keyboard scheme; they are controller-only.

**Pad mapping**

Defaults match the owner's USB retro pads: A = 0, B = 1, Select = 8, Start = 9, D-pad on axes 0 and 1 with a 0.5 dead zone, plus the pad's own D-pad buttons when it reports them. To change the numbers without recompiling, create `assets/controller.properties` with `a`, `b`, `select`, `start`, `axisX`, `axisY`, `invertY` and `deadZone`. Set `DEBUG_PRINT_BUTTONS` to true in `InputManager` to print each button number to the console.

**Debug keys (game screen)**

F1 shows collision shapes and hitboxes, F2 cycles which layer is shown, and I / K / J / L nudge the ring by 1 px (Shift for 10 px) while F1 is on.

## Gameplay systems

`GameScreen` runs the match as a finite state machine of ten phases (drawn below): Thrower roaming, selecting angle, selecting power, slipper flying, can-hit scramble, Taya waiting to pick up, Taya selecting angle, Taya selecting power, can flying, and retrieval. The Thrower and Taya are two `Player` objects whose references swap on a role swap; each keeps its own input and colour tint.

**Aiming and power**

- Thrower angle swings back and forth (a sine wave up to 80 degrees); Taya's angle spins through 360 degrees. A locks the angle, then a second press locks the power.
- Power oscillates between 0 and 100 with a sine wave.
- Slipper launch speed is power x 18 px/s, slowed by friction.
- Taya's can lands at a distance of `40 + 460 x (power / 100) ^ 1.5` px along the arrow. `Can.tossTo()` computes the launch speed (using the real air time and friction) so the first landing is exactly there. The can leaves from Taya's position at hand height (30), so its shadow follows the aim arrow.

**Physics and collision**

- The can has a pseudo-3D arc (height, gravity -850, bounce 0.55) and time-based ground friction, so its distance does not depend on frame rate.
- Slipper and can move along their path in 4 px steps, so fast throws cannot tunnel through thin walls. An object that starts inside a wall is nudged to the nearest free spot first, then swept.
- The slipper bounces off collision1; the can bounces off collision1 and collision2, in flight as well as on the ground, so it always lands inside the play area.
- Players slide along walls: movement is tried on X and Y separately against collision1.
- `Can` and `Slipper` also keep a built-in outer limit, set far outside the art, as a safety net.

**Timer and victory**

The match timer counts down in the top centre. At zero the game freezes, takes the scores (random while `fakeScoresForTesting` is true) and changes to the victory screen through the screen transition.

**Key tuning constants**

| Constant | Value | Where |
| --- | --- | --- |
| `PLAYER_SPEED` / `TAYA_SPEED` | 200 / 190 px/s | `GameConstants` |
| `SLIPPER_FRICTION` | 2.2 (applied x 0.8) | `GameConstants`, `Slipper` |
| `BOUNCE_DAMPING` | 0.5 | `GameConstants` |
| `MATCH_TIME_SECONDS` | 40 s (test value) | `GameScreen` |
| `CAN_MIN_THROW_DISTANCE` / `CAN_MAX_THROW_DISTANCE` | 40 / 500 px | `GameScreen` |
| `CAN_POWER_CURVE` | 1.5 | `GameScreen` |
| `CAN_RELEASE_HEIGHT` | 30 | `GameScreen` |
| Player hitbox | 14 x 10 px at the feet | `GameScreen` |
| Can hitbox / slipper hitbox | 16 x 16 / 12 x 12 px | `GameScreen` |
| `CAN_WALL_BOUNCE` / `SLIPPER_WALL_BOUNCE` | 0.5 / 0.6 | `GameScreen` |
| Shadow size and alpha | 28 x 10 px, 0.30 | `Player` |

Note: `GameConstants.CAN_FRICTION` (1.8) is not used; the can's friction lives inside `Can` (0.95 per 60 fps frame, made time-based).

## Map, rendering and coordinates

The court art is bigger than the Tiled grid, and the whole game is built around that. The TMX is 40 x 22 tiles of 32 px (1280 x 704), but the court art (an Image Layer) and `upperring.png` are 1408 x 768. The art starts at the TMX's top-left, so it hangs 64 px below the grid and 128 px to its right.

**Coordinates**

- LibGDX is y-up with the origin at the TMX's bottom-left. The art therefore spans x 0 to 1408 and y -64 to 704.
- The game camera shows exactly that region: `VIEW_X = 0`, `VIEW_Y = -64`, `VIEW_W = 1408`, `VIEW_H = 768`, fitted with a `FitViewport`.
- `upperring.png` is drawn at (0, -64), after the players and the can, so characters walk behind the ring.
- Gameplay positions still use the 1280 x 704 grid: the throw line is at x = 320 (25% of the width), the can base at (1024, 352), and Taya is clamped to x 20 to 1260 and y 20 to 684, and the Thrower to x 20 to 300 (see Known issues).
- The thrower spawns at (270, 352) and Taya at (1104, 352).

**Tiled map (`MAPCOLLISION.tmx`)**

| Layer | Type | Purpose |
| --- | --- | --- |
| Image Layer 1 | Image layer | Court art, 1408 x 768 |
| collision1 | Object layer | Walls for the players, the slipper and (by default) the can |
| collision2 | Object layer | Extra walls for the can only |

- Collision shapes can be rectangles (rotation is supported) or convex polygons. Ellipses, polylines and tile objects are skipped, and the console logs each skipped object.
- The loader reads every layer with the matching name, including layers inside groups. Layer offsets must be 0 in Tiled, because object offsets are not applied.
- Shapes may extend outside the grid (the border walls do); that is intended.

**Windows, viewports and scaling**

- The window opens at 700 x 500 and is resizable. Menu screens use a 700 x 500 `FitViewport`; the game screen uses 1408 x 768. Letterbox bars appear when the window shape differs.
- The screen transition draws on its own camera over the whole window, so it also covers the bars.
- `TumbalataGame` owns window sizing: it switches to 1280 x 698 for the game and back to 700 x 500 for menus, but only when the window is still at the size it set itself (not fullscreen, not maximized or resized by hand).
- A decision is pending on moving the menus to 960 x 540 (16:9), which scales exactly 2x at 1920 x 1080 with no side bars. The game art stays 1408 x 768, which scales about 1.36x at 1080p.

**Live menu background**

`MenuBackdrop` loads the same TMX for the menus: camera centred at (704, 320) with zoom 1.5, four characters (the walk sheet tinted per character) wandering inside x 260 to 1130, y 20 to 420, avoiding collision1, with shadows and the ring on top. One instance is shared by the main menu, player count and character select screens and released when the game starts.

## Assets

All assets live in the `assets/` folder and are loaded by file name, which is case-sensitive on Linux and macOS. Screens that use optional images draw a placeholder when the file is missing, so nothing crashes while art is still being made.

**Existing assets**

| File | Size | Used by | Notes |
| --- | --- | --- | --- |
| `MAPCOLLISION.tmx` | 40 x 22 tiles of 32 px | Game, menu background | Layers: Image Layer 1, collision1, collision2 |
| `mapcourt.png` | Court art, 1408 x 768 expected | TMX Image Layer | Check the TMX if renamed |
| `upperring.png` | 1408 x 768 | Game, menu background | Drawn at (0, -64) over the characters |
| `16x16 Walk-Sheet.png` | 4 x 5 frames of 16 x 16 | `Player`, menu background | Rows: down, down-diagonal, side, up-diagonal, up; left is a flipped right; drawn at 48 x 48 |
| `16x16 Walkwithslipper.png` | same layout | `Player` | Holding the slipper |
| `Walkwithcan.png` | same layout | `Player` | Carrying the can |
| `can_spin_sheet.png` | 4 frames in a row | `Can` | Drawn at 24 x 36 |
| `menu_title.png` | any | Main menu | Drawn at native size with a bobbing effect |
| `start_button.png`, `exit_button.png` | 100 x 50 | Main menu |  |
| `menu_slipper.png` | any | All three menus | Horizontal slipper used as the pick indicator |
| `3p_button.png`, `4p_button.png` | 144 x 100 | Player count screen |  |
| `menu_background.png` | 700 x 500 | Menus | Only used if the live background cannot load |
| `can_transition.png` | 2112 x 540 | Screen transition | Current code splits it into 8 frames |
| `libgdx.png`, `player_walk.png` | n/a | Nothing found in code | Template leftover and an unused sheet; confirm before deleting |

**Still to make (placeholders today)**

| File | Size | Screen |
| --- | --- | --- |
| `char1.png` to `char4.png` | 110 x 150 | Character select cards |
| `select_character_title.png` | any | Character select heading |
| `select_players_title.png` | any | Player count heading |
| `victory_background.png` | 700 x 500 | Victory screen (falls back to `menu_background.png`) |
| `victory_title.png` | any | Victory heading |
| `controller.properties` (optional) | text | Overrides for pad button numbers |

Per-character sprite sheets do not exist yet: the four characters are currently one walk sheet with a colour tint (see Characters in the architecture section).

## Build, run and environment

**Requirements**

- JDK 21 (the project targets Java 21; Gradle can download a JDK if it has internet).
- Gradle wrapper 9.7.1 is in the repo, so no separate Gradle install. The first run downloads Gradle and the LibGDX libraries and needs internet.
- VS Code with the Extension Pack for Java and Gradle for Java (the project's IDE so far).

**Run from the project root** (the folder with `gradlew.bat`, `core` and `lwjgl3`):

```
.\gradlew.bat lwjgl3:run
.\gradlew.bat build
```

The `lwjgl3` run task uses the `assets` folder as its working directory. Gradle also generates `assets/assets.txt` (a list of asset files) on build.

**Modules**

- `core`: all game code. Depends on LibGDX, gdx-controllers-core, box2d, freetype and box2dlights (box2d and freetype are declared but not used).
- `lwjgl3`: desktop launcher (`Lwjgl3Launcher`, window 700 x 500, resizable, 4x MSAA) and the desktop controller backend.

**Errors seen on the owner's machine and their fixes**

| Error | Cause | Fix |
| --- | --- | --- |
| `Could not find or load main class ...Lwjgl3Launcher` | The project did not build, so no launcher class exists | Run the game with `gradlew.bat lwjgl3:run` to see the real error |
| `Resource already exists on disk: .../bin/main/can_spin_sheet.png` | VS Code's build state is out of sync with disk | Close VS Code, delete `bin` and `build` folders (not `assets`), run Java: Clean Java Language Server Workspace, restart |
| `The import com.badlogic cannot be resolved` on every file | The Gradle import failed, so no libraries are on the classpath | Run `gradlew.bat build` with JDK 21 and internet, then clean the workspace and let the import finish |
| `.\gradlew` is not recognised | The terminal is in the wrong folder | `cd` to the folder that contains `gradlew.bat` |

If the Gradle import keeps failing, check that two copies of the same asset do not exist under `assets/`, since the `lwjgl3` module adds that folder as a resource directory.

## Known issues and risks

The most important gap is that the Thrower can never cross the throw line, so two core rules cannot happen yet. The rest are smaller or are decisions waiting on the owner.

| # | Issue | Impact | Suggested fix |
| --- | --- | --- | --- |
| 1 | The Thrower's x range is set to 20 to 300 (throw line minus 20) at start and on every round reset, and is never widened | High: the Thrower cannot fetch a slipper that lands past the line, and a tag needs the Thrower past the line, so tagging cannot happen | Widen the Thrower's bounds to the full arena once the slipper is thrown, and restore them in the round reset |
| 2 | The 4-slot controller layer was written without access to the owner's pads and has not been run | Medium: unknown pad numbers or a compile error would block menus and the game | Run it with one pad, check the console button numbers, add `controller.properties` if needed |
| 3 | The owner's VS Code / Gradle import is failing (see Build section) | Blocks all testing in the IDE | Fix the import; `gradlew.bat lwjgl3:run` works as a fallback |
| 4 | `playerCount` (3 or 4) is only passed to the victory screen; there are still only two `Player` objects | Medium: 3P and 4P look selectable but play as 2P | Needs the 3 to 4 player rules (open question), then new `Player` objects on input slots 3 and 4 |
| 5 | No scoring: the victory screen uses random scores while `fakeScoresForTesting` is true | Medium | Define how points are earned, fill the `scores` array, set the flag to false |
| 6 | Character choice only tints Player 1's sprite; there are no per-character sheets | Low | Replace the tint with per-character sheets when the art exists |
| 7 | Gameplay positions (throw line at 25%, can base at 80% x / 50% y, spawns) are fractions of the 1280 x 704 grid, not measured on the painted art | Low | Tune against the final court art |
| 8 | `GameConstants.WORLD_WIDTH/HEIGHT` (1280 x 720) and `CAN_FRICTION` are unused and differ from the game screen's own values | Low (confusing) | Remove or align |
| 9 | The collision loader exists twice (`GameScreen` and `MenuBackdrop`) | Low (maintenance) | Extract one shared `MapCollision` class |
| 10 | A controller unplugged mid-match leaves that player standing still with no notice | Low | Show a "controller disconnected" message and pause |
| 11 | The game scales 1.36x at 1920 x 1080 (not a whole number) and the menus are 7:5, so they get side bars in fullscreen | Low: possible slight blur, side bars | Decide on 960 x 540 menus (open question) |
| 12 | Unused dependencies (box2d, freetype, box2dlights) and leftover files (`libgdx.png`, `player_walk.png`, `Main.java`) | Low | Clean up when convenient |
| 13 | No automated tests, settings or save data | Low | Out of scope for now |

**Risks**

- Pad button numbers can differ between the four pads if they are not all the same model; per-controller profiles would then be needed (the profile class already exists per controller).
- The screen transition's seam depends on the art: if the sheet is not solid at `SEAM_POSITION`, a hard edge between old and new screen shows during the sweep.
- The `FRAME_COUNT` in the transition must match the sheet, or frames look cut.

## Roadmap

Work in this order: unblock and verify first, then finish the 2-player match, then build 3 to 4 players, then content and polish. No dates are set yet.

| Phase | Work item | Acceptance criteria |
| --- | --- | --- |
| 0. Unblock | Fix the VS Code / Gradle import | The game starts from the IDE run button and from `gradlew.bat lwjgl3:run`; no `com.badlogic` errors |
| 0. Unblock | Verify the controller layer with one real pad | Menus, character select and both game roles are playable with the pad; the console shows `Player 1 controller: ...`; unplugging does not crash; button numbers confirmed or set in `controller.properties` |
| 0. Unblock | Let the Thrower cross the throw line after throwing (issue 1) | The Thrower can fetch a slipper past the line; Taya can tag the Thrower only with the can upright on its base; the round resets when the Thrower is back behind the line holding the slipper |
| 1. Core match | Scoring system | Points rules agreed; `scores[]` filled during play and shown in a HUD; `fakeScoresForTesting` set to false; ties handled on the victory screen |
| 1. Core match | Real match length and end conditions | `MATCH_TIME_SECONDS` set to the chosen value; a clear "time up" moment before the transition |
| 1. Core match | Controller disconnect handling | A visible notice when a pad drops, and the match pauses or the player is clearly marked |
| 2. 3 to 4 players | Rules for 3P and 4P (open question) | Written rules for roles, turns and scoring with more than two players |
| 2. 3 to 4 players | Players 3 and 4 in the game | New `Player` objects use input slots 3 and 4; spawn positions, HUD and victory podium show all players; 4 pads work at once |
| 2. 3 to 4 players | Per-player character pick | Each joined player picks a character on the select screen, with its own cursor |
| 3. Content | Real character art | Four character sheets replace the colour tint; character cards, headings and victory art in place |
| 3. Content | Thrower to Taya swap animation and game-screen effects | The swap is no longer instant; a short effect plays; dust and impact particles on the can and slipper |
| 3. Content | Audio | Sounds for throw, can hit and bounce, swap, tag, menu and transition |
| 3. Content | Menu size decision (960 x 540) | If chosen: all menu art and layouts re-done at 960 x 540 and the code constants updated |
| 3. Content | Pause screen and settings | Pause on Start; volume and a way to check controller mapping |
| 4. Tech | Clean-up and packaging | One shared `MapCollision` class; unused dependencies and files removed; tests for the collision sweep and throw distance; a packaged Windows build |

## Open questions

The scoring rules and the 3 to 4 player rules block the most work, so they come first. Each row shows the assumption used until the owner decides.

| # | Question | Why it matters | Assumption until decided |
| --- | --- | --- | --- |
| 1 | How are points earned (can knocked down, successful tag, can landing on the slipper, time spent as Taya)? | Needed for scoring and the victory podium | Not decided; random scores are shown |
| 2 | How do 3 and 4 players play together (rotating queue, several throwers against one Taya, teams, free-for-all)? | Decides roles, spawn positions, HUD and what the 3P and 4P buttons mean | Still two active players; extra players are unused |
| 3 | Should the Thrower be free to roam the whole arena after throwing? | Needed to fix the throw-line bounds | Yes, only until the round resets |
| 4 | Keep menus at 700 x 500 or switch to 960 x 540 (16:9)? | Decides the size of all menu art | 700 x 500 stays in the code |
| 5 | Match length: one timer, or best of N rounds? What is the final length? | Replaces the 40 s test timer | One 40 s timer |
| 6 | Does each player pick their own character, and may two players pick the same one? | Changes the character select screen | Only Player 1 picks; duplicates allowed |
| 7 | Are all four controllers the same model? | If not, each needs its own button profile | Same model, one default profile |
| 8 | Which platforms must it run on, and what is the delivery format (jar, Windows exe)? | Affects packaging and testing | Windows only |
