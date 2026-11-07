# JEI 15.74.0.234 adapter

Target: Minecraft 1.20.1 / Forge, JEI 15.74.0.234, SHA-256 `0bbd27c886b974be6906fe16f5e5e3ace8a87350b32e4361cca0213df1e7e86e`.

Exit the intended game before installing. Put plugin.json, adapter.jar and this GUIDE in `MCPMCP-Plugin/jei-0bbd27c886b974be6906fe16f5e5e3ac/`. Do not put adapter.jar in mods. Install only one JEI variant matching the actual full JAR hash/version. Enable trusted Java plugins locally after code review and restart. The alternatives ZIP in the release contains both mutually exclusive targets.

Query `jei` for its runtime contract; use `jei.categories`, `jei.ingredients`, `jei.recipes`, `jei.recipe`. Continue next_cursor even for empty incomplete pages. Cursors/recipe handles bind to runtime/world; do not reuse after reload or world change. Recipes keep the original focus group across subsequent queries. `outputs` lists all recipe alternatives; `displayed_outputs` and `slots[].displayed` identify the focused visible variant.

Use `jei.transfer` only with an already open compatible container and empty cursor. First dry_run, then submit once with the same live recipe handle. `submitted` is not server acceptance, material consumption or completed crafting. Observe transfer feedback and actual container slots after sync; JEI15.20 has no completion callback. Take crafted outputs separately through the correct current menu. `jei.show` changes GUI; queries do not open it. Missing handler or materials are errors; no fabricated fallback.

The core does not depend on JEI. This adapter is optional and contains no JEI/Minecraft/runtime dependency classes. The current committed build receipt and release manifest identify the binary. Other mod versions, all recipe categories and multiplayer latency remain unverified.
