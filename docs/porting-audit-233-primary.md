# QA Porting Audit — 233 as PRIMARY Direct-Port Source

**Date:** June 29, 2026
**Author:** Rei
**Target:** OpenRune 239 (rev 239 cache, rsmod Kotlin architecture)
**Primary Source:** `osrs-ps-dev-source/rsmod/` (rev 233 content — 19 skills, 22 quests, mechanics, teleports, consumables)
**Secondary Source:** `current_development/rsmod-238/` (rev 238 — closer to 239, Draynor area content)
**Design Reference:** Kronos (legacy Java, mechanics reference only)

---

## Source Comparison Summary

| Dimension | 233 Source | 238 Source | 239 Target Missing |
|-----------|-----------|-----------|-------------------|
| **Skills** | 18 implemented + 1 utils | 4 (1 with files) | 8 missing skills |
| **Quests** | 21 implemented + 1 stub | 1 implemented + 1 stub | 20 missing quests |
| **Mechanics** | 12 systems | 5 systems | 7 missing systems |
| **Areas/cities** | 12 cities + dungeons | 3 cities | 9 cities missing |
| **Other systems** | bosses, consumables, npc-drops, teleports, windmill | consumables, npc-drops, windmill | consumables, npc-drops, etc. |

---

## Ironman F2P Quest Guide Order (Josh's progression)

From `progression_timeline.json` (Oziris v4), early-game quest order:
1. X Marks the Spot
2. Cook's Assistant
3. Sheep Shearer
4. The Restless Ghost
5. Rune Mysteries
6. Demon Slayer
7. Shield of Arrav
8. Dwarf Cannon (members, skip for F2P)
9. Prince Ali Rescue
10. Dragon Slayer I
11. The Knight's Sword
12. Doric's Quest
13. Goblin Diplomacy
14. Imp Catcher
15. Witch's Potion
16. Ernest the Chicken
17. Vampyre Slayer
18. Romeo & Juliet
19. Pirate's Treasure
20. Black Knights' Fortress
21. Below Ice Mountain
22. Misthalin Mystery
23. The Corsair Curse

Zone priorities follow from quest locations: Lumbridge → Draynor → Varrock → Al Kharid → Falador → Port Sarim → Rimmington → Karamja → Edgeville → Barbarian Village → Mining Guild.

---

## TIER 0: Finish Milestone 0 — Current Lumbridge Walkthrough

**Goal:** Get the current server green and through Layer 10 verification before starting porting.

| # | Item | SOURCE | EFFORT | BLOCKER | Notes |
|---|------|--------|--------|---------|-------|
| 0.1 | Fix Rune Mysteries startup (`Player.questState` access) | 239 local | Easy | None | Compile gate. Override `startup()` in QuestScript subclass |
| 0.2 | Full server compile + boot verification | 239 local | Easy | 0.1 | `:server:app:compileKotlin` + boot |
| 0.3 | Lumbridge Layer 10 runtime walkthrough | 239 local | Medium | 0.2 | Verify all layers 1-9 evidence |
| 0.4 | Decide DEF-001 castle elf doors fix vs defer | 239 local | Hard | 0.3 | ConstantProvider fix or workaround |

**STATUS:** ⏳ In progress (0.1 nearly done per worklog). Dock T1-T4 work behind these.

---

## TIER 1: Highest Value/Effort Ports from 233 (Skills That Unblock Gameplay)

**Priority reasoning:** These are the 8 skills missing from 239 that directly unblock the ironman F2P gameplay loop. Ordered by gameplay unlock value + portability.

| # | Item | SOURCE | EFFORT | BLOCKER | NPC/Area Dependencies | Notes |
|---|------|--------|--------|---------|----------------------|-------|
| **1.1** | **Mining** | **233** | **Medium** | None | Dwarven Mine, Lumbridge swamp, Varrock SE mine, Al Kharid mine, Falador mine, Mining Guild, Rimmington mine | **HIGHEST VALUE**. Mining unblocks smithing (already in 239). 5 .kt files in 233: pickaxes, rocks, ore veins, rock depletion/respawn. Straightforward port — `onOpLoc` tick-loop pattern like woodcutting. Adapt IDs to 239 RSCM symbols. |
| **1.2** | **Thieving** | **233** | **Easy** | None | Draynor (stalls), Varrock (stalls/pickpocket), Al Kharid (stalls), Edgeville (men) | 1 .kt file in 233. Pickpocket + stalls + wall safes. Needed early for ironman GP (25 Thieving required early). Small file = fast port. |
| **1.3** | **Crafting** | **233** | **Medium** | None | Al Kharid (furnace/glass), Rimmington (pottery), Lumbridge (spinning wheel), Crafting Guild | 7 .kt files in 233: Leatherworking, Jewelry, Pottery, Spinning, Glassblowing (via configs), Gem Cutting. Needed for jewelry, leather armor, bow strings. |
| **1.4** | **Fletching** | **233** | **Medium** | None | Varrock (fletching shop), Rimmington | 3 .kt files in 233: arrows/bows/darts/bolts. Needed for ranged weapon progression. |
| **1.5** | **Fishing (complete)** | **233** | **Easy** | None | Lumbridge swamp (net), Draynor (net), Barbarian Village (fly), Karamja (harpoon/cage) | 239 has empty fishing dir. 233 has 1 .kt file. Small port but critical for early game loop with cooking. |
| **1.6** | **Ranged skill** | **233** | **Easy** | 1.4 (Fletching provides ammo) | Lumbridge (Ranged Combat Tutor already exists) | 3 .kt files in 233. Bow/arrow tier configs, ranged strengths. The `ranged` mechanic (in 238) should also be ported alongside for combat calc. |
| **1.7** | **Agility** | **233** | **Hard** | Towers/obstacle Locs must exist in 239 cache | Draynor Rooftop, Al Kharid Rooftop, Varrock Rooftop, Barbarian Outpost | 6 .kt files (base + 4 courses + 1). F2P only has Draynor Rooftop course. Skill matters for run energy and quest access. **HIGH EFFORT due to obstacle/location ID mapping.** |
| **1.8** | **Construction (F2P-limited)** | **233** | **Medium** | None | Estate agents (Varrock/Falador) | 2 .kt files. F2P can only buy house but not build much. Lower early-game value. |
| **1.9** | **Hunter (F2P-limited)** | **233** | **Medium** | None | None (members skill; F2P can walk through but not train) | 1 .kt file. Lowest F2P priority. Include for architecture but defer runtime testing. |
| **1.10** | **Farming (members)** | **233** | **Hard** | Much zone content | Allotment patches across Gielinor | Members skill. Defer. Only port the module structure, defer gameplay implementation. |

---

## TIER 2: Quest + Mechanic Ports (Content Density)

**Priority reasoning:** 233 has 21 fully-implemented F2P quests (2-4 .kt files each). Ports are direct — same architecture, same patterns. Order follows ironman guide + zone progression.

### Phase 2A — Draynor Area Quests + Content (Next zone after Lumbridge)

| # | Quest | SOURCE | EFFORT | BLOCKER | Zone | Notes |
|---|-------|--------|--------|---------|------|-------|
| 2.1 | **X Marks the Spot** | 233 | Easy | None | Lumbridge → Port Sarim | 2 .kt files. Very short quest, great starting quest for ironman guide. Fast port. |
| 2.2 | **Restless Ghost** | 233/238 | Easy | Draynor area NPCs | Lumbridge/Draynor Manor | 4 .kt files (identical in 233 and 238). Already partially started in 239's Lumbridge NPC set. |
| 2.3 | **Cook's Assistant** | 233 | Easy | Lumbridge NPCs | Lumbridge Castle | 3 .kt files. Already started in 239. Fast port. |
| 2.4 | **Sheep Shearer** | 233 | Easy | Fred/Farmer NPCs | Lumbridge (north) | 3 .kt files. Simple gather quest. |
| 2.5 | **Draynor Area NPCs** | 238 | Easy | Restless Ghost quest | Draynor Village | **238 is PREFERRED** — has more NPCs (Aggie, Wise Old Man, Father Urhney, Morgan, Ned, TownCrier) than 233. 10 files vs 8. |
| 2.6 | **Vampyre Slayer** | 238 | Medium | Draynor area + Morgan NPC | Draynor Village | 238 has the quest framework stub. 233 has 0 files for this quest. Use 238 as base. |

### Phase 2B — Varrock Area Quests

| # | Quest | SOURCE | EFFORT | BLOCKER | Zone | Notes |
|---|-------|--------|--------|---------|------|-------|
| 2.7 | **Rune Mysteries** | 233 | Easy | Varrock area (Aubury NPC), Wizards Tower | Varrock/Lumbridge | 3 .kt files. Already partially in 239 (Duke Horacio, Aubury, Sedridor handlers exist). Finalize port. |
| 2.8 | **Shield of Arrav** | 233 | Medium | Varrock area (Baraek, Charlie, Curator, etc.) | Varrock | 4 .kt files. Multi-path quest, needs Phoenix Gang + Black Arm Gang NPCs. HIGH DEPENDENCY on Varrock area NPCs. |
| 2.9 | **Demon Slayer** | 233 | Medium | Varrock area NPCs + Dark Wizards | Varrock | 3 .kt files. Needs Gypsy Aris, Sir Prysin, etc. |
| 2.10 | **The Knight's Sword** | 233 | Medium | Falador/White Knights, dwarven mines | Falador/Dwarven Mine | 4 .kt files. Medium complexity. |
| 2.11 | **Doric's Quest** | 233 | Easy | Doric NPC, Falador area | Falador (north) | 3 .kt files. Simple mining quest — but needs Mining skill (T1.1). |
| 2.12 | **Below Ice Mountain** | 233 | Medium | Ice Mountain area, Goblin Village | Ice Mountain | 2 .kt files. Newer quest (2022), ties into Misthalin Mystery. |
| 2.13 | **Misthalin Mystery** | 233 | Medium | Varrock Museum, various NPCs | Varrock | 4 .kt files. Longer F2P quest. |

### Phase 2C — Other F2P Quests

| # | Quest | SOURCE | EFFORT | BLOCKER | Zone | Notes |
|---|-------|--------|--------|---------|------|-------|
| 2.14 | **Prince Ali Rescue** | 233 | Medium | Al Kharid area NPCs (Osman, Leela, Hassan, etc.) | Al Kharid | 3 .kt files. |
| 2.15 | **Dragon Slayer** | 233 | Hard | Port Sarim (Melzar, Oziach), Crandor Island | Port Sarim/Crandor | 3 .kt files. Major quest, needs Crandor area content, dragon NPCs, Melzar's Maze — LOTS of zone dependencies. |
| 2.16 | **Goblin Diplomacy** | 233 | Easy | Goblin Village, Falador | Goblin Village | 3 .kt files. Simple goblin mail quest. |
| 2.17 | **Imp Catcher** | 233 | Easy | Wizards Tower area | Wizards Tower | 3 .kt files. |
| 2.18 | **Witch's Potion** | 233 | Easy | Rimmington, Witch's house | Rimmington | 3 .kt files. |
| 2.19 | **Ernest the Chicken** | 233 | Medium | Draynor Manor (levers/puzzle) | Draynor Manor | 3 .kt files. Puzzle-based, needs Draynor Manor interiors. |
| 2.20 | **Pirate's Treasure** | 233 | Medium | Port Sarim, Karamja, Redbeard Frank | Port Sarim/Karamja | 2 .kt files. |
| 2.21 | **Romeo & Juliet** | 233 | Easy | Varrock Square | Varrock | 4 .kt files. Mostly dialogue — straightforward. |
| 2.22 | **Black Knights' Fortress** | 233 | Medium | Falador, Black Knights | Falador | 3 .kt files. |
| 2.23 | **The Corsair Curse** | 233 | Medium | Corsair Cove (requires boat from Rimmington) | Corsair Cove | 3 .kt files. |
| 2.24 | **Vampyre Slayer** | 238 | Medium | Draynor area | Draynor | 0 files in 233. 238 has stub but no script. May need new implementation. |

### Phase 2D — Mechanics Ports

| # | Mechanic | SOURCE | EFFORT | BLOCKER | Notes |
|---|----------|--------|--------|---------|-------|
| 2.25 | **Trade** | 233 | Medium | None | 4 .kt files. Player-to-player trading. Companion object trade session. Important for ironman group mode. |
| 2.26 | **Ranged combat mechanic** | 238 | Medium | Ranged skill (1.6) | 4 .kt files in 238 (RangedModule, CannonObjs, RangedObjs, DwarfMulticannonScript). Port alongside the ranged skill. |
| 2.27 | **Status effects** | 233 | Medium | None | 2 .kt files. Poison, stat drain, etc. Needed for combat completeness. |
| 2.28 | **Time system** | 233 | Easy | None | 1 .kt file. Day/night cycle, growth timers. |
| 2.29 | **Consumables** | 233/238 | Easy | None | 5 .kt files. Identical in 233 and 238. Food + potions framework. Direct port. |
| 2.30 | **Stat restore** | 233 | Medium | Status effects (2.27) | Combat stat restoration after damage/potions. |

---

## TIER 3: Zone Completion Ports (Areas, NPCs, Shops)

**Priority reasoning:** Zone content follows ironman quest progression path. Each zone needs NPCs, shops, interactables, spawns. 233 and 238 have existing implementations.

| # | Zone | SOURCE | EFFORT | BLOCKER | Quest Connections | Notes |
|---|------|--------|--------|---------|------------------|-------|
| 3.1 | **Draynor Village** | **238** | **Medium** | T0 completed | Restless Ghost, Vampyre Slayer, Ernest Chicken, X Marks, Rune Mysteries | **238 preferred** — 10 files vs 233's 8. Has Aggie, WiseOldMan, FatherUrhney, Morgan, Ned, TownCrier. Unique NPCs not in 233. |
| 3.2 | **Draynor Manor** | 238 | Medium | 3.1 | Restless Ghost, Ernest Chicken | 4 files in 238 vs 3 in 233. Has DraynorManorScript + Locs + Npcs + NpcSpawns. |
| 3.3 | **Varrock** | 233 | Hard | 3.1 (Draynor done first) | Shield of Arrav, Demon Slayer, Rune Mysteries, Knights Sword, Romeo & Juliet, Misthalin Mystery, Below Ice Mountain | 18 files in 233. Highest quest density zone. |
| 3.4 | **Al Kharid** | 233 | Medium | T0 | Prince Ali Rescue, The Feud, Mining | 15 files in 233. Mining + crafting hotspot. |
| 3.5 | **Falador** | 233 | Medium | Varrock done | Doric's Quest, Knights Sword, Black Knights Fortress, Goblin Diplomacy | 15 files in 233. |
| 3.6 | **Port Sarim** | 233 | Medium | Falador done | Dragon Slayer, Pirate's Treasure, X Marks the Spot | 19 files in 233. High density. |
| 3.7 | **Rimmington** | 233 | Easy | Port Sarim done | Witch's Potion, Corsair Curse | 2 files in 233. Small zone. |
| 3.8 | **Karamja** | 233 | Medium | Port Sarim done | Dragon Slayer, Pirate's Treasure | 8 files in 233 + island version. |
| 3.9 | **Edgeville** | 233 | Easy | Varrock done | None major | 9 files in 233. |
| 3.10 | **Barbarian Village** | 233 | Easy | Edgeville done | None major | 2 files in 233. Small. |
| 3.11 | **Lumbridge Swamp** | 233 | Easy | Lumbridge done | None major | ~3 files in 233. |
| 3.12 | **Wizards Tower** | 233 | Easy | Draynor done | Rune Mysteries, Imp Catcher | ~3 files in 233. |
| 3.13 | **Mining Guild** | 233 | Easy | Falador done | None | 5 files in 233. |
| 3.14 | **Dungeons** | 233 | Medium | Various areas | Various | Edgeville dungeon, Varrock sewer, Draynor sewer, Dwarven Mine, Asgarnian Ice. |

### Dungeon Port Details (from 233)

| # | Dungeon | SOURCE | FILES | Notes |
|---|---------|--------|-------|-------|
| 3.14a | Edgeville Dungeon | 233 | ~3 | Edgeville teleport, Hill Giants, moss giants |
| 3.14b | Varrock Sewer | 233 | ~3 | Rune Mysteries related, zombies |
| 3.14c | Draynor Sewer | 233 | ~3 | Connected to Draynor Manor |
| 3.14d | Dwarven Mine | 233 | ~3 | Mining hub, Doric's Quest related |
| 3.14e | Asgarnian Ice | 233 | ~3 | Ice warriors, ice giants |

### Misc Area Ports (from 233)

| # | Misc Area | SOURCE | Notes |
|---|-----------|--------|-------|
| 3.15 | Champions' Guild | 233 | Quest point requirement |
| 3.16 | Cooking Guild | 233 | 32 Cooking req |
| 3.17 | Crafting Guild | 233 | 40 Crafting req |
| 3.18 | Essence Mine | 233 | Rune Mysteries related |
| 3.19 | Goblin Village | 233 | Goblin Diplomacy quest |
| 3.20 | Ice Mountain | 233 | Below Ice Mountain quest |
| 3.21 | Shantay Pass | 233 | Desert access |
| 3.22 | Dark Wizard Tower | 233 | Combat area |
| 3.23 | Crandor | 233 | Dragon Slayer quest |
| 3.24 | Outlaw Camp | 233 | Wilderness edge |
| 3.25 | Catherby | 233 | Members (lower priority) |
| 3.26 | F2P Wilderness | 233 | Volcanic, ruins, multiway |

---

## TIER 4: Polish + Stretch (Teleports, Clues, Windmill, etc.)

**Priority reasoning:** Nice-to-have systems that add polish. Not required for basic gameplay.

| # | Item | SOURCE | EFFORT | BLOCKER | Notes |
|---|------|--------|--------|---------|-------|
| 4.1 | **Magic teleports** | 233 | Medium | Magic skill (already in 239) | 3 .kt files in 233 (under skills/magic/teleports/): TeleportConfigs, TeleportScript, TeleportModule. Standard teleport spells. |
| 4.2 | **Windmill** | 233 | Medium | None | 3 .kt files in 233 vs 2 in 238. Hopper/flour bin mechanics. Lumbridge windmill for Cook's Assistant quest. |
| 4.3 | **Clue scrolls (F2P)** | 233 | Hard | Zone content (dig locations, emote clues) | 4 .kt files in 233. Easy clue steps, reward tables, scroll configs. HIGH effort due to coordinate mapping. |
| 4.4 | **NPC drops** | 238 | Medium | NPC combat (T3) | 238 has EXTENSIVE drop tables (28 files vs 233's 31 — 238 has broader variety). Port 238's version. |
| 4.5 | **NPC combat** | 238 | Medium | Status effects (2.27) | 7 files in 238. AggressiveNPC, Warrior, ManWoman, Wizard, ChaosDruid, JailGuard combat scripts. |
| 4.6 | **NPC spawns** | 233/238 | Easy | Area zones (T3) | Skeleton framework for NPC placement. |
| 4.7 | **Bosses** | 233 | Hard | NPC combat (4.5) | 4 files in 233. KBD + Kalphite Queen. Members content, low F2P priority. |
| 4.8 | **P2P blocking** | 233 | Medium | All zones (T3) | 1 file. Prevents members-only actions for F2P players. Important for live server integrity. |
| 4.9 | **Achievement diaries** | 233 | Hard | All zones + skills (T1-T3) | Members content. Defer. |
| 4.10 | **Random events** | 233 | Medium | None | Anti-bot random events. |
| 4.11 | **Poison mechanic** | 238 | Medium | Status effects (2.27) | Already partially in 238. |
| 4.12 | **Canoe travel** | 233/238 | Easy | None | Already in 239. Verify/complete if needed. |
| 4.13 | **Aggression mechanics** | 238 | Medium | None | NPC aggression zones. Already in 238. |

---

## Source Selection Decision Matrix

| Content Type | Recommended Source | Rationale |
|-------------|-------------------|-----------|
| **Skills** (missing 8) | **233** | 233 is the only source with all 8 missing skills. 238 only has 4 skills (mostly empty). |
| **Existing skills** (10 in 239) | **Keep 239, reference 233** | 239's versions may be more updated. Use 233 for bug fixes/reference. |
| **Quests** | **233** (primary), **238** (Vampyre Slayer) | 233 has 21/22 F2P quests implemented. 238 only has Restless Ghost + Vampyre Slayer stub. |
| **Draynor/Draynor Manor** | **238** | 238 has more complete NPC handler set + spawns. 8 files vs 10 (Draynor), 3 vs 4 (Manor). |
| **Lumbridge** | **Keep 239** | 239 already has 28 files of rich Lumbridge NPC content not in 233/238. |
| **Mechanics: Ranged** | **238** | Similar to 233's version but rev 238 is closer to 239. |
| **Mechanics: Trade** | **233** | 233 has 4 complete files. 238 has none. |
| **Mechanics: Clues/Status/Time** | **233** | Only 233 has these implementations. |
| **NPC Drops** | **238** | 238 has more extensive and varied drop tables (28 files). |
| **NPC Combat** | **238** | 238 has 7 files with variety. 233 has similar count (7). Use 238 for rev proximity. |
| **Consumables** | **Either (identical)** | Both 233 and 238 have identical 5-file sets. No difference. |
| **Windmill** | **233** | 233 has 3 files (includes WindmillScript). 238 only has 2. |
| **Teleports** | **233** | Only 233 has magic teleport spells implementation. |
| **Bosses** | **233** | Only 233 has KBD/KQ implementations. |
| **P2P Blocking** | **233** | Only 233 has this. |

---

## Estimated Total Effort

| Tier | Items | Est. Total Effort | Best Parallelization |
|------|-------|-------------------|---------------------|
| T0 | 4 | 1-2 days | Serial (blocking chain) |
| T1 | 10 skills | 3-5 weeks | Parallel: Mining+Thieving+Crafting+Fletching (4 devs) |
| T2 | 24 quests + 6 mechanics | 4-6 weeks | Parallel by zone (Draynor team, Varrock team) |
| T3 | ~26 zones | 4-6 weeks | Parallel by zone (after quest team dependencies) |
| T4 | ~13 stretch items | 2-3 weeks | Low priority, pick off individually |
| **TOTAL** | **~83 items** | **~14-22 weeks** | ~8-12 weeks with parallel teams |

---

## Key Risks & Mitigations

### Risk 1: Rev 233 → 239 ID/Symbol Drift
- **Problem:** 233 uses some numeric IDs/references that must be re-mapped to 239 RSCM symbols.
- **Mitigation:** Use the `tools/symbol-codegen/` and `tools/cache-symbol-audit/` to cross-reference. Most common symbols (`find("sym_name")`) should be stable between 233 and 239. Run compile after each port to catch drift.

### Risk 2: Object/NPC/Loc ID Shifts
- **Problem:** Some object IDs (trees, rocks, doors) may differ between rev 233 and rev 239 caches.
- **Mitigation:** Prefer `find("symbol_name")` lookups over numeric IDs. Where 233 uses hardcoded IDs, populate from the 239 cache dump first.

### Risk 3: Quest Interface/CS2 Script Mismatches
- **Problem:** Quest journals and progress tracking rely on CS2 scripts and varbits that may differ between revs.
- **Mitigation:** Prioritize quests with the simplest progression tracking first (Cook's Assistant, Sheep Shearer, Restless Ghost) to validate the pattern before tackling complex quests (Shield of Arrav, Dragon Slayer).

### Risk 4: API/Engine Changes Between Revs
- **Problem:** 233 was built against an older rsmod API; some API calls may not exist in 239.
- **Mitigation:** Check `api/` diffs between 233 and 239. The `content/` layer is the most portable — engine and API changes are isolated.

### Risk 5: 238 Draynor Files Reference Different API
- **Problem:** 238 is closer to 239's rev but still not identical. 238's build may use slightly different API signatures.
- **Mitigation:** Same as R1 — compile-test after each port. 238 should be the smoothest transition.

---

## Recommended Execution Plan

### Sprint 1 (Week 1-2): Foundation + Mining
- Finish T0 items (Lumbridge walkthrough)
- Port Mining (T1.1) — highest value, unblocks smithing
- Port Consumables (2.29) — needed for food in gameplay
- Port Time System (2.28) — needed for respawn/growth timers
- Compile + test

### Sprint 2 (Week 2-3): Core F2P Skills
- Port Thieving (T1.2), Crafting (T1.3), Fletching (T1.4)
- Port Fishing (T1.5), Ranged skill (T1.6)
- Port Ranged mechanic (2.26), Status effects (2.27)
- Compile + test

### Sprint 3 (Week 3-4): Draynor Zone + Initial Quests
- Port Draynor Village (3.1) from 238
- Port Draynor Manor (3.2) from 238
- Port X Marks the Spot (2.1), Restless Ghost (2.2), Cook's Assistant (2.3), Sheep Shearer (2.4)
- Compile + runtime test

### Sprint 4 (Week 4-6): Varrock Zone + Dense Quests
- Port Varrock (3.3) from 233
- Port Rune Mysteries (2.7), Shield of Arrav (2.8), Demon Slayer (2.9)
- Port Trade (2.25), Consumables potions (2.29)
- Port remaining F2P quests in batches
- Compile + runtime test

### Sprint 5 (Week 6-8): Remaining Zones + Completeness
- Port Al Kharid, Falador, Port Sarim
- Port remaining quests (Dragon Slayer, Prince Ali Rescue, etc.)
- Port extra skills: Agility (T1.7), Construction (T1.8)
- Port NPC drops + combat from 238
- Compile + runtime test

### Sprint 6+ (Week 8+): Polish
- Magic teleports (4.1), Windmill (4.2)
- Clue scrolls (4.3), P2P blocking (4.8)
- Bosses (4.7), Random events (4.10), Achievement diaries (4.9)
- Comprehensive QA + regression

---

**End of Porting Audit — Rei, June 29, 2026**
