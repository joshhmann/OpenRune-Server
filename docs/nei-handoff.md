@nei: Lumbridge handoff

OpenRune, CT 123. Server at ~/Runescape/open_rune/OpenRune-Server/.

Three docs to read first:
1. docs/worklog-239.md — what's been done
2. docs/zone-workflow.md — the 10-layer template for completing zones
3. docs/deferred-items.md — known gaps (DEF-001 elf doors)

Our current state:
- Lumbridge NPCs done (26+15 handlers), template baked
- Castle doors are broken (RSCM symbol mapping issue, documented)
- Rune Mysteries quest is written but has a compile issue (QuestScript startup conflict with multi-quest — needs startup() override)
- Server hasn't been restarted with latest code (need pgdata wipe + clean boot)

Priority for next session:
1. Fix the Rune Mysteries startup issue so it compiles
2. Restart server and do Layer 10 verify walkthrough
3. Decide: fix castle doors or move to Draynor

Tools:
- NPC symbols: grep .data/raw-cache/map/npcs/*.toml
- Item IDs: grep .data/raw-cache/server/items.toml
- Cache defs: .data/raw-cache/server/
- Corpus: tools/data/corpus/parsed-data/
- Wiki: `web_extract` + `web_search`
- Compile: `./gradlew :content:areas:city:<zone>:compileKotlin`
- Run: `./gradlew :server:app:run --console=plain`

Mai left the workflow doc hot and the NPC pattern ready to replicate. You're set up for success.
