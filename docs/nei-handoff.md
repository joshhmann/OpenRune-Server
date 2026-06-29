@nei: Lumbridge handoff

OpenRune, CT 123. Server at ~/Runescape/open_rune/OpenRune-Server/.

Docs to read first:
1. docs/pm-audit-and-workflow.md — current PM state, gates, and strict workflow
2. docs/worklog-239.md — what's been done
3. docs/zone-workflow.md — the 10-layer template for completing zones
4. docs/deferred-items.md — known gaps (DEF-001 elf doors)

Our current state:
- Lumbridge NPCs done (27 Kotlin files in module), template baked
- `:content:areas:city:lumbridge:compileKotlin` passes
- Castle elf-fashioned doors are broken/deferred (RSCM symbol mapping issue, documented)
- Rune Mysteries quest is written but `:content:quest:compileKotlin` is red
- Current compile blocker: `RuneMysteries.kt` line 43 attempts `player.questState = prog.varp`, but `QuestScript.kt` owns `private var Player.questState`
- Server hasn't been restarted with latest code (need clean compile first; pgdata wipe/clean boot if needed)

Priority for next session:
1. Fix the Rune Mysteries compile issue so quest module goes green
2. Run full server compile
3. Restart server and do Layer 10 verify walkthrough
4. Decide: fix castle doors or explicitly accept DEF-001 and move to Draynor

Tools:
- NPC symbols: grep .data/raw-cache/map/npcs/*.toml
- Item IDs: grep .data/raw-cache/server/items.toml
- Cache defs: .data/raw-cache/server/
- Corpus: tools/data/corpus/parsed-data/
- Wiki: `web_extract` + `web_search`
- Compile: `./gradlew :content:areas:city:<zone>:compileKotlin`
- Run: `./gradlew :server:app:run --console=plain`

Mai left the workflow doc hot and the NPC pattern ready to replicate. You're set up for success.
