# AUTISM PvP Addon

Sword PvP micro-automations for [AUTISM Client](https://autismclient.com) for Minecraft 26.2. The modules are deliberately "legit-looking": they only ever do things a real player can do (key presses, opening the inventory, real container clicks), with human randomized timing.

## Features

| Module | What it does |
| --- | --- |
| **Auto WTap** | Briefly taps W (or sneak) right after a landed hit to push your opponent back further. |
| **Auto JumpReset** | Jumps the moment you get hit to reduce knockback. |
| **Reach** | Extends your attack and block-interaction range up to 40.0 blocks via a mixin on `Player#entityInteractionRange` / `Player#blockInteractionRange`. |
| **Legit AutoTotem** | Opens your real inventory and swaps a totem into the offhand using genuine container clicks (`ServerboundContainerClickPacket`) — no bare item-change packets, so anticheat sees normal inventory interaction. |
| **KillAura But Better** | Works with the client's KillAura/aimbot: KillAura keeps aiming while this module takes over the clicking at your tuned **1.8-style CPS** (min/max slider, e.g. 15–18) and hops right before a swing at a stationary target so those hits land as crits. Its own clicker replaces KillAura's fixed-rate one. |
| **Quick Config** | Applies a MCTIERS-style gamemode preset (**Sword / Crystal / Rod / Bow**) across the addon's combat modules, scaled by a **Blatantness** slider (0–100). Stays pinned to the top of the addon's category column. |
| **Velocity** | Scales the knockback you take (horizontal + vertical %) via a mixin on the motion packet (`Entity#lerpMotion`) for modern multiplayer knockback, with the singleplayer `LivingEntity#knockback` mixin kept for offline play. |

Each combat module ships with a small HUD indicator (WTAP / JUMP / REACH / TOTEM / CRIT / KB) that only appears while the module is enabled and flashes briefly when the module fires. Indicators are tinted by their module's **risk tier** (see below).

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
- **Require sprinting / Require full cooldown**: only tap while sprinting and/or with a full attack-cooldown (1.9+ servers).

### Auto JumpReset
- **Chance (%)**, **Min/Max reaction** (ticks), **Min/Max hold** (ticks): randomized reaction and jump length.
- **Require on ground**: only jump when grounded.

### Reach
- **Reach**: 3.0–40.0 blocks (default 4.0). Servers still enforce their own limit.

### Legit AutoTotem
- **Chance (%)** and **Min/Max delay (ms)**: randomized timing between each inventory click.
- **Double hand**: also hold a second totem in your main hand. **On by default** — it only double-hands into an empty hotbar slot or a slot already holding a totem, and never takes over your active slot.
- **Only when low health / Health threshold**: only swap when below a health+absorption threshold (default 14).

### KillAura But Better
- Requires the client's **KillAura** to be enabled (it reads KillAura's current target and keeps its aim/rotation).
- **CPS min / CPS max**: click rate range (default **15–18**, 4–20). Each click sends a real attack + swing, so the total rate matches the slider instead of KillAura's fixed internal clicker.
- **Chance (%)**: per-hop chance. **Ready scale (%)**: hop when the attack cooldown reaches this (default 80).
- **Attack cooldown (on by default)**: only sends attack packets when the cooldown is ready so 1.9+ hits deal full damage; turn it off for pure 1.8-style clicking at the CPS sliders.
- **Min still (ticks) / Still tolerance (blocks)**: how long the target must stay motionless before hopping.
- **Min between hops (ticks)**: space between jumps so it stays believable.

### Velocity
- **Horizontal (%) / Vertical (%)**: how much of the incoming knockback you keep on each axis. **0% / 0%** is near-total anti-knockback.
- **Chance (%)**: per-hit chance to apply the scaling.
- CatPVP (very weak anticheat): **H 0 / V 0 / Chance 100** for blatant anti-kb; **H 25 / V 30** if you want to look a little less frozen on hit.

### Quick Config
- **Gamemode**: Sword, Crystal, Rod, or Bow — each tunes which modules are used and how.
- **Blatantness** (0–100): 0–19 Closet, 20–44 Legit, 45–69 Risky, 70–89 Blatant, 90–100 Impossible. Scales reach, velocity, CPS and per-module chances.
- **Apply**: writes the preset to Reach / Velocity / KillAura But Better / Auto WTap / Auto JumpReset / Legit AutoTotem (and enables the client's KillAura). Changing the gamemode or slider also re-applies automatically.
- The module is always rendered first in the addon's category column (pinned via the persisted per-category module order).

## Anti-detection notes

Modules are built to avoid the behaviors flagged by common CPvP cheat-catching guides:

- **No silent swapping** — items are always used from the selected hotbar slot, so the selected slot and hotbar tooltip stay in sync.
- **No hover-totem behavior** — totems are only moved with discrete clicks, never "the instant the cursor hovers".
- **Double-hand is slot-safe** — the second totem only goes into an empty hotbar slot or one that already holds a totem, so your active item is never replaced and your other items stay usable.
- **No tick-perfect timing** — every interaction uses randomized human jitter.
- **No global fast-place** speedups.

## Structure

```
src/main/java/com/example/minimal/
  MinimalAddon.java          addon entrypoint: registers modules + HUDs, seeds the ClickGUI window position
  MinimalInit.java           Fabric client entrypoint
  Tier.java                  risk tier labels + colors (Closet/Legit/Risky/Blatant/Impossible)
  modules/                   AutoWTapModule, AutoJumpResetModule, ReachModule, LegitAutoTotemModule,
                             KillAuraButBetterModule, VelocityModule, QuickConfigModule
  hud/                       WtapIndicatorHud, JumpResetIndicatorHud, ReachIndicatorHud, TotemIndicatorHud,
                             KillAuraButBetterIndicatorHud, VelocityIndicatorHud, FlashIndicatorHud (base)
  mixin/                     PlayerReachMixin (reach), VelocityMixin (singleplayer kb), VelocityMotionMixin
                             (multiplayer kb), MinecraftMissTimeAccessor (CPS clicker), wired via
                             src/main/resources/autism-minimal-addon-template.mixins.json
```

The addon uses the AUTISM Client API (`autismclient.api.*`) and follows the refmap-free Mojang-mappings mixin setup from the client's advanced addon template.

## License

This addon is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version, matching the AUTISM Client it extends. See the `LICENSE` file for the full text.
