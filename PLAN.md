# Panic — plan

Alpha horde roguelike survival for Paper. Night belongs to alpha mobs. Full spec below.

## Current state

- 2026-09-01: bootstrap. Gradle + Paper API 26.2 + JUnit 6 + spotless/checkstyle skeleton,
  `TimeOfDay` day-cycle helper with tests, plugin shell (`PanicPlugin`, plugin.yml, config.yml
  phase-1 knobs). Nothing in-game yet.
- 2026-09-01: **phase 1 done + deployed to mrzmc as Panic 0.2.0.** One-way spawn haven (24x24,
  kit, re-entry knockback, mob/hunger protection, no monster spawns), survival run timer with
  per-player sidebar + `/top`, alpha zombies (5x hp / 3x dmg / 0.5x speed / 2.5x scale, glowing,
  persistent, anti-idle nudge) leading hordes of 8 (cap 40), night-only spawns on a 10-minute
  cadence anchored 40-80 blocks from an escaped player, dawn burn-off, YAML persistence in
  plugins/Panic/data.yml, `/panic` admin (spawnalpha [at x z], despalphas, dawn, night, status).
  25/25 tests green, RCON-verified live (spawn/status/kill flows).
- 2026-09-01: **phase 2 done (dread pack, 0.3.0).** Proximity warnings, distant screams,
  heartbeat, fake steps/cracks, false alarms, alpha-kill announce.
- 2026-09-01: **phase 3 done (0.4.0) + spawn peace ring (0.4.1).** Tunnelling with dawn
  healing: hordes chew through solid blocks on tiered dig times, dawn burns the horde and
  heals tunneled-back blocks after a grace period. Peace ring: while any player is inside
  `peace-radius` (32), monster spawns are cancelled in the ring, monsters in the ring are
  de-targeted, set on fire each second, and cannot retarget (EntityTargetEvent gate); alphas
  cannot hunt a player in the ring. Fixes the spawn-camp bug where the rejoin point (2 blocks
  outside the 24x24 box) left rejoining players as valid hunt targets at 100 blocks.
- 2026-09-01: **0.4.2 speed baseline.** Vanilla creepers run 0.25 movement speed (9% faster
  than the 0.23 zombie baseline). `MobSpeedTuner` locks creepers to `mobs.creeper-speed`
  (default 0.23, 0 = vanilla) on spawn — every mob now moves at the same 1x pace.
   RCON-verified live via `attribute ... base get`.
- 2026-09-01: **0.4.3 playtest fixes + built haven.** Direct fixes from the live playtest:
  re-entry gate message throttled to 1/5s per player (was spamming); alpha stuck escalation
  (force re-path at 10s, despawn at 30s, `alpha.stuck-*`); alpha despawns 5 min after its last
  target goes offline (`alpha.offline-despawn-minutes`); unescaped players respawn at the haven
  center with a fresh kit (`PlayerRespawnEvent`). `HavenBuilder` flattens the haven at startup —
  smooth-stone floor, glowstone border ring + center marker, ceiling cleared — so the spawn
  reads as a clean lit room (`spawn-haven.build/floor/border`). Floor anchors to the spawn
  column's surface and the surface scan ignores the floor/border materials, so a stale slab
  from a previous build cannot mask the natural ground (idempotent rebuild). Glow and build are
  config-gated (`alpha.show-glow`). Scoreboard adds `/panic help` + one-life-per-run lines;
  `/panic help` is open to all players, admin subcommands check `panic.admin`.
   RCON-verified live: floor/ring/ceiling/marker at the spawn surface, outside-ring untouched.
- 2026-09-01: **0.4.4 spawn fortress + world reset.** From the live playtest (world got
  "bombed up"): haven column is now unbreakable/unplaceable bedrock-to-sky — `BlockBreakEvent`
  and `BlockPlaceEvent` cancelled in the 24x24 box at all Y, `EntityExplodeEvent` strips box
  blocks from the blast list (creepers crater the outside but never the room),
  `EntityChangeBlockEvent` cancelled (endermen). `peaceTick` scan now centers at the surface
  elevation with a 96-block vertical half-extent (the old y=0 + 32 radius missed the entire
  surface layer, so the peace ring never actually touched ground mobs). `HavenBuilder` strips
  tree material (leaves/logs/saplings/azaleas, explicit list — 26.2 `Material` has no
  isLog/isLeaves helper) 16 blocks above the ceiling clear, killing the floating-canopy
  "leaf slop" the ceiling cut left behind. Deployed with a world regen (old world backed up as
  world-backup-20260901-bombed) using the pinned seed
  `-8089496942705041839` extracted from `world_gen_settings.dat` (26.2 moved the seed out of
  level.dat, and an empty `level-seed` is random per generation). `gamerule locator_bar false`
  set post-regen (new-world gamerules reset). RCON-verified live: 12/12 haven probes, locator
  bar off, live creeper blast left the floor intact and cratered the outside ring.

Build-order status: steps 1-3 done. Next is step 4: stats, corpses, blood moon.

## Next step

Playtest again with real players on 0.4.4: spawn protection (try to mine the floor, pop a
creeper in the room), peace ring at surface elevation, no floating leaves. Re-op Anita + Jash
on join (new world = no ops). Then build-order step 4: RPG stats, corpses, blood moon. Code
review of the full polish pass is queued on switchboard (task 283f47fa) — apply findings when
it lands.

## NMS recipe (add only when a feature needs it)

The paper-api dependency covers the Bukkit/Paper API. For NMS (net.minecraft.*) compile-time
access, add the assembled Paper server jar:

1. Download the paperclip launcher jar from `https://fill.papermc.io/v3/projects/paper/versions/<ver>/builds/<build>`
   (`downloads["server"]["default"]`), save to `libs/paper-<ver>.jar`.
2. Run it once under Java 25 in a scratch dir (`java -jar libs/paper-<ver>.jar --nogui`); it
   assembles `versions/<ver>/paper-<ver>.jar` (byte-identical to a live server install, verified
   sha256) then exits at the EULA gate. Copy that assembled jar to `libs/paper-<ver>.jar`.
3. Add `compileOnly(files("libs/paper-26.2.jar"))` to build.gradle.kts and gitignore libs/.

---

# Brief (v2, 1 Sep 2026)

# Alpha horde

Full brief, v2. Paper plugin, server side only, vanilla clients. 1 Sep 2026. Feasibility tweaks folded in.

## Concept

Roguelike survival. Night belongs to alpha mobs: oversized pack leaders that hunt players across the map with a horde that digs through walls. Spawn is a small safe haven you can leave exactly once per life. The scoreboard tracks one thing: who survived longest. Death resets the run, not the player.

## High score

- Timer starts the moment a player first steps out of spawn, stops on death.
- Longest single life is the score. Persisted per player, shown on a sidebar scoreboard and a `/top` command.
- Death announces the run length in chat.

## Spawn haven

- Small region, roughly 24x24 blocks. New players start inside with a basic kit.
- One-way exit: the first outward border crossing sets a persistent `escaped` flag on the player.
- Flagged players cannot re-enter. Movement back across the border is cancelled with a knockback and a message.
- Death clears the flag. You respawn in the haven and walk out to start a fresh run.
- Inside the haven: no mob damage, no hunger drain, no alpha targeting.

## Alpha mobs

| Stat | Value | How |
|---|---|---|
| Health | 5x base mob | `max_health` attribute |
| Damage | 3x | `attack_damage` attribute |
| Speed | 0.5x | `movement_speed` attribute |
| Size | 2.5x, hitbox included | `scale` attribute (1.20.5+) |
| Detection | 100x range | `follow_range` attribute, plus a plugin tick that force-sets the nearest valid player as target |
| Aura | Glowing particle ring | Per-tick particle spiral (`END_ROD` or `GLOW`), plus the glowing outline effect |

Alphas never idle. A targeting task runs every second: if an alpha has no target, it picks the nearest escaped player anywhere within range and pathfinds toward them. Base mob type is configurable, zombie first.

## Horde

- Normal-stat copies of the alpha's mob type spawn continuously around the alpha, up to a cap (default 8, config).
- Horde members copy the alpha's current target every second. Player switches, they all switch.
- Cut-off spawns: when the target flees, some horde members spawn ahead of the player's escape line, not around the alpha. Flight is never free.
- Horde members within the aura get a faint matching particle so packs read visually.
- On alpha death the link breaks: members lose the forced target, scatter in random directions for 5 seconds, then behave as vanilla mobs.

## Spawning rules

- Alphas spawn only at night, at most one per 10 minutes, server wide.
- Blood moon: every 10th night, a horde event. 3 special alphas spawn at once, announced in chat.
- Blood moon alphas have a red aura (redstone dust and flame particles) instead of the normal glow, so they read as event mobs.
- They die automatically at dawn, with a burn-away particle effect. Kills before dawn still count.
- Blood moon alphas carry a guaranteed rare drop, so the event rewards a fight, not a bunker night.
- The moon itself cannot change colour server side. The event sells the look with red particle haze in the sky, red-tinted chat, and ambient sound.
- Population cap: one alpha per player that has ever joined. Under the cap, the 10 minute timer runs; at the cap, nothing spawns.
- Spawn point: 40 to 80 blocks from a random escaped player, on a valid surface, never inside the haven.

## Day and night

Day is shortened. A time task accelerates ticks during day (roughly 5 minutes of day, the full 10 of night, tunable). Night is when alphas spawn and the pressure systems run.

## Tunnelling

- Normal zombies dig, alphas do not (Snake's split). The alpha looms outside while its horde chews through the walls. A blocked zombie applies "chew" damage to the block ahead each second, with crack animations sent to nearby players, then the block breaks.
- All vanilla zombies dig too, at half the horde dig speed, so bases hold longer against random mobs.
- Resistance tiers by material: dirt and sand fast (about 1s), wood medium (about 3s), cobblestone and stone slow (about 6s), iron and metal blocks very slow (about 15s). Obsidian and bedrock immune.
- Broken blocks do not drop items. This makes base material choice a real defence decision: metal buys time, nothing buys safety.
- Tunnels heal at dawn: broken blocks restore with a particle effect, so night damage is a tide, not permanent scarring. Blocks broken in the final 30 seconds stay broken so escapes are not walled off.
- Dig logic is fully custom (mobs do not path through walls). Prototype this feature first: stuck loops and self-drowning are the known risks.

## Dread systems

- While an alpha is hunting a player, that player hears distant mob screams (vanilla sounds, pitched down, played at a random offset position) every 20 to 40 seconds.
- Proximity chat messages as the alpha closes: at 80 blocks "Something has your scent.", at 40 "The ground trembles.", at 15 "It is here." Messages go only to the hunted player.
- Heartbeat effect inside 20 blocks: brief darkness or nausea pulses (potion effects, so fully server side).
- Alpha kills a player: server-wide lightning-strike sound and a chat announcement.
- Fake footsteps: sounds played behind a player when nothing is there.
- Warden heartbeat that speeds up as the alpha closes, then sudden silence right before an ambush.
- Per-player fake blocks: the hunted player sees cracks spread across their wall, others do not.
- False alarms: about 1 in 4 approach warnings have no alpha behind them.
- Screen effects: darkness pulses, fake lightning, title text ("IT SEES YOU"), red vignette via the per-player world border warning.
- Death corpses: loot stays where you died in a lootable container, open to anyone.
- Named bounty alphas: an alpha that survives 3 nights gets a name and a public kill count in chat.
- Social pressure: chat announces who the alpha hunts, and the alpha retargets whoever hit it last.

## RPG stats

Fully server side via vanilla attributes, no client mods. Players earn stat points (from alpha kills, survival time milestones, night survival) and spend them with a command or a chest GUI menu.

| Stat | Attribute | Per point (example) |
|---|---|---|
| Vitality | `max_health` | +1 heart |
| Strength | `attack_damage` | +5% damage |
| Agility | `movement_speed` | +3% speed |
| Grit | `knockback_resistance` | +5% resist |
| Ferocity | `attack_speed` | +4% swing speed |
| Labour | `block_break_speed` | +5% mine speed |

- On death you keep 25% of earned points (config), the rest is lost. A death stings without erasing a long run entirely.
- Current stats show in the tab list or on the sidebar under the survival timer.
- Caps per stat (config) so late runs get strong, not silly.
- This stacks with the high score: a long run is also a powerful run, so deaths cost more the longer you live.

## Hyper alpha (endgame)

- One giant warden, 10x health, 4x damage, scale about 4x. Spawned once per world.
- It does not hunt players. It stays in its lair region and roams slowly inside it.
- It spawns alphas around itself on its own timer, in addition to the normal night spawns.
- Its sonic boom is the lair defence: heavy damage plus capped knockback to anyone inside 20 blocks.
- The lair is a walled arena, so the void cannot cheese the fight in either direction.
- Warden idle burrowing and despawn are suppressed. Sculk sensing gets replaced by plugin targeting.
- Kill it and the run ends permanently: all alpha spawns stop for the world, a server-wide victory announcement fires, and the scoreboard freezes as the final record.
- The lair is in the End dimension, out on the outer islands past the main island. Reaching it needs the full vanilla path: find the stronghold, kill the ender dragon, take a gateway.
- This keeps overworld survival as the main loop. The hyper alpha is opt-in endgame content for a geared group.
- Compass: rare alpha drops give a lodestone-style compass. In the overworld it points to the nearest stronghold. In the End it points to the hyper alpha's live position.

## Alpha abilities

- Psychic screech (3 min cooldown): a warden-style sonic boom sound and a particle shockwave. For 30 seconds the horde gets 2x speed and 2x damage, and the alpha drains its own health slowly during the effect. Used when the target is within 30 blocks or has been fleeing for over a minute.
- Burrow ambush (5 min cooldown): the alpha digs down, moves under the target and erupts beneath them with a block-shower particle burst and knockup. Only when the target stands still too long.
- Call of the deep (once per night): if the alpha's horde is below half cap, an instant respawn wave refills it.
- Each alpha rolls one bonus ability at spawn so no two nights feel identical.

## Alpha variants

- Alpha creeper: keeps its distance from the player, it never closes in itself.
- Its spawned creepers hold passive around it and do not attack.
- On its screech, every held creeper gets 2x speed and 0.5x health and rushes the player.
- The rush triggers each creeper's fuse on contact range, so the screech is the "incoming wave" alarm.
- Same variant pattern is open for other mob types later (skeleton alpha as artillery, spider alpha as ambusher).

## Pillagers and villages

- Roving pillager bands (4 to 8) patrol the overworld and attack players and mobs on sight, alphas included. Three-way fights are the point.
- Villages get aligned pillagers, friendly to their home village. Some hold the village as guards. Some go out as raiding parties against other villages and players.
- Guard pillagers defend villagers against zombies and alphas, so a village is a real (temporary) shelter.
- Village loot buffed: chests roll from a better table (diamond gear, enchanted books, golden apples) so villages are worth the risk of the raiders around them.

## Treasure pigs

- Rare glowing pigs, 2x scale, 0.5x speed, that flee from players.
- Heavy aura so they read as loot: dense glow and firework-spark particles, visible far off.
- On death they drop a random enchanted diamond weapon plus diamond ore or emeralds.
- Slow but fleeing, so the chase is easy to start and annoying to finish, especially at night with alphas about.

## Oceans

- Alpha drowned: the full alpha treatment underwater, trident armed, with a drowned horde that shares its target.
- Something below: in deep ocean a guardian-style shape circles under boats with sonar ping sounds, then strikes the boat and dumps the rider in.
- Ocean pressure: the darkness effect thickens with depth, so deep dives need night vision potions.
- Night aggro: swimming or boating at night pulls hostile aggro from a wide radius, land is the safe route after dark.
- Glowing dolphins: the treasure pig of the sea, they flee and drop heart of the sea loot.
- Rip currents: random strong current zones drag players off course, particles show the flow direction.

## LLM operator (Franky)

- The plugin collects a world snapshot every 5 minutes while players are on: who is online, positions, health, armour, alpha targets, recent deaths, blood moon state, notable events.
- A sidecar service sends the snapshot plus a persona to an LLM (hydrogen llama-swap, or OpenRouter cheap model).
- The LLM picks actions from a fixed menu only: chat taunt (global or per player), title text, sound at a position, fake lightning, a false alarm. No raw command access.
- Deaths and hyper alpha hits push immediately, so taunts land while they sting.
- Franky NPC: every 15 in-game minutes Franky appears at the edge of a player's view (spawned on the periphery, despawns if approached too slowly). He taunts in chat while visible.
- Franky can be killed. It is hard, he flees and blinks away. A kill sets alphas passive for 2 nights, announced server wide.
- Franky holds grudges. When he respawns after a kill, he marks his killer as hunted and triggers a blood horde on them: a wave of red-aura mobs that target only that player, with taunts to match. The 2 passive nights come first, then the payback.
- The killer also gets a unique trophy item and a scoreboard title, so the kill reads as a badge, not a mistake.

## Implementation notes

- Everything is vanilla-client compatible: attributes, particles, sounds, scoreboards, chat, potion effects, block-crack packets. No resource pack required.
- One Paper plugin, Java, per-alpha state object, tasks on a shared 1s scheduler plus a 1-tick particle task.
- Config file for every number above.
- Persistence: player flags and high scores in the plugin's data file; alphas can despawn at dawn (config) or persist.
- 100x detection is custom movement: a tick task walks the alpha toward the target in segments, real pathfind takes over inside 100 blocks.
- False alarm ratio scales with player survival time: veterans get fewer but nastier fakes.
- Performance caps: 4 live alphas, about 40 total horde mobs, one shared particle scheduler. Paper is CPU single-thread, the caps are load-bearing.

## Build order

1. Core loop: one-way spawn, high score, alpha with horde, short days.
2. Dread pack: sounds, chat, false alarms, fake blocks.
3. Tunnelling with dawn healing (the risky one, prototype early).
4. Stats, corpses, blood moon.
5. Pillagers, oceans, treasure mobs.
6. Hyper warden lair, compass.
7. Franky and the LLM operator.
