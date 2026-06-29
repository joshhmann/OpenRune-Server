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
