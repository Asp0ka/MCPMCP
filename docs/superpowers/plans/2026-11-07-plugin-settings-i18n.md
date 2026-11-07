# Plugin Settings and Localization Implementation Plan

> Use superpowers:executing-plans. User has already authorized implementation.

**Goal:** Native plugin compatibility list, local force overrides, en_us/zh_cn GUI.
**Architecture:** Pure compatibility record and binding token in core; existing PluginManager summarizes metadata, local Runtime persists overrides; native list screen and language resources.
**Tech Stack:** Java17/Forge47.4.28/Minecraft1.20.1/Gson/Python stdlib.
**Spec:** docs/superpowers/specs/2026-11-07-plugin-settings-i18n-design.md

## Global Constraints
- Only authorized dedicated instance; no external mod or recipe modification.
- Optional target_version preserves old manifests; absent version is unknown, not falsely matched.
- Force bypass only version/hash with exact manifest+target binding; no MCP self-escalation.
- Java remains trusted cold-only; revoke loaded code requires restart.
- GUI follows game language; no protocol identifier translation.

## Review Focus
- Missing mod/absent expected version/hash/platform mismatch.
- Override stale when manifest/mod file changes; arbitrary plugin text cannot force.
- Failed reload retains declarations; local override persistence rolls back on error.
- Narrow screen/long IDs/scroll and language changes.
- Already loaded Java enable/revoke communicates restart.

### Task 1: Compatibility and persistence
- [x] Write PluginCompatibilityTest for exact/version/hash/missing/platform/old manifest/override binding and run RED.
- [x] Implement PluginCompatibility, optional target_version validation and config overrides; integrate PluginManager and runtime reload.
- [x] Run core, manager standalone and config tests GREEN.

### Task 2: Native plugin screen and languages
- [x] Write language parity/usage tests and GUI policy test RED.
- [x] Add PluginListScreen, local force confirmation, translate SettingsScreen via en_us/zh_cn, extend UI settings guard.
- [x] Full build; inspect native screenshots in both languages.

### Task 3: Delivery
- [x] Test mismatch/force/revoke/missing and config persistence in dedicated client.
- [x] Focused independent review; fix material findings, rerun full suite.
- [x] Update docs and artifact version0.1.1; hash-check install, commit, package, final readback.

Evidence: docs/plugin-settings-0.1.1.md. Focused review I1/M1/M2 addressed with failing-first regressions. Two-language UI, force/revoke/persistence, real mod metadata, revoke+malformed reload and auto retry, empty/narrow/long-text checked. Local defaults restored.
