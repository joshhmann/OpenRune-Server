# Worklog — OpenRune Lumbridge Content

Date: June 28, 2026

## Session Summary

### Layer 2 — NPCs (COMPLETE)
Created 26 major + 15 batch NPC handlers covering all F2P Lumbridge NPCs:
- Dialogue handlers for Duke Horacio, Father Aereck, Lumbridge Guide, Doomsayer,
  Restless Ghost, Gelin, 3x Combat Tutors, 5x Skill Tutors, Cook's Assistant
- Batch file `LumbridgeMinorNpcs.kt` covering farmers, border guards, imps, 
  Adventure Path guides, Seth Groats, Gillie, Hickton
- Generic content-group handlers cover men, women, cows, sheep, ducks

### Layer 4 — Interactables (DEFERRED)
Castle elf-fashioned doors identified as loc types `elfdoor`/`elfdooropen` (IDs 1543/1544).
These lack RSCM symbol mappings — the ConstantProvider throws on reverse lookup.
**Root cause confirmed.** Three fix approaches documented in deferred-items.md.

### Layer 8 — Quests (IN PROGRESS)
- **Cook's Assistant:** ✅ Complete (upstream)
- **Rune Mysteries:** Framework built but needs compile fix for QuestScript
  duplicate-startup issue. Duke Horacio and Aubury handlers done. Sedridor
  handler using `npc.head_wizard` created.

### Bot Architecture
- Codex's agent separation committed: RspBotCycle, AgentBotService, 3-lane
  lifecycle (human/playerbot/agent). Fixes doppelganger visibility.

### Deliverables
- `content/areas/city/varrock/npcs/Aubury.kt` — new NPC handler
- `content/quest/src/main/kotlin/.../wizards_tower/RuneMysteries.kt` — quest impl
- `docs/zone-workflow.md` — 10-layer zone completion template
- `docs/deferred-items.md` — known gaps tracker (DEF-001: elf doors)
- `content/areas/city/lumbridge/npcs/DukeHoracio.kt` — quest-gated dialogue added

### Next Actions
1. Fix Rune Mysteries startup (override startup() in QuestScript subclass)
2. Compile + restart server for full verify pass
3. Castle doors need core ConstantProvider fix or workaround
4. Next zone: Draynor Village (F2P progression from Lumbridge)


## PM Audit — 2026-06-28

### Scope
Nei reviewed the current OpenRune docs, recent git history, kanban state, and live compile status to establish a strict PM workflow.

### Findings
- Kanban has no open todo/ready/running/blocked items, but the repo is not green.
- `:content:areas:city:lumbridge:compileKotlin` passes.
- `:content:quest:compileKotlin` fails in `RuneMysteries.kt` line 43 because it tries to access private `Player.questState` from `QuestScript`.
- Older `.hermes/plans/2026-06-20_rsmod-*` roadmap docs are stale for this OpenRune 239 tree and overstate F2P quest completion.

### Docs Updated
- Added `docs/pm-audit-and-workflow.md` as the PM operating state and strict workflow.
- Updated `docs/nei-handoff.md` to point at the PM audit and corrected the Rune Mysteries compile diagnosis.

### Next
Fix Rune Mysteries compile gate first, then full compile, then Lumbridge Layer 10 runtime verification before opening Draynor work.


## Template / Workflow Research — 2026-06-28

### Scope
Nei researched corpus and osrs-mcp surfaces to strengthen the zone template and PM workflow without creating kanban cards.

### Evidence
- Corpus files inventoried: `npc_directory.json`, `npc_spawns.json`, `shops.json`, `quest_lookup.json`, `item_by_name.json`, `monster_by_name.json`, `varbit_index.json`.
- osrs-mcp tool surface reviewed: `wiki_npc_spawns`, `gameval_search`, `cache_search`, reload tools.
- Lumbridge research snapshot: 142 broad NPC-directory matches, 7 shop matches, 377 raw NPC spawns / 112 unique NPC symbols, 55 ground object spawns, 27 zone Kotlin files, 41 direct registered NPC/LOC symbols.
- Draynor seed snapshot: 53 broad NPC-directory matches, 8 shop matches, `draynor.toml` 88 NPC spawns / 58 unique symbols, `draynor_manor_forest.toml` 41 NPC spawns / 19 unique symbols, 15 total ground item spawns across Draynor files.

### Docs Updated
- `docs/zone-workflow.md`: added Layer 0 evidence packet, evidence states, corpus/raw-cache/osrs-mcp source map, gap schema, classification rules, coverage rules, Lumbridge/Draynor research examples.
- `docs/pm-audit-and-workflow.md`: added mandatory research gate before implementation task specs.

### Phase 2 — Fishing (COMPLETE — March 29, 2026)
Fishing skill module created with full F2P implementation:

**Files created/updated:**
- `content/skills/fishing/build.gradle.kts` — added pluginCommons dependency
- `content/skills/fishing/src/main/kotlin/.../FishingModule.kt` — PluginModule binding
- `content/skills/fishing/src/main/kotlin/.../scripts/Fishing.kt` — main fishing script

**Implementation:**
- Adapted from rev 233 Fishing.kt — converted from `onOpNpc1(FishingNpcs.*)` to `onOpNpc1("npc.0_50_50_freshfish")` (239 string-based style)
- Follows 239 Woodcutting pattern for tick loop (actionDelay/skillAnimDelay)
- Freshwater spot (net: shrimp/anchovies, bait: sardine/herring) bound to `npc.0_50_50_freshfish`
- Saltwater spot (cage: lobster, harpoon: tuna/swordfish) bound to `npc.0_50_49_saltfish`
- Inline data tables for FishingTool, FishCatch, SpotAction (companion object, 233 style)
- Uses 239 API: `statRandom("stat.fishing", ...)`, `statAdvance("stat.fishing", ...)`, `invAdd(inv, "obj.xxx")`, `inv.contains("obj.xxx")`

**Compilation:** `./gradlew :content:skills:fishing:compileKotlin` — BUILD SUCCESSFUL (3s)

**Runtime notes:**
- Basic fishing items (nets, rods, raw fish, bait) need registration in `items.toml` before runtime testing
- Fishing spot NPC spawns need to be placed in Lumbridge area content
- See Fishing.kt TODO comments for future expansions (lure/bait spots, P2P fish, spot despawn)

### Next
Use the research gate for the Rune Mysteries repair package and for the later Draynor template run. No kanban cards created yet per Josh's direction.
