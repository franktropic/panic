# Panic

Paper plugin: alpha horde roguelike survival. Full spec + build order in PLAN.md.

## Commands

- build (lint + format check + tests + jar): `./gradlew build`
- format: `./gradlew spotlessApply`
- test: `./gradlew test`
- lint only: `./gradlew spotlessCheck checkstyleMain checkstyleTest`
- deploy: copy `build/libs/Panic-0.1.0.jar` into a Paper 26.2 server's `plugins/` dir

## Stack

- Java 25 (Gradle toolchain; hydrogen has `/home/frank/tools/jdk25`, elsewhere auto-provisioned)
- Gradle 9.7.1 wrapper, Paper API `26.2.build.121-stable`, JUnit 6, spotless (google-java-format), checkstyle
- Sources: `src/main/java/dev/mrz/panic/`, tests: `src/test/java/`
- NMS: not on the classpath yet. Recipe in PLAN.md ("NMS recipe") — add `libs/paper-26.2.jar` + `compileOnly` only when a feature needs it.

## Conventions

- One feature = one PR-sized step following the PLAN.md build order; update PLAN.md "Current state" + "Next step" at the end of each session.
- Every tunable number lives in `config.yml` (the brief demands a knob for everything).
- Bump the version in BOTH `build.gradle.kts` and `src/main/resources/plugin.yml`.
