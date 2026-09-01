# Panic

Alpha horde roguelike survival for Minecraft. A Paper plugin, server side only, vanilla clients.

Night belongs to the alpha mobs: oversized pack leaders that hunt you across the map with a
horde that digs through walls. You get one small safe haven, one one-way exit, and a scoreboard
that tracks one thing: who survived longest. Death resets the run, not the player.

The full design lives in [PLAN.md](PLAN.md).

## Requirements

- Paper 26.2 (build 121), Java 25
- A JDK 25 on the build machine (Gradle auto-provisions one if missing)

## Build

```bash
./gradlew build        # lint + format check + tests + jar
```

## Run

Drop `build/libs/Panic-0.1.0.jar` into your server's `plugins/` directory and restart.

## Test

```bash
./gradlew test
```

## Status

Bootstrap skeleton (2026-09-01). See PLAN.md for current state and the 7-phase build order.
