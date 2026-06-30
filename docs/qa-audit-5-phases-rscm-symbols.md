# QA Audit: RSCM Symbol Mismatches in 5 Ported Phases (rsmod-233 → OpenRune 239)

**Date:** 2026-06-29
**Auditor:** Rei (QA Agent)
**Scope:** Phase 1 (Consumables), Phase 2 (Fishing), Phase 3 (Mining), Phase 4 (Crafting/Leatherworking), Phase 5 (Thieving/Pickpocketing)

## Methodology

1. Extracted all `obj.*`, `npc.*`, `seq.*`, `synth.*`, `stat.*`, `content.*`, `loc.*` RSCM string references from the 5 source files.
2. Verified each against `.data/raw-cache/server/items.toml` (5141 items), `.data/raw-cache/server/npcs.toml` (116 NPCs), `.data/raw-cache/server/loc.toml` (object/server locs), `.data/raw-cache/server/stats.toml`, `.data/gamevals/content.rscm`, `.data/gamevals/synth.rscm`, and `.data/gamevals-binary/gamevals.dat` (binary sequences).
3. Checked map spawn files in `.data/raw-cache/map/npcs/` for live NPC presence.

---

## PHASE 5 — Thieving / Pickpocketing

### Source: `PickpocketTargets.kt`, `PickpocketScript.kt`

### NPC RSCM references (PickpocketTargets.npcToEntry)

| NPC RSCM | In npcs.toml? | In Map Spawns? | Issue |
|---|---|---|---|
| `npc.man` | ✅ | ✅ (Lumbridge, varrock, edgeville, draynor) | OK |
| `npc.man2` | ✅ | Not spawned | OK (fallback) |
| `npc.man3` | ✅ | Not spawned | OK (fallback) |
| `npc.man_indoor` | ✅ | Not spawned | OK (fallback) |
| `npc.woman` | ✅ | ✅ | OK |
| `npc.woman2` | ✅ | Not spawned | OK (fallback) |
| `npc.woman3` | ✅ | Not spawned | OK (fallback) |
| `npc.farmer1` | ✅ | Not spawned in Lumbridge | OK |
| `npc.farmer1_f` | ✅ | Not spawned | OK |
| `npc.farmer2` | ✅ | Not spawned | OK |
| `npc.farmer2_f` | ✅ | Not spawned | OK |
| `npc.farmer3` | ✅ | Not spawned | OK |
| `npc.farmer3_f` | ✅ | Not spawned | OK |
| `npc.farmer4` | ✅ | Not spawned | OK |
| ❌ `npc.ham_member` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.ham_guard` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.al_kharid_warrior` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.rogue` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.cave_goblin` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| `npc.goblin_unarmed_melee_1..8` | ✅ | ✅ (Lumbridge) | OK |
| `npc.goblin_armed` | ✅ | Not spawned | OK (fallback) |
| ❌ `npc.master_farmer` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.city_guard` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.guard` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.knight_of_ardougne` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.paladin` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.hero` | **MISSING** | N/A | **FAIL — not in 239 cache** |
| ❌ `npc.ellis` | **MISSING** | N/A | **FAIL — not in 239 cache** *(referenced by LeatherworkingEvents)* |

### Pickpocket Loot Item References

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| `obj.coins` | ✅ | OK |
| ❌ `obj.bolts` | **MISSING** | **FAIL — not in 239 cache** (man loot) |
| ❌ `obj.potato_seed` | **MISSING** | **FAIL** (farmer, master farmer loot) |
| ❌ `obj.digsitebuttons` | **MISSING** | **FAIL** (HAM member loot) |
| `obj.bronze_dagger` | ✅ | OK (HAM member, Al-Kharid warrior loot) |
| `obj.iron_knife` | ✅ | OK (HAM guard loot) |
| `obj.leather_gloves` | ✅ | OK (HAM guard loot) |
| ❌ `obj.lockpick` | **MISSING** | **FAIL** (rogue loot) |
| ❌ `obj.iron_ore` | **MISSING** | **FAIL** (cave goblin loot) |
| ❌ `obj.strawberry_seed` | **MISSING** | **FAIL** (master farmer loot) |
| ❌ `obj.ranarr_seed` | **MISSING** | **FAIL** (master farmer loot) |
| ❌ `obj.lawrune` | **MISSING** | **FAIL** (knight of ardougne loot) |
| ❌ `obj.earthrune` | **MISSING** | **FAIL** (menaphite thug loot) |
| `obj.chaosrune` | ✅ | OK (paladin loot) |
| `obj.bloodrune` | ✅ | OK (hero loot) |
| ❌ `obj.ruby` | **MISSING** | **FAIL** (hero loot) |

### Animation References

| Seq RSCM | In gamevals? | Issue |
|---|---|---|
| `seq.human_pickpocket` | ✅ (binary: 881) | OK |

### Handler Registration

- PickpocketScript wraps each `onOpNpc2` register in `runCatching` → **safe fallback**, missing NPCs silently skipped.
- **But:** the `npc.goblin_unarmed_melee_1..8` are mapped to `CAVE_GOBLIN` (level 36, XP 40) — these are the **Lumbridge regular goblins**, not Cave Goblins. Regular goblins in OSRS are NOT pickpocketable (level requirement does not apply — they simply should not have a pickpocket option). **WRONG NPC mapping** — these should not be mapped to any pickpocket entry at all, or at minimum should be mapped to a correct low-level entry only if intended as custom content.

---

## PHASE 2 — Fishing

### Source: `Fishing.kt`

### Fishing Spot NPCs

| NPC RSCM | In npcs.toml? | In Map Spawns? | Issue |
|---|---|---|---|
| `npc.0_50_50_freshfish` | ✅ | ✅ (Lumbridge, 2 spawns) | OK |
| `npc.0_50_49_saltfish` | ✅ | ✅ (Kingdom of Misthalin, 5 spawns) | OK |

### Fishing Tool Items

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.small_fishing_net` | **MISSING** | **FAIL — tool item not in 239 cache** |
| ❌ `obj.fishing_rod` | **MISSING** | **FAIL** |
| ❌ `obj.fishing_bait` | **MISSING** | **FAIL** |
| ❌ `obj.lobster_pot` | **MISSING** | **FAIL** |
| ❌ `obj.harpoon` | **MISSING** | **FAIL** |

### Fish Items

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.raw_shrimp` | **MISSING** | **FAIL** |
| ❌ `obj.raw_anchovies` | **MISSING** | **FAIL** |
| ❌ `obj.raw_sardine` | **MISSING** | **FAIL** |
| ❌ `obj.raw_herring` | **MISSING** | **FAIL** |
| ❌ `obj.raw_lobster` | **MISSING** | **FAIL** |
| ❌ `obj.raw_tuna` | **MISSING** | **FAIL** |
| ❌ `obj.raw_swordfish` | **MISSING** | **FAIL** |

### Animation References

| Seq RSCM | In gamevals? | Issue |
|---|---|---|
| `seq.human_smallnet` | ✅ (binary: 621) | OK |
| `seq.human_fish_onspot` | ✅ (binary: 623) | OK |
| `seq.human_lobster` | ✅ (binary: 619) | OK |
| `seq.human_harpoon` | ✅ (binary: 618) | OK |

### Handler Registration Issue

- The fishing spot NPCs exist, but **none of the tool items or fish items exist in the 239 cache**.
- Players at Lumbridge swamp will see the fishing spot NPC, op it → the handler fires → `inv.contains("obj.small_fishing_net")` returns false → "You need a small fishing net to fish here." → **entire skill is non-functional**.
- Error in description: "Players at Lumbridge swamp can't fish shrimp properly — get 'need level 40' (lobster level)" — this is an old 233 issue; the 239 code uses a lowest-req check which would be level 1 for shrimp. The actual problem is **all items are missing**, so fishing simply doesn't work at all.

---

## PHASE 1 — Consumables: Food

### Source: `Food.kt`, `FoodScript.kt`

### All Food Item References — NONE exist in 239 cache

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.shrimp` | **MISSING** | **FAIL** |
| ❌ `obj.sardine` | **MISSING** | **FAIL** |
| `obj.cooked_chicken` | ✅ | OK |
| `obj.cooked_meat` | ✅ | OK |
| ❌ `obj.herring` | **MISSING** | **FAIL** |
| ❌ `obj.mackerel` | **MISSING** | **FAIL** |
| ❌ `obj.trout` | **MISSING** | **FAIL** |
| ❌ `obj.cod` | **MISSING** | **FAIL** |
| ❌ `obj.pike` | **MISSING** | **FAIL** |
| ❌ `obj.salmon` | **MISSING** | **FAIL** |
| ❌ `obj.tuna` | **MISSING** | **FAIL** |
| ❌ `obj.lobster` | **MISSING** | **FAIL** |
| ❌ `obj.bass` | **MISSING** | **FAIL** |
| ❌ `obj.swordfish` | **MISSING** | **FAIL** |
| ❌ `obj.monkfish` | **MISSING** | **FAIL** |
| ❌ `obj.shark` | **MISSING** | **FAIL** |
| `obj.bread` | ✅ | OK |
| ❌ `obj.plain_pizza` | **MISSING** | **FAIL** |
| ❌ `obj.half_plain_pizza` | **MISSING** | **FAIL** |
| ❌ `obj.meat_pizza` | **MISSING** | **FAIL** |
| ❌ `obj.half_meat_pizza` | **MISSING** | **FAIL** |
| ❌ `obj.anchovy_pizza` | **MISSING** | **FAIL** |
| ❌ `obj.half_anchovy_pizza` | **MISSING** | **FAIL** |
| ❌ `obj.cooked_karambwan` | **MISSING** | **FAIL** |
| ❌ `obj.anglerfish` | **MISSING** | **FAIL** |

### Animation Reference

| Seq RSCM | In gamevals? | Issue |
|---|---|---|
| `seq.human_eat` | ✅ (binary: 829) | OK |

### Handler Registration

- FoodScript wraps each `onOpHeld2` in `runCatching` → **safe fallback**. Items not in cache are silently skipped.
- **Only 3 food items actually work:** `obj.cooked_chicken`, `obj.cooked_meat`, `obj.bread`.
- The 239 cache is missing almost ALL fish, pizza, karambwan, and anglerfish item definitions.

---

## PHASE 1 — Consumables: Potions

### Source: `Potion.kt`, `PotionScript.kt`

### All Potion Item References — NONE exist in 239 cache

| Item RSCN | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.vial` | **MISSING** | **FAIL** |
| ❌ `obj.energy_potion4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.attack_potion4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.strength_potion4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.defence_potion4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.prayer_potion4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.super_attack4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.super_strength4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.super_defence4/3/2/1` | **MISSING** | **FAIL** |
| ❌ `obj.4doseantipoison` etc. | **MISSING** | **FAIL** |
| ❌ `obj.4dose2antipoison` etc. | **MISSING** | **FAIL** |
| ❌ `obj.4doseantidote+` etc. | **MISSING** | **FAIL** |
| ❌ `obj.4doseantidote++` etc. | **MISSING** | **FAIL** |
| ❌ `obj.4doseantivenom` etc. | **MISSING** | **FAIL** |
| ❌ `obj.4doseantivenom+` etc. | **MISSING** | **FAIL** |

### Handler Registration

- PotionScript wraps each `onOpHeld2` in `runCatching` → **safe fallback**.
- **NONE of the potion items exist in the 239 cache.** Potions are completely non-functional.

### Naming Convention Issue

The 233 source used names like `obj.energy_potion4` (4-dose energy potion). The 239 cache uses entirely different naming patterns for items (e.g., `obj.91_energy_transfer` for the Lunar spell). It does not follow the `_potion4` / `_potion3` convention. All names are **strictly wrong** for this cache.

---

## PHASE 4 — Crafting / Leatherworking

### Source: `LeatherworkingEvents.kt`

### Leather Item References

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| `obj.leather` | ✅ | OK |
| ❌ `obj.hard_leather` | **MISSING** | **FAIL — not in 239 cache** |
| ❌ `obj.cow_hide` | **MISSING** | **FAIL — not in 239 cache** |
| ❌ `obj.needle` | **MISSING** | **FAIL — not in 239 cache** |
| ❌ `obj.thread` | **MISSING** | **FAIL — not in 239 cache** |
| ❌ `obj.studs` | **MISSING** | **FAIL — not in 239 cache** |
| ❌ `obj.coins` | ✅ | OK |
| `obj.leather_gloves` | ✅ | OK |
| `obj.leather_boots` | ✅ | OK |
| `obj.leather_cowl` | ✅ | OK |
| `obj.leather_vambraces` | ✅ | OK |
| `obj.leather_armour` | ✅ | OK |
| `obj.leather_chaps` | ✅ | OK |
| `obj.hardleather_body` | ✅ | OK |
| `obj.studded_body` | ✅ | OK |
| `obj.studded_chaps` | ✅ | OK |

### Crafting Item Status

| Product | Leather input | Product exists? | Inputs exist? | Functional? |
|---|---|---|---|---|
| Leather gloves | `obj.leather` | ✅ | `obj.leather` ✅, `obj.thread` ❌ | ❌ (thread missing) |
| Leather boots | `obj.leather` | ✅ | same | ❌ |
| Leather cowl | `obj.leather` | ✅ | same | ❌ |
| Leather vambraces | `obj.leather` | ✅ | same | ❌ |
| Leather body | `obj.leather` | ✅ | same | ❌ |
| Leather chaps | `obj.leather` | ✅ | same | ❌ |
| Hard leather body | `obj.hard_leather` | ✅ | `obj.hard_leather` ❌, `obj.thread` ❌ | ❌ |
| Studded body | `obj.leather_armour` | ✅ | `obj.leather_armour` ✅, `obj.studs` ❌, `obj.thread` ❌ | ❌ |
| Studded chaps | `obj.leather_chaps` | ✅ | `obj.leather_chaps` ✅, `obj.studs` ❌, `obj.thread` ❌ | ❌ |

### NPC Reference

| NPC RSCM | In npcs.toml? | Issue |
|---|---|---|
| ❌ `npc.ellis` | **MISSING** | **FAIL — tanner NPC not in 239 cache** |

### tanning Items

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.cow_hide` | **MISSING** | **FAIL — needed for tanning** |

### Animation Reference

| Seq RSCM | In gamevals? | Issue |
|---|---|---|
| `seq.human_leather_crafting` | ✅ (binary: 1249) | OK |

### Handler Registration Issue

- `onOpNpc1("npc.ellis")` is wrapped in `runCatching` → safe fallback, but Ellis doesn't exist.
- `onOpHeldU("obj.needle", "obj.leather")` → `obj.needle` doesn't exist → `runCatching` not used here, but `onOpHeldU` API may gracefully handle missing items.
- The products (leather_gloves, body, chaps, etc.) exist in the cache, but **none of the crafting inputs exist** (needle, thread, studs, cow_hide, hard_leather). Leatherworking is **completely non-functional**.

---

## PHASE 3 — Mining

### Source: `MiningScript.kt`

### Content References

| Content RSCM | In content.rscm? | Issue |
|---|---|---|
| `content.ore` | ✅ (id: 31) | OK |
| `content.mining_pickaxe` | ✅ (id: 51) | OK |

### Stat References

| Stat RSCM | In stats.toml? | Issue |
|---|---|---|
| `stat.mining` | ✅ | OK |
| `stat.fishing` | ✅ | OK |

### Pickaxe Items — All 8+ pickaxes exist with correct params

| Item | In items.toml? | pickaxeMiningReq anim? |
|---|---|---|
| `obj.bronze_pickaxe` | ✅ | level 1, `seq.human_mining_bronze_pickaxe` |
| `obj.iron_pickaxe` | ✅ | level 1, `seq.human_mining_iron_pickaxe` |
| `obj.steel_pickaxe` | ✅ | level 6, `seq.human_mining_steel_pickaxe` |
| `obj.black_pickaxe` | ✅ | level 11 |
| `obj.mithril_pickaxe` | ✅ | level 21 |
| `obj.adamant_pickaxe` | ✅ | level 31 |
| `obj.rune_pickaxe` | ✅ | level 41 |
| `obj.dragon_pickaxe` | ✅ | level 61 |

All pickaxes have `contentGroup = 51` (= `content.mining_pickaxe`) and `param.skill_anim` with correct seq names.

### Ore Rocks (Locations) — Use `content.ore` group

**Issue: Ores referenced by skill_productitem do NOT exist in items.toml**

| Product Item | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.clay` | **MISSING** | **FAIL** |
| ❌ `obj.copper_ore` | **MISSING** | **FAIL** |
| ❌ `obj.tin_ore` | **MISSING** | **FAIL** |
| ❌ `obj.iron_ore` | **MISSING** | **FAIL** |
| ❌ `obj.coal` | **MISSING** | **FAIL** |
| ❌ `obj.blurite_ore` | **MISSING** | **FAIL** |
| ❌ `obj.silver_ore` | **MISSING** | **FAIL** |
| `obj.gold_ore` | ✅ | OK |
| ❌ `obj.mithril_ore` | **MISSING** | **FAIL** |
| ❌ `obj.adamantite_ore` | **MISSING** | **FAIL** |
| ❌ `obj.runite_ore` | **MISSING** | **FAIL** |
| ❌ `obj.blankrune` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_sapphire` | **MISSING** | **FAIL** (gem rock product) |

### Gem Drop Items

| Item RSCM | In items.toml? | Issue |
|---|---|---|
| ❌ `obj.uncut_opal` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_jade` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_red_topaz` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_sapphire` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_emerald` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_ruby` | **MISSING** | **FAIL** |
| ❌ `obj.uncut_diamond` | **MISSING** | **FAIL** |

### Sound Reference

| Synth RSCM | In synth.rscm? | Issue |
|---|---|---|
| `synth.pillory_wrong` | ✅ (id: 2277) | OK |

### Summary

Mining has the best infrastructure (data-driven with correct content groups, loc params, pickaxes all set up), but **12 out of 13 ore product items and all 7 gem items are missing** from the cache. Only `obj.gold_ore` exists. Mining will function mechanically but `invAdd()` will fail trying to add non-existent items.

---

## Cross-Phase Issues

### Missing NPCs Across All Phases

| Missing NPC | Needed By | Reason |
|---|---|---|
| `npc.ellis` | LeatherworkingEvents | Tanner interface |
| `npc.ham_member` | PickpocketTargets | HAM pickpocketing |
| `npc.ham_guard` | PickpocketTargets | HAM guard pickpocketing |
| `npc.al_kharid_warrior` | PickpocketTargets | Warrior pickpocketing |
| `npc.rogue` | PickpocketTargets | Rogue pickpocketing |
| `npc.cave_goblin` | PickpocketTargets | Cave goblin pickpocketing |
| `npc.master_farmer` | PickpocketTargets | Master farmer pickpocketing |
| `npc.guard` / `npc.city_guard` | PickpocketTargets | Guard pickpocketing |
| `npc.knight_of_ardougne` | PickpocketTargets | Knight pickpocketing |
| `npc.paladin` | PickpocketTargets | Paladin pickpocketing |
| `npc.hero` | PickpocketTargets | Hero pickpocketing |

### Missing Core Items Across All Phases

Total: **73 missing item symbols** of ~85 referenced.

### Wrong NPC Mapping (Design Bug)

- `npc.goblin_unarmed_melee_1..8` (Lumbridge goblins) mapped to `CAVE_GOBLIN` pickpocket entry (level 36, XP 40). Regular goblins should NOT be pickpocketable in OSRS. This is either:
  1. A **wrong mapping** — these should not be in the map at all, OR
  2. A **custom content decision** — if deliberate, they should have a separate low-level entry (e.g., level 1-5, minimal loot).

### Safe Registration Pattern

The following phases use `runCatching` wrappers, preventing crashes:
- ✅ **PickpocketScript**: `runCatching { registerHandler(npcId, entry) }` — missing NPCs silently skipped
- ✅ **FoodScript**: `runCatching { onOpHeld2(food.itemName) { ... } }` — missing items silently skipped
- ✅ **PotionScript**: `runCatching { onOpHeld2(potionName) { ... } }` — missing items silently skipped

The following phases do NOT use `runCatching`:
- ⚠️ **Fishing (Fishing.kt)**: Has handler registration without runCatching but `onOpNpc1`/`onOpNpc2` reference NPCs that DO exist. The item checks happen at runtime via `inv.contains()` — graceful failure, just "You need X" messages.
- ⚠️ **LeatherworkingEvents.kt**: `onOpNpc1("npc.ellis")` (only has runCatching), BUT `onOpHeldU` for items that don't exist may cause issues.
- ⚠️ **MiningScript.kt**: No runCatching, but content-driven with loc params. The `resolveOreProduct()` function calls `RSCM.getReverseMapping(RSCMType.OBJ, type.rockOre.id)` which will throw if the item ID is invalid. If `skill_productitem` param references a non-existent item, this **will crash**.

---

## Severity Summary

| Phase | RSCM Issues | Runtime Impact | Severity |
|---|---|---|---|
| **Phase 1 (Food)** | 22/25 items missing | Complete failure for fish/pizza/karambwan; only chicken/meat/bread work | 🔴 HIGH |
| **Phase 1 (Potions)** | All 38 item variants missing | Complete failure — no potions work at all | 🔴 HIGH |
| **Phase 2 (Fishing)** | All tool items & fish missing | Complete failure despite NPCs existing — "need item" mes loop | 🔴 HIGH |
| **Phase 3 (Mining)** | 12/13 ores + 7 gems missing | Runtime crash on invAdd if ore is mined (null ItemServerType?) | 🔴 HIGH |
| **Phase 4 (Leatherworking)** | 5 crafting inputs + 1 NPC missing | Complete failure — no crafting possible, tanner unreachable | 🟠 MEDIUM |
| **Phase 5 (Thieving)** | 11 NPCs + 9 loot items missing + wrong goblin mapping | Man/woman/farmer/goblins work. Most high-level targets missing | 🟠 MEDIUM |

### Root Cause

The 239 cache is a very early build with basic items for Lumbridge/F2P tutorial only. The 5 phases were ported from rsmod-233 assuming a much more complete item database. The **naming conventions** (e.g., `obj.energy_potion4`, `obj.raw_shrimp`) are incompatible with the 239 cache's naming pattern.

### Recommendations

1. **Data-first**: Add missing items to `items.toml` with correct naming matching the 239 convention before attempting to use these phases.
2. **Mining crash fix**: Add null-safety around `resolveOreProduct()` or populate ore items.
3. **Goblin mapping fix**: Remove or re-map `npc.goblin_unarmed_melee_1..8` and `npc.goblin_armed` — they should NOT be mapped to CAVE_GOBLIN entry.
4. **Prioritize phase enablement**: Phase 3 (Mining) has the best infrastructure and needs the fewest items added (just ores). Phase 5 (Thieving) man/woman/farmer/goblin routes already work with existing NPCs.
