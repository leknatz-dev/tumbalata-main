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

Throwers try to knock down a can with their slippers (the pambato); one Taya guards the can. 2 to 4 players: one is Taya, everyone else is a Thrower. A match ends on a timer and the highest score wins.

**Roles**

- **Thrower.** Each Thrower has their own slipper (tinted in their player colour). A Thrower who has not thrown yet must stay behind the throw line. After throwing, they may roam the whole map.
- **Taya (the guard, "it").** Roams the arena and guards the can on its base marker. Taya may only pick up the can when it has been knocked over, or to toss it when every Thrower missed.

**Round flow and role swaps**

1. Every round starts with all Throwers behind the line holding their slipper. They throw one at a time in turn order (P1, P2, P3; the first thrower rotates each round).
2. While nobody has hit the can, slippers stay where they land: Throwers who missed may roam but cannot pick up their slipper, and nobody can be tagged.
3. **Hit:** a slipper that passes within 25 px of the standing can knocks it down and starts the scramble. Throwers who already threw fetch their own slipper; Throwers who have not thrown yet may still throw (and knock the can down again once Taya stands it back up). Taya picks up the can and puts it back on its base.
4. **Everyone missed:** once all slippers have stopped, Taya picks up the can and must stay on that spot: Taya cannot move from picking up the can until it is tossed, so the toss always leaves from where the can stood. A can that lands within 30 px of a slipper makes that slipper's owner the new Taya. The can only hops a little when it lands (bounce 0.2).
5. **Toss missed:** the scramble starts: Throwers may now pick up their slippers and run home while Taya fetches the can and puts it back on its base.
6. **Tag:** during the scramble, once the can stands upright on its base (within 10 px), Taya can tag a Thrower who is past the throw line **and holding their slipper** (within 30 px). A Thrower without their slipper cannot be tagged; once picked up, a slipper cannot be put down, so the only way to be safe again is to cross back behind the line. The tagged Thrower becomes Taya.
6b. **Re-throw:** during the scramble, a Thrower who is home (behind the line, holding their slipper, after throwing) may throw again with A, e.g. to knock the can down so a teammate still out there can run home (Taya must stand it back up before tagging). A knock scores as usual; the re-thrower then has to fetch the slipper again.
6c. **Toss when every slipper is down:** during the scramble, if no Thrower holds a slipper, every slipper lies still on Taya's side of the line and the can stands on its base, it becomes Taya's toss turn (MY TURN!), exactly like after a full miss.
6d. **Head start after a missed toss:** when Taya's toss misses, Taya can't pick the can up for 3 s (`Match.CAN_LOCK_AFTER_MISS`; a 3-2-1 countdown shows over the can), so the Throwers get a free window to grab their slippers. This stops a Taya from tossing short on purpose to retry. It applies only after a toss, not after a knock.
**Intro dialogue** (see its own section): the kids call each other out to play, then the mano. Skippable by holding B.
**Mano ("maiba taya") before the match** (`Mano`, drawn by `GameScreen`): before GAME START the hands of all players sit in a ring over the blurred court and bob while a 3-second countdown runs. Each player picks **A = palm up** or **B = palm down** (no press = palm down). Then the hands flip: the one hand that differs from all the others (3/1 with 4 players, 2/1 with 3) is **Taya**, shown with "NAME IS TAYA!". 2/2 or all the same = AGAIN. With 2 players, different hands make the palm-up player Taya; same hands go again. After 5 agains a Taya is picked at random. Art: `assets/mano/back.png` (done: the round backing, 32 x 32 drawn 13x) and `hand_fist.png`, `hand_up.png`, `hand_down.png` (done: 36 x 36, white with transparent backgrounds, fingers pointing up, drawn 3x, turned to point into the ring and tinted per player), and `select.png` (done: the red starburst, 50 x 50 drawn 2.5x, spinning behind the Taya's hand). Under the ring the hint shows the button art: yellow = palm up (A), red = palm down (B).
**Choosing the can spot** (after the mano, if the map has a `can zone` layer): "CHOOSE CAN SPOT!" and the zone shows as a box on the ground (green when Taya stands inside it). Taya carries the can, walks anywhere in the zone and presses B to stand it there; only Taya moves and the clock is stopped. That spot is the can's base for the whole match (tosses and "put the can back" use it). Then GAME START. Without a `can zone` layer this step is skipped (`can base` layer or the centre circle).
7. After a swap, the old Taya becomes a Thrower and throws first in the new round.
8. The round ends when every Thrower is back behind the line holding their own slipper; the next round starts with the same Taya.

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

A countdown timer ends the match and opens the victory screen, where the highest score stands in the middle of the podium, largest. The timer is 40 seconds for testing. Points (values in `Scoring.java`): knocking the can down +3 to that Thrower; every Thrower who threw and gets home safe when a round ends +1; a tag +2 to Taya; a toss that lands on a slipper +2 to Taya. Scores show at the top left during play with "+N" pop-ups. Tied players share a place on the podium.

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
| Character select (4 cards) | Working, placeholder art | Each player has their own cursor; a character can only be locked by one player ("TAKEN") |
| Victory podium | Working, placeholder art | Uses random scores; winner in the middle and largest |
| Match timer | Working | 40 s test value |
| Screen transition (can sweep) | Working | Mask wipe, 8-frame sheet, 1.4 s |
| Fullscreen and window handling | Working | F11, size only changed when the player has not resized it |
| Controller layer (4 slots, all screens) | Delivered, unverified | Blocked by the owner's VS Code / Gradle import problems |
| Gameplay for 3 and 4 players | Not started | Rules undefined |
| Scoring system | Not started | Points rules undefined |
| Audio | Working, placeholder sounds | `Audio` class; 12 effects and 3 music tracks wired in, all synthesized placeholders (see Audio) |
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
| Player names | `NameEntryScreen` | 700 x 500 | One row per player. Keyboard: type, Enter = OK, Up/Down = row, Esc = back (the keyboard only types here). Pads: Up/Down = letter, Left/Right = move, A = OK, B = delete, Select = back. Names start blank every game (not kept); empty = "P1"... and used everywhere (tags, HUD, signs, victory, awards) |
| Character select | `CharacterSelectScreen` | 700 x 500 | Four cards; name tags above the card each player is on |
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
| Select | R | R | Select (button 4) | Pause / settings menu (Start or Esc / P too) |
| Start | none | none | Start (button 6) | Confirm in menus, pause in a match |

**Pause / settings menu** (`PauseMenu`): Select or Start on any player's pad, R (Select for P1 / P2 on the keyboard), Esc or P pauses the match, blurs the court and shows a square panel: RESUME, RESTART ROUND (what Select used to do), SOUND on/off, STREET EVENTS on/off, EXIT TO MENU. B / Esc / Select resumes. Placeholder look, all buttons work.
**Button prompts:** instead of text, the button art (`assets/redbutton.png`, `yellowbutton.png`, 18 x 18 drawn 1.5x, pulsing; drawn circles if missing) floats over the player who can press it now: **red = B** (pick up a slipper, pick up or put back the can, place the can on its spot) and **yellow = A** (start aiming, lock the aim, throw, re-throw, toss). The "NAME THROW!" sign waits for 0.5 s with no other sign and no hit-stop, so it is never lost behind RUN!, TAGGED! or a freeze.
**Menu hints** (`ControlHints`): each menu shows its controls with the button art and a drawn D-pad. Main menu: yellow = SELECT. Player count: yellow = PICK. Names: D-pad up/down = letter, right = next space, yellow = READY, red = UNREADY (plus a keyboard line). Character select: D-pad left/right = CHOOSE, yellow = LOCK IN, red = CHANGE.

Space and E also work on the second keyboard scheme, so one person can still test both roles alone. Actions are tied to the player whose turn it is: Thrower phases read the Thrower's input, Taya phases read Taya's.

**Menus**

Any controller or the keyboard can drive any menu. D-pad or WASD or arrows move the selection; A, Start, Enter, Space or E confirm; B, Select or Esc go back; F11 toggles fullscreen. The mouse also works (hover to select, click to confirm). Menu input is ignored while a screen transition is playing.

**Controller slots**

- Controllers take the first free slot as they connect, or in the order the system lists them at startup. Identical pads work because slots follow plug-in order, not the pad's name.
- Unplugging frees the slot; the player there stops moving until a pad is plugged back in.
- A `Player` owns its `PlayerInput`, so the input stays with the character when the Thrower and Taya swap roles.
- Slots 3 and 4 have no keyboard scheme; they are controller-only.

**Pad mapping**

Button numbers come from the pad's own mapping when the controller library recognises it; the owner's PS1 pads report A = 0, B = 1, Select = 4, Start = 6 (confirmed Oct 2026: the old fixed Select = 8 / Start = 9 never fired on them). Unrecognised pads fall back to A = 0, B = 1, Select = 8, Start = 9, D-pad on axes 0 and 1 with a 0.5 dead zone. The console prints the numbers in use for each pad when it connects. To change the numbers without recompiling, create `assets/controller.properties` with `a`, `b`, `select`, `start`, `axisX`, `axisY`, `invertY` and `deadZone`. Set `DEBUG_PRINT_BUTTONS` to true in `InputManager` to print each button number to the console.
**Pads the game doesn't see:** the controller library only accepts pads in its built-in list. Others are added through `assets/gamecontrollerdb.txt` (SDL mapping lines, loaded by the desktop launcher at startup). It already has the owner's DragonRise "USB Joystick" (`0079:0006`) with the same PlayStation-style layout as the PS1 pads. To find another pad's GUID, run `java -cp lwjgl3/build/libs/TUMBALATAEXPO-1.0.0.jar tools/PadProbe.java`. A pad Windows doesn't list at all (not in the probe, not in `joy.cpl`) is a hardware or USB problem. Player slots follow the order Windows lists the pads.

**Debug keys (game screen)**

F1 shows collision shapes and hitboxes, F2 cycles which layer is shown, and I / K / J / L nudge the ring by 1 px (Shift for 10 px) while F1 is on.

## Gameplay systems

`Match` (no graphics, unit-tested in `MatchTest`) runs the rules: a round mode (Throwing, Taya toss, toss flying, scramble), an aim state (angle, power) for whoever is aiming, tagging, scoring and the match timer. `Roster` decides who is Taya and the throwing order. `GameScreen` only loads assets, calls `match.update(delta)` each frame and draws what the match reports. `Match.Events` reports scoring, hits, tags and round ends for pop-ups, and later sound and effects.

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

**Court line and throw area (from Tiled):** the `courtline` object layer is the line between Throwers and Taya. It may lean with the perspective; the game uses its centre line (`CourtLine`), so "past the line" and "keep Throwers who haven't thrown behind the line" follow the drawing at every height. The `throw area` object layer is where Throwers spawn (pulled inside it and out of the walls). F1 shows both (green line, light blue area). Without these layers the old straight line at x 320 is used.
**Can base (from Tiled):** add an object layer named `can base` with one object (a point, small rectangle or ellipse); its centre is where the can stands each round and where Taya puts it back. Without the layer the can stands in the centre circle (704, 177).

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
**Font:** all text uses Pixelta (`assets/fonts/pixelta.ttf`) through `Fonts.create()`; screens size it with `setScale` as before (scale 1 = 16 px). It is rendered 3x and shrunk smoothly, so in-between sizes stay readable.

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
| `character.png` (or `char1.png` to `char4.png`) | any (the current one is 746 x 634) | **Done.** Character select: drawn **white** in each 110 x 110 character box, tinted in that character's colour and fitted to the box; the name and stat bars sit under the box. One `character.png` is used for every card without its own `charN.png` |
| `select_character_title.png` | any | Character select heading |
| `select_players_title.png` | any | Player count heading |
| Victory background | | **Done.** The same live map as the main menu (falls back to `menu_background.png`) |
| Victory podium figures | from the walk sheets | **Done.** Each player as their character: the front-facing standing frame, tinted like in the match; 1st place holds the slipper (in the player's colour) |
| `victory_title.png` | any | Victory heading |
| `slipper.png` | 16 x 16 (drawn 2x) | **Done.** Thrown slipper, white: tinted in the owner's player colour, spins while flying |
| `16x16 Walkwithslipper.png` slipper | in the sheet | **Done.** The held slipper is pure white (#FFFFFF) in the sheet; the game splits those pixels off and tints them in the player colour (the body keeps the character tint). Keep #FFFFFF only for the slipper |
| `arrow_default.png`, `arrow_sniper.png` | 36 x 36 (drawn 2x) | **Done.** Aim arrows, white, pointing up-right at 45 degrees; turned around the tail (`ARROW_*_TAIL` in `GameScreen`). SNIPER characters get the long one |
| `warning_sign.png` | 32 x 32 | **Done.** Bobs over each spot where trash is about to land |
| `trash/banana.png`, `box.png`, `apple.png` | 16 x 16 (drawn 2x) | **Done.** Street-event trash |
| `street/dog.png` | 512 x 432 | **Done.** 8 x 9 frames of 64 x 48, facing left; row 5 = walking, row 2 = sitting (while pooping) |
| `portraits/fast.png`, `strong.png`, `sniper.png`, `sneaky.png` | about 150 x 200 | **Done.** Intro dialogue portraits, white or light-grey shirt (tinted per player). See Intro dialogue |
| `dialogue/box.png` (optional) | any, 8 px corners | Intro dialogue box (nine-patch); a drawn box is used without it |
| `controller.properties` (optional) | text | Overrides for pad button numbers |

Per-character sprite sheets do not exist yet: the four characters are currently one walk sheet with a colour tint (see Characters in the architecture section).

## Intro dialogue

Before the mano, every match opens with a short Pokémon-style chat between the kids calling each other out to play (`DialogueScript` reads the script, `DialogueBox` draws it, stage `DIALOGUE` in `GameScreen`). The court is blurred behind it. Each line types out letter by letter with a voice blip (`sfx/dialogue_blip`, pitched per character). When one kid talks, the box covers only their half of the screen: P1/P3 on the left, P2/P4 on the right. When the speaker changes sides, the box slides over and the new portrait rises up from behind it before the text starts typing. The name plate is in the player colour. An `ALL:` line widens the box to full width and shows "EVERYONE" with every portrait in a row.

**Controls:** A finishes the line, then A again goes to the next one. Holding B for 0.8 s skips the whole dialogue (a bar fills along the bottom of the box). The button hints sit inside the box.

**Script** (`assets/dialogue/intro.txt`, plain text, edit freely):

```
# a note (ignored)
[3]                     a script for 3 players; several [3] sections = one is picked at random
P1: Uy {P2}! Laro tayo!  speaker P1..P4 or ALL, then the text
ALL: Maiba taya!
```

`{P1}`..`{P4}` become the players' names. Lines for a player who isn't in the game are dropped. If there is no script for the player count, the dialogue is skipped. Unreadable lines are skipped and logged, and `DialogueScriptTest` checks that the real file has scripts for 2, 3 and 4 players with no bad lines. The current scripts are Taglish, with 2 variants per player count, each ending "Maiba taya!".

**Portraits** (`assets/portraits/fast.png`, `strong.png`, `sniper.png`, `sneaky.png`, about 150 x 200 with a transparent background, drawn 1.6x): by character, not by player. The shirt must be **white or light grey**. The game finds it by filling up from the bottom of the picture through light, colourless pixels (around the outside too), and tints it in the player colour, keeping the folds. White up on the face (teeth, eyes, headband) is not touched. Faces and other colours stay as drawn.

**Box art** (optional, `assets/dialogue/box.png`): drawn as a nine-patch with 8 px corners. Without it the box is a white frame with a dark navy fill.

## Pop-up signs

Big signs pop up in the middle of the match screen (class `Signs`), one at a time: they spring in from nothing with an overshoot and a tilt wobble, float gently, then grow and fade out. Art goes in `assets/signs/` with the names below and is drawn **5x** (`Signs.IMAGE_SCALE`; the 81 x 37 signs become 405 x 185). Signs without art show big outlined placeholder text. Display times are in the `Signs.Sign` list. Done so far: game_start, run, haha, my_turn, gotcha, tagged, streak.

| File (`assets/signs/`) | Placeholder text | When it shows |
| --- | --- | --- |
| `game_start.png` | GAME START! | After the screen transition into the match. The match (and its timer) waits until the sign is gone. |
| `run.png` | RUN! | The can is knocked down while a Thrower is past the line, or Taya's toss misses |
| `haha.png` | HAHA! | The first knock of a round while every Thrower is safe behind the line |
| `my_turn.png` | MY TURN! | Everyone missed: Taya's turn to toss the can |
| `gotcha.png` | GOTCHA! | Taya's tossed can lands on a slipper |
| `good_job.png` | GOOD JOB! | Time is up (with the whistle); the victory screen follows when the sign is gone |
| `taya_picked.png` | NAME IS TAYA! | After the mano, in the new Taya's colour (the name is drawn under the art) |
| `choose_spot.png` | CHOOSE CAN SPOT! | After the mano, while Taya picks the can spot (no blur) |
| `tagged.png` | TAGGED! | Taya tags a Thrower |
| `streak.png` | STREAK x2! | The can is knocked again in the same round while everyone is safe (RUN! still wins if someone is past the line); the first knock shows HAHA! |
| `throw_turn.png` | NAME THROW! | Each time a new Thrower's turn starts (round start and after each throw), in that player's colour. Waits for any other sign to finish; no blur. With art, the player's name is drawn under the image |

## Match feel, street events, traits and awards

**Hit-stop, shake and blur** (`CourtEffects`, `BlurRenderer`; numbers at the top of `GameScreen`)
- Can knocked down: the action freezes for 0.07 s, then the camera shakes 3–9 px depending on how hard the slipper hit. A tag or a GOTCHA! toss gets a smaller version.
- While a pop-up sign is up, the court behind it blurs and darkens a little (`SIGN_BLUR_RADIUS`, 0 = off). The HUD and the sign stay sharp.
- Dust puffs at players' feet while running, a dust trail behind flying slippers, and dust behind the rolling can. Pooled, so cheap. Optional art: `assets/effects/dust.png`.

**Street events: trash** (rules in `Match` + `Trash`; switch on the player-count screen, saved)
- Every 15–25 s (first one after 8–14 s) a neighbour throws trash over the fence: a red warning circle for 1 s, then it flies in and lands. At most 3 on the court; each fades away after 20 s.
- Walking onto it: the player slides for 0.45 s, then is stunned for 1 s (stars over the head). No moving, picking up, throwing or tagging meanwhile. The trash is used up.
- A stunned Thrower can still be tagged; a stunned Taya can't tag. Slippers and the can pass over trash. Standing still on it does nothing.
- Placeholder shapes (banana peel, plastic bag, sardine can) until `assets/trash/banana_peel.png`, `plastic_bag.png`, `sardine_can.png` exist.

**Street events: stray dog** (rules in `Match` + `StrayDog`, same Street Events switch)
- 15–25 s into the match, then 20–35 s after its poop is stepped in, a stray dog barks and trots in from the left or right edge, stops at a random free spot, poops (1.2 s), and runs off the other side.
- Only one poop on the court at a time, and it never fades: it stays until someone walks into it. No new dog comes while it is there.
- Stepping in it: stunned on the spot for 1.5 s (no slide), same rules as trash (a stunned Thrower can still be tagged, a stunned Taya can't tag). Standing still on it does nothing.
- Art: `assets/street/dog.png` (animated sheet, see Assets) and `assets/street/poop.png` (16 x 16, drawn 2x). The can stands in the centre circle (704, 177).

**Character traits** (placeholder balance in `Characters`; multipliers on top of the role's base speed, so Taya keeps its own speed)

| Character | Trait | Effect |
| --- | --- | --- |
| CHAR 1 | FAST | Speed x1.12; aim meter x1.2 faster (harder to aim) |
| CHAR 2 | STRONG | Throw x1.15 (slippers fly faster, Taya tosses further); speed x0.92; aim meter x1.15 faster |
| CHAR 3 | SNIPER | Aim meter x0.75 speed (easier to time) and the long aim arrow; throw x0.9 |
| CHAR 4 | SNEAKY | Slipper pick-up reach x1.4; speed x1.05 |

The character select cards show the trait and SPD / PWR / AIM / REACH bars (half full = normal). The trait belongs to the character, so it stays with the player in both roles; the in-game HUD lists each player's trait next to their score.

**Can drawing:** the can on the ground is depth-sorted with the players by where it touches the ground, so a player standing behind it is hidden by it and a player in front covers it. While Taya carries it, it is drawn smaller at hand height (`CARRIED_CAN_Y`, `CARRIED_CAN_SCALE` in `GameScreen`) and has no separate shadow.

**End-of-match awards** (`Awards`, counted in `MatchStats`): shown one at a time under the podium. Only awarded if someone did it; ties share.
ASINTADO (most can knocks), BEST TAYA (tags + toss hits), TSINELAS CHAMPION (most safe runs), MADULAS (most slips on trash), MABAHO (stepped in dog poop the most), MALAS (caught the most).

## Audio

All sound goes through one `Audio` class, created once by `TumbalataGame` (`game.audio()`). Screens call `play(Audio.Sfx.X)` for effects and `playMusic(Audio.Track.X)` for music; music fades between tracks, and asking for the track that is already playing does nothing, so the menu music carries on across the menus. **M** mutes and unmutes anywhere. Master, music and effects volume and mute are saved in Preferences (`tumbalata`), ready for a settings screen.

Every file below is a synthesized **placeholder** (made by `tools/GenPlaceholderSounds.java`). To replace one, put a file with the same name in the same folder as `.ogg` (preferred), `.mp3` or `.wav`, and delete the placeholder if the extension differs. A missing file is logged and stays silent; `AudioAssetsTest` fails the build if a name the code uses has no file.

| File (`assets/audio/...`) | When it plays |
| --- | --- |
| `sfx/ui_move` | Menu selection moves (keys, pad or mouse hover), character cursor moves |
| `sfx/ui_confirm` | Menu button pressed, character locked in, leaving the victory screen |
| `sfx/ui_back` | Back to the previous menu, character unlocked |
| `sfx/ui_deny` | Trying to lock a character another player already has |
| `sfx/transition_roll` | The rolling can sweeping across on every screen change (about 1.4 s) |
| `sfx/game_start` | The match screen opens |
| `sfx/game_end` | Time is up (whistle) |
| `sfx/victory_fanfare` | The winner pops in on the podium |
| `sfx/throw` | A slipper is thrown or Taya tosses the can (slight random pitch) |
| `sfx/can_hit` | A slipper knocks the can down, or Taya's can lands on a slipper |
| `sfx/tag` | Taya tags a Thrower |
| `sfx/score` | Any points scored |
| `sfx/trash_land` | Street-event trash lands on the court |
| `sfx/slip` | A player slips on trash |
| `sfx/dog_bark` | The stray dog trots onto the court |
| `sfx/poop_squish` | A player steps in the dog's poop |
| `sfx/dialogue_blip` | Each pair of letters typed in the intro dialogue (pitched per character) |
| `sfx/sign_run`, `sign_haha`, `sign_my_turn`, `sign_gotcha`, `sign_throw_turn`, `sign_taya_picked`, `sign_choose_spot`, `sign_tagged`, `sign_streak` | Each pop-up sign plays its own sound when it appears (GAME START! uses `game_start`, GOOD JOB! uses `game_end`) |
| `music/menu` | Looping on the main menu, player count and character select; also quietly under the game music during a match (background layer at 20%, under the game music in front). Music defaults to 15% volume; each track has its own volume in `Audio.Track` for balancing real songs |
| `music/game` | Looping during the match (fades out at time up) |
| `music/victory` | Looping on the victory screen |

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
| 1 | The Thrower's x range is set to 20 to 300 (throw line minus 20) at start and on every round reset, and is never widened | High: the Thrower cannot fetch a slipper that lands past the line, and a tag needs the Thrower past the line, so tagging cannot happen | **Fixed (Oct 2026):** the active Thrower may cross the line once the slipper is thrown; bounds are restored on round reset |
| 2 | The 4-slot controller layer was written without access to the owner's pads and has not been run | Medium: unknown pad numbers or a compile error would block menus and the game | Run it with one pad, check the console button numbers, add `controller.properties` if needed |
| 3 | The owner's VS Code / Gradle import is failing (see Build section) | Blocks all testing in the IDE | Fix the import; `gradlew.bat lwjgl3:run` works as a fallback |
| 4 | ~~Only two `Player` objects~~ | **Fixed (Oct 2026):** `GameScreen` holds 2 to 4 players; `Roster` assigns 1 Taya and a turn order of Throwers | — |
| 5 | ~~No scoring~~ | **Fixed (Oct 2026):** points awarded by `Match` (values in `Scoring`), shown in the HUD and on the podium; ties share a place | — |
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
| 3. Content | Audio | Replace the placeholder files in `assets/audio` with real sounds and music (names in the Audio section); add a volume screen |
| 3. Content | Menu size decision (960 x 540) | If chosen: all menu art and layouts re-done at 960 x 540 and the code constants updated |
| 3. Content | Pause screen and settings | Pause on Start; volume and a way to check controller mapping |
| 4. Tech | Clean-up and packaging | One shared `MapCollision` class; unused dependencies and files removed; tests for the collision sweep and throw distance; a packaged Windows build |

## Open questions

The scoring rules and the 3 to 4 player rules block the most work, so they come first. Each row shows the assumption used until the owner decides.

| # | Question | Why it matters | Assumption until decided |
| --- | --- | --- | --- |
| 1 | How are points earned (can knocked down, successful tag, can landing on the slipper, time spent as Taya)? | Needed for scoring and the victory podium | **Decided:** knock the can +3, home safe +1, tag +2 (Taya), toss hits slipper +2 (Taya) |
| 2 | How do 3 and 4 players play together (rotating queue, several throwers against one Taya, teams, free-for-all)? | Decides roles, spawn positions, HUD and what the 3P and 4P buttons mean | **Decided:** 1 Taya vs all other players as Throwers, who take turns throwing (P1, P2, P3...). A tagged Thrower, or the one whose slipper Taya's can hits, becomes Taya and the old Taya throws next. Last player starts as Taya. |
| 3 | Should the Thrower be free to roam the whole arena after throwing? | Needed to fix the throw-line bounds | Yes, only until the round resets |
| 4 | Keep menus at 700 x 500 or switch to 960 x 540 (16:9)? | Decides the size of all menu art | 700 x 500 stays in the code |
| 5 | Match length: one timer, or best of N rounds? What is the final length? | Replaces the 40 s test timer | One 40 s timer |
| 6 | Does each player pick their own character, and may two players pick the same one? | Changes the character select screen | **Decided:** every player picks with their own cursor and locks in with A; a character already locked by another player cannot be picked |
| 7 | Are all four controllers the same model? | If not, each needs its own button profile | Same model, one default profile |
| 8 | Which platforms must it run on, and what is the delivery format (jar, Windows exe)? | Affects packaging and testing | Windows only |
