# AUTISM PvP Addon

Sword PvP micro-automations for [AUTISM Client](https://autismclient.com) for Minecraft 26.2. The modules are deliberately "legit-looking": they only ever do things a real player can do (key presses, opening the inventory, real container clicks), with human randomized timing.

## Features

| Module | What it does |
| --- | --- |
| **Auto WTap** | Briefly taps W (or sneak) right after a landed hit to push your opponent back further. |
| **Auto JumpReset** | Jumps the moment you get hit to reduce knockback. |
| **Reach** | Extends your attack and block-interaction range up to 40.0 blocks via a mixin on `Player#entityInteractionRange` / `Player#blockInteractionRange`. Default **3.05** — Grim simulates combat and flags attacks at ~3.06+ blocks. |
| **Legit AutoTotem** | Opens your real inventory and swaps a totem into the offhand using genuine container clicks (`ServerboundContainerClickPacket`) — no bare item-change packets, so anticheat sees normal inventory interaction. |
| **BetterAutoClicker** | Clicks at your tuned **1.8-style CPS** range — one slider bar with **two handles** (min/max, e.g. 10–13). Toggle **Attack with KillAura** on to auto-click while the client's KillAura is targeting (its aim stays with KillAura), or off to run it as a classic autoclicker that only clicks while you hold the attack button. Suppresses the host clickers via `MC.missTime` so the total rate is exactly the slider. Only clicks once the target is actually under your crosshair (never during KillAura's rotation snap), caps the click reach at 3.2 blocks, and jitters every interval so it never looks machine-timed. |
| **Auto Critout** | Hops right before a swing so hits on a stationary target land as crits. Works together with BetterAutoClicker (both modes): it asks to hop before each click and BetterAutoClicker holds the swing until the hop lands. |
| **Quick Config** | Applies a real-MCTIERS gamemode preset (**Vanilla / UHC / Pot / NethOP / SMP / Sword / Axe / Mace**) across the addon's combat modules, scaled by a **Blatantness** slider (0–100) that stays realistic even at the top. Stays pinned to the top of the addon's category column. |
| **Velocity** | Scales the knockback you take (horizontal + vertical %) via a mixin on the motion packet (`Entity#lerpMotion`) for modern multiplayer knockback, with the singleplayer `LivingEntity#knockback` mixin kept for offline play. Two modes: **Reduce** (Vape-style — keep most knockback, default 85%/90%) and **Jump Reset** (Grim-safest — keep 100% and jump the instant the knockback lands, a vanilla mechanic Grim reads as a legit jump instead of reduced velocity). |
| **KnockbackDelay** | Vape v4-style: holds your incoming knockback motion packets for a short window and releases them **late** (instead of reducing them), so you stay in place and keep hitting while the opponent is still being pushed away — which reads as extra reach on weak servers. Only engages while a valid target is near your crosshair (chance-gated) and skips liquids. Pairs with **Velocity** (delayed *and* reduced). |
| **SpearKill** | Blatant trident spammer — the one addon module that is deliberately not "legit-looking": toggle it on, hold right-click with a trident in hand, and it snaps your aim onto the nearest player/mob and keeps charging + releasing the spear at a machine-steady rhythm for as long as you hold the button. Pairs with **Velocity** when you use a Riptide trident (the launch sends you at the target). |
| **Auto Strafe** | Jump-strafe assist: while you are airborne it holds the strafe key (plus W) for the classic strafe-circle around the enemy during fights. Direction can be a fixed side, a random side per jump, or **Around target**, which reads the enemy's bearing and strafes toward their side so you orbit them mid-fight. Keys are restored to their real physical state the moment strafing stops, so your controls are never locked. |
| **Backtrack** | A "delay adder" (Vape v4 style): while on, your outgoing position packets are held and released N ms **late**, so the server still sees you where you were a moment ago while you have already moved — which reads as extra reach when you close the gap mid-fight. Weak-server module (delayed position packets will flag on Grim). Pairs with **Velocity** + **KnockbackDelay**. |
| **Crystal Macro** | Hostile macro (explicitly not "legit-looking", like SpearKill): **bind a key and hold it** — while held it places an end crystal on the block under your crosshair, waits a short detonate delay, then attacks the crystal to blow it up, on a steady loop. Auto-selects the crystal from your hotbar and can switch back to a sword after every detonation. |
| **Anchor Macro** | Hostile macro: **bind a key and hold it** — while held it places a respawn anchor on the block under your crosshair, charges it with glowstone, then breaks it so it explodes, on a loop. Auto-selects anchor/glowstone and can switch back to a sword. |
| **Crystal Aura** | Fully automatic crystal combat: finds the nearest player/mob, picks a solid block beside them that can hold a crystal, places one there and detonates it on a steady, humanized cycle. Avoids spots too close to you, keeps the crystal selected automatically, glides your aim instead of snapping, and switches back to a sword between hits. Hostile. |
| **CrystalWarp** | The self-launch escape: **bind a key and hold it** — it places an end crystal at your own feet (on the block you're standing on), jumps, and detonates it so the explosion rockets you upward/away to get out from under pressure. Loops while held. Hostile. |
| **Litematica Printer** | Reads the currently loaded **Litematica** schematic world (via cached reflection into Litematica's `SchematicWorldHandler`) and places every block that is missing around you — real `useItemOn` placements, hotbar item selection, and real inventory swaps (genuine container clicks) when the block is not in the hotbar. Needs Litematica installed alongside the client and a schematic world loaded. |
| **Screenshare Bypass** | The staff-screenshare panic button: **double-tap the module's keybind** and the entire client hides instantly (all modules, the whole HUD, chat/packet output) via the client's built-in Panic Mode. **Double-tap again** (the secret gesture) and everything comes back exactly as it was. Works even while hidden, because the restore is read from a raw key hook that Panic Mode doesn't disable. |

Each combat module ships with a small HUD indicator (WTAP / JUMP / REACH / TOTEM / CRIT / KB / KBDLY / SPEAR / STRAF / BKTRK / CRYST / ANCHR / CAURA / CWARP / PRINT) that only appears while the module is enabled and flashes briefly when the module fires. Indicators are tinted by their module's **risk tier** (see below).

### Risk tiers

Every addon module shows a colored risk label next to its `info()` value (e.g. `Reach 12.0 Blatant`) based on how obvious the current configuration is:

- **Closet** (green) — barely noticeable
- **Legit** (yellow) — passes casual inspection
- **Risky** (orange) — will draw attention
- **Blatant** (red) — obviously cheated
- **Impossible** (pink) — reach ≥ 20 blocks etc.

## Requirements

- Minecraft **26.2** (Mojang mappings)
- Fabric Loader **>= 0.19.3**
- Fabric API **>= 0.152.2+26.2**
- AUTISM Client **4.4-26.2** (the addon compiles against the client jar; load at runtime)
- Java **25**

## Building

The AUTISM Client must be published to your local Maven repo once, from the AUTISM project:

```sh
./gradlew publishToMavenLocal -Prelease
```

Then build the addon:

```sh
./gradlew clean build --no-daemon
```

The jar lands in `build/libs/autism-minimal-addon-template-1.0.0.jar`.

## Installing

1. Drop the jar into `.minecraft/mods/`.
2. Launch the game with AUTISM Client loaded.
3. Open the module menu (default: Right Shift), find the addon category, and enable the modules you want.

## Usage & settings

### Auto WTap
- **Mode**: Sprint-Tap (release W) or Sneak-Tap (tap sneak) after a hit.
- **Delay / Min hold / Max hold** (ticks): reaction delay and randomized hold length.
- **Chance (%)**: per-hit chance to actually tap.
- **Accuracy (%)** (default 85): how often the tap is perfectly on time; the rest taps a tick or two late to read as a human reaction.
- **Require sprinting / Require full cooldown**: only tap while sprinting and/or with a full attack-cooldown (1.9+ servers).

### Auto JumpReset
- **Chance (%)**, **Min/Max reaction** (ticks), **Min/Max hold** (ticks): randomized reaction and jump length.
- **Accuracy (%)** (default 85): how often the reset is perfectly on time; the rest reacts a couple of ticks late, like a slow human reaction instead of a perfect scripted jump every hit.
- **Require on ground**: only jump when grounded.

### Reach
- **Reach**: 3.0–40.0 blocks (default **3.05**). Servers still enforce their own limit. Grim simulates combat and flags attacks above ~3.05 blocks; Vape v4 legit configs run 3.1–3.4 (bedwars) and 3.0–3.1 on Grim servers. The clicker additionally never attacks past 3.2 blocks.

### Legit AutoTotem
- **Chance (%)** and **Min/Max delay (ms)**: randomized timing between each inventory click.
- **Dynamic delay (on by default)**: the lower your health, the faster it reacts (danger-based delay like Vape/Prestige auto totem) — near death it refills quickly, at full health it uses the normal randomized delay. Reads as a human speeding up under pressure.
- **Grim-safe opening**: the inventory only opens once you're **grounded and stopped** (not sprinting, not mid-jump, not knocked back) and not using an item/attacking. Grim's `InventoryD` flags moving while an inventory is open, which is why instant mid-fight refills "sometimes flag". If you keep moving past a short deadline, the attempt aborts and retries later so it never stalls.
- **Pause in combat (on by default)**: while a player (or KillAura target) is within the **Combat distance** (default 6 blocks) the totem refill waits — no risky mid-fight inventory opens. Retries shortly once the enemy is out of range.
- **Double hand**: also hold a second totem in your main hand. **On by default** — it only double-hands into an empty hotbar slot or a slot already holding a totem, and never takes over your active slot.
- **Only when low health / Health threshold**: only swap when below a health+absorption threshold (default 14).

### BetterAutoClicker
- Requires the client's **KillAura** to be enabled when **Attack with KillAura** is on (it reads KillAura's current target and keeps its aim/rotation). Turn that toggle off for a classic autoclicker that clicks only while you hold the attack button (clicks whatever is under your crosshair).
- **CPS** (default **10–13**, domain 1–20): one slider bar with **two handles** — drag either handle to set the min and max clicks per second; every click is randomized somewhere between them (Vape v4 legit range is 8–14). Even at a fixed slider value the interval gets human variance and a ~6% skipped beat, so the timing is never a machine-perfect period.
- **Attack cooldown (on by default)**: only sends attack packets when the cooldown is ready so 1.9+ hits deal full damage; turn it off for pure 1.8-style clicking at the CPS slider.
- **Swing when cooldown at (%)** (default 100): minimum attack-cooldown charge before attacking. 100 = only fully-charged swings, which is what modern servers expect and the safest against anticheats.
- **Require facing (on by default)**: only clicks once the target is actually under your crosshair, so it never clicks while KillAura's rotation is still snapping — clicking off the crosshair during the snap is what anticheats flag as aimbot. Clicks at any CPS with an out-of-range target are also rejected (vanilla 3.0-block fallback when Reach is off), so a bad slider value can't produce reach flags.

### Auto Critout
- Works together with **BetterAutoClicker** in both of its modes; it hops before a swing so the hit lands as a crit.
- **Chance (%)**: per-hop chance. **Min between hops (ticks)**: space between jumps so it stays believable.
- **Min hold / Max hold (ticks)**: how long the hop is held (the crit window).
- **Min still (ticks) / Still tolerance (blocks)**: how long the target must stay motionless before hopping.

### Velocity
- **Horizontal (%) / Vertical (%)** (default **85 / 90**): how much of the incoming knockback you keep on each axis. Vape v4 legit configs run 85–95; big reductions are what Grim flags ("if you are moving, you will flag antikb").
- **Jump Reset mode** (off by default): recommended for Grim. Keeps 100% knockback and jumps the instant the knockback packet lands. Jumping is a vanilla mechanic that cancels the knockback by taking you out of the server's movement sim, so Grim reads a legit jump rather than a client-side velocity reduction. This is the only velocity that reliably passes strong Grim setups.
- **Chance (%)** (default 100): per-hit chance to apply the velocity effect (reduce or jump reset).
- **Delay reduction (ticks)** (default 0): keeps the full server knockback for this many ticks before the reduction kicks in, so each hit still moves you the way the server predicted first. 0 = reduce instantly.
- **Jitter (%)** (default 10): random variance added to the reduced knockback each hit, so the reduction is never the same flat percent twice. Keep low (0–10) on Grim.
- Example Grim-safe reduce: **H 90 / V 90 / Chance 100 / Delay 0 / Jitter 10**. For anti-kb that keeps combo: enable **Jump Reset mode**. CatPVP (very weak anticheat): **H 0 / V 0 / Chance 100**.

### KnockbackDelay
- Vape v4-style: incoming knockback packets are **held** (not applied) for a short window and released late. You stay in place during that window, so in a combo you keep hitting while the opponent is still being pushed — extra reach on weak servers.
- **Chance (%)** (default 100): chance the knockback is delayed rather than applied instantly.
- **Air delay / Ground delay (ticks)** (defaults 4 / 10): the hold window, chosen by whether you were grounded for a few ticks before the hit. Higher = longer stay-in-place, but more obvious.
- **Target range (blocks)** (default 4): the delay only engages while an enemy is within this distance of your crosshair (uses the clicker/KillAura target, falling back to whatever is under your crosshair).
- **Disable in water (on by default)**: never delays while in water or lava, to avoid looking suspicious.
- Pairs with **Velocity** — a held knockback is released through Velocity's reduction, so you get delayed *and* reduced (Vape's recommended combo).
- Because it only engages mid-fight near a target, it never affects random knockback (rods, fall, arrows) when you're not fighting.

### SpearKill
- Deliberately blatant: toggle it on and **hold right-click** with a trident (spear) in hand. It snaps your aim to the nearest target and keeps charging + releasing the trident as long as you hold the button.
- **Range (blocks)** (default 12): max distance to pick a spear target.
- **FOV** (default 360): target cone. 360 = any direction — the module does the aiming for you.
- **Players / Mobs**: which entity types count as targets.
- **Charge (ticks)** (default 12): charge time before each throw. 10+ is required to throw at all; **20 = full power / max range**. Lower = faster spam, weaker throw.
- Pairs with **Velocity** for a Riptide combo (the throw launches you toward the target).

### Auto Strafe
- Strafes around the enemy during your jumps. Pairs with **KillAura** + **Auto Critout**.
- **Direction**: **Around target** (default — reads the enemy's bearing and holds the strafe key toward their side so you orbit them), Left, Right, or Random (switches side each jump).
- **Hold W (on by default)**: also holds forward while strafing for the diagonal jump-strafe. Off = strafe key only.
- **Only while jumping (on by default)**: strafes only while airborne. Off = strafes constantly while the module is on (only makes sense with Hold W off).
- **Target range (blocks)** (default 6): scan distance for Around-target. Uses the client's **KillAura target** when it has one, falling back to the nearest player/mob.
- **Players / Mobs**: which entity types count for Around-target.
- Any simulated key is always released back to your real physical key state the moment strafing stops, disables, or you leave the game — it never leaves a key held.

### Backtrack
- Delays your outgoing **position packets** by a configurable amount, so the server sees you where you were a moment ago (Vape v4 BackTrack / PingSpoof style). Uses the client's own `PingSpoofController` with a dedicated owner slot, so it never collides with the built-in PingSpoof module.
- **Delay (ms)** (default 150, 0–500): how long each position packet is held. 50–100 subtle, 150+ very noticeable.
- **Only during combat (off by default)**: delays only while an enemy is near your crosshair / KillAura target (**Target range** blocks). Off = delays constantly while the module is on (classic backtrack behavior).
- The delay only ever touches movement packets — keepalive/pong/chat stay immediate, and the client force-flushes on teleport/respawn/death so it can never strand you.
- **Not for Grim** — delayed position packets are easy to flag; this is a weak-server (CatPVP-style) module.

### Crystal Macro
- **Bind a key and hold it** — the module only runs while the bind is held (or while toggled on from the menu). It places an end crystal on the block under your crosshair, waits, then attacks the crystal to detonate it, looping while you keep holding.
- **Detonate delay (ticks)** (default 6): gap between placing and attacking so the server registers the placement.
- **Cycle delay (ticks)** (default 4): gap between detonations. Lower = faster spam.
- **Auto-select crystal (on by default)**: finds an end crystal in the hotbar and selects it before placing. Off = places whatever is in your selected slot.
- **Switch back to sword (off by default)**: selects a sword in the hotbar after every detonation.
- **Range (blocks)** (default 4): placement distance limit from your position. Look at the block, hold the bind, done.
- Already-placed crystals are detonated without re-placing, so holding the bind over a crystal kills it even if the placement failed.

### Anchor Macro
- Same hold-your-bind pattern as the crystal macro: places a respawn anchor on the block under your crosshair, charges it with glowstone, then **breaks it to detonate** (a charged anchor explodes when broken), looping.
- **Detonate delay (ticks)** (default 4) / **Cycle delay (ticks)** (default 4): timing gaps as in the crystal macro.
- **Auto-select anchor (on by default)** and **Charge with glowstone (on by default)**: auto-select the anchor to place and glowstone to charge. Turn charging off to only detonate anchors that are already charged.
- **Switch back to sword (off by default)** and **Range (blocks)** (default 4): as in the crystal macro.
- Both macros are **hostile, machine-speed** modules (the addon's explicit exceptions together with SpearKill) — they automate actions that real players do manually, so they read as obviously automated.

### Crystal Aura
- Fully automatic: targets the nearest player (or mob, toggle), finds the safest valid crystal spot beside them, places a crystal, then attacks it to detonate after a short delay — looping with random jitter so it is never perfectly periodic.
- **Target range (blocks)** (default 6), **Players / Mobs**: who to target. Uses the client's **KillAura target** when it has one.
- **Min self distance** (default 2.0): how far from you a crystal spot must be so you don't blow yourself up with every hit.
- **Detonate delay (ticks)** (default 2) / **Cycle delay (ticks)** (default 3) / **Jitter (ticks)** (default 1): the spam rhythm. Lower cycle = faster, more obvious.
- **Smooth aim (on by default)**: glides toward each crystal instead of snapping instantly.
- **Auto-select crystal (on by default)** / **Switch back to sword (on by default)**: item handling between hits.
- It never detonates an already-exploding crystal and re-uses the current spot while it stays valid.

### CrystalWarp
- The classic self-launch escape: **bind a key and hold it** — places an end crystal at your own feet, then detonates it to rocket yourself up and away from pressure. Loops while held (each launch is a full cycle).
- **Detonate delay (ticks)** (default 1): minimal gap so the placement is registered.
- **Jump before detonate (on by default)**: jumps right before the explosion so it launches you higher.
- **Cycle delay (ticks)** (default 6): spacing between launches.
- **Auto-select crystal / Switch back to sword**: as in the other crystal modules.
- Like the macros and Crystal Aura, this is a **hostile** module — you take the explosion damage on purpose.

### Litematica Printer
- Reads the schematic **world** of the currently loaded Litematica schematic and places any block that is missing in your vicinity. Uses cached reflection into `fi.dy.masa.litematica.world.SchematicWorldHandler` / the schematic world's `getBlockState`, so it works whenever Litematica is installed alongside the client — the addon itself never hard-depends on Litematica classes (the host client does not bundle Litematica).
- **Rotation never snaps**: the default **Smooth look** turns the camera toward each block with **Smooth speed** (deg/tick) and only clicks after the aim is within **Look tolerance** — so every placement happens with the look actually on the block, which is what non-cheating servers expect. Turn Smooth look off only if you want fast snap-placement (more visible).
- **Range (blocks)** (default 4, 1–6): scan box around you. Actual placement is still capped by your real block-interaction reach, so raising the range only scans farther.
- **Place delay (ticks)** (default 2, 0–20) / **Blocks per pass** (default 1, 1–10): printing rhythm. Delay 0 + a high blocks-per-pass is an obvious fast-printer; the defaults stay a steady, human-ish builder.
- **Rotate to block (on by default)**: aims at each placement before clicking. Off = you look at the blocks yourself and it places under your view.
- **Smooth look (on by default)**: instead of snapping the aim onto the block and placing instantly, the look **glides** toward each block every tick and the placement click only fires once the aim is within the **Look tolerance** (default 2°) of the target. Because the placement rotation matches what the server/anticheat expects (no snap-and-place, no rotation-vs-click mismatch), it stays human-looking and doesn't flag. **Smooth speed** (default 2 deg/tick) controls how fast the turn is.
- **Swing hand (on by default)**: visible swing on every placement.
- **Air place (on by default)**: when no solid neighbor is in reach it falls back to placing against an air/replaceable block (player-facing placement). Off = only place against a reachable solid neighbor.
- **Search inventory (on by default)**: if the needed block is not in the hotbar it swaps it in from the main inventory into an empty hotbar slot using genuine container clicks (`AutismInventoryHelper`), then selects it. Off = hotbar only.
- **Dirt as grass (on by default)**: when the schematic wants a grass block and you have none, it uses a dirt item instead (grass will spread in place).
- Skips blocks you can't build: ones that are already correct, replaceable-air that would intersect your hitbox, spots where the required block can't survive, and blocks whose fluid isn't empty.

### Screenshare Bypass
- The panic button for staff screenshares: enable the module and **bind it to an inconspicuous keyboard key** (the module's own keybind). **Double-tap the key** and the entire client hides instantly — every module turns off, every HUD element disappears, and all client chat/packet output is suppressed. **Double-tap it again** and everything you had enabled is restored exactly as it was.
- It drives the client's built-in **Panic Mode** (the "hide" module) through its public API rather than reimplementing the hide: same coverage, same clean restore.
- Because Panic Mode switches off the client's normal keybind/event system, the restore gesture is caught by a raw `KeyboardHandler` hook (`KeyboardInputMixin`) that is not gated by panic state — the double-tap works in both directions even while hidden, and nobody can toggle anything from the menu mid-screenshare.
- **Double tap window (ms)** (default 350, 100–1000): how quickly the two presses must land.
- **Keyboard key only**: mouse-button binds are not supported for the gesture (the raw hook is keyboard-only). Set a keyboard bind on the module (e.g. F8) and remember it.

### Quick Config
- **Gamemode**: Vanilla, UHC, Pot, NethOP, SMP, Sword, Axe, or Mace (the real MCTIERS gamemodes) — each tunes which modules are used and how.
- **Blatantness** (0–100): 0–19 Closet, 20–44 Legit, 45–69 Risky, 70–89 Blatant, 90–100 Impossible. Scales reach, velocity, CPS and per-module chances (presets stay realistic even at max).
- **Apply**: writes the preset to Reach / Velocity / BetterAutoClicker / Auto Critout / Auto WTap / Auto JumpReset / Legit AutoTotem (and enables the client's KillAura). Changing the gamemode or slider also re-applies automatically.
- The module is always rendered first in the addon's category column (pinned via the persisted per-category module order).

## Anti-detection notes

Modules are built to avoid the behaviors flagged by common CPvP cheat-catching guides:

- **No silent swapping** — items are always used from the selected hotbar slot, so the selected slot and hotbar tooltip stay in sync.
- **No hover-totem behavior** — totems are only moved with discrete clicks, never "the instant the cursor hovers".
- **Double-hand is slot-safe** — the second totem only goes into an empty hotbar slot or one that already holds a totem, so your active item is never replaced and your other items stay usable.
- **No tick-perfect timing** — every interaction uses randomized human jitter.
- **No global fast-place** speedups.
- **Grim-safe defaults** — reach 3.05 (Grim flags ~3.06+), velocity keeps 85–90% (or Jump Reset mode), CPS 10–13 with non-periodic intervals. Quick Config presets stay in the same Vape-v4 legit ranges (reach 3.0–3.4, velocity 80–95% kept, 8–14 CPS).
- **KnockbackDelay** only engages mid-fight (target near the crosshair) with a chance gate, so random knockback (rods/arrows/fall) is never touched — it stays a PvP-only effect.
- **Screenshare Bypass** hides through the client's own Panic Mode with a double-tap gesture, so during a screenshare nothing is visibly running (no HUD, no modules, no client output), and the restore can't be triggered accidentally or from the menu.
- **SpearKill, the hostile crystal/anchor modules (Crystal Macro, Anchor Macro, Crystal Aura, CrystalWarp) are the explicit exceptions** — they are built to be blatant (instant aim snap, machine-steady hostile automation). Everything else in the addon stays in "legit-looking" territory.

## Structure

```
src/main/java/com/example/minimal/
  MinimalAddon.java          addon entrypoint: registers modules + HUDs, seeds the ClickGUI window position
  MinimalInit.java           Fabric client entrypoint
  Tier.java                  risk tier labels + colors (Closet/Legit/Risky/Blatant/Impossible)
  modules/                   AutoWTapModule, AutoJumpResetModule, ReachModule, LegitAutoTotemModule,
                             AutoCritoutModule, BetterAutoClickerModule, VelocityModule,
                             KnockbackDelayModule, SpearKillModule, AutoStrafeModule, BacktrackModule,
                             CrystalMacroModule, AnchorMacroModule, CrystalAuraModule, CrystalWarpModule,
                             LitematicaPrinterModule, ScreenshareBypassModule, QuickConfigModule
  hud/                       WtapIndicatorHud, JumpResetIndicatorHud, ReachIndicatorHud, TotemIndicatorHud,
                             AutoCritoutIndicatorHud, VelocityIndicatorHud, KnockbackDelayIndicatorHud,
                             SpearKillIndicatorHud, AutoStrafeIndicatorHud, BacktrackIndicatorHud,
                             CrystalMacroIndicatorHud, AnchorMacroIndicatorHud, CrystalAuraIndicatorHud,
                             CrystalWarpIndicatorHud, LitematicaPrinterIndicatorHud, FlashIndicatorHud (base)
  api/                       RangeSetting (two-handle CPS slider value)
  mixin/                     PlayerReachMixin (reach), VelocityMixin (singleplayer kb), VelocityMotionMixin
                             (multiplayer kb), VelocityDelayMixin (delayed reduction tick), MinecraftMissTimeAccessor,
                             RangeSliderMixin (two-handle CPS slider), SettingOwnerAccessor + SettingDescriptionMixin
                             (readable full-size tooltip descriptions instead of tiny scaled inline text),
                             KeyboardInputMixin (raw key hook for the screenshare bypass gesture), wired via
                             src/main/resources/autism-minimal-addon-template.mixins.json
```

The addon uses the AUTISM Client API (`autismclient.api.*`) and follows the refmap-free Mojang-mappings mixin setup from the client's advanced addon template.

## License

This addon is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version, matching the AUTISM Client it extends. See the `LICENSE` file for the full text.
