# Architecture Audit: 5 Ported Phases vs. Upstream OpenRune Patterns

**Date:** 2026-06-29
**Auditor:** Architecture subagent
**Repo:** `/root/Runescape/open_rune/OpenRune-Server/`
**Upstream Reference:** https://github.com/OpenRune/OpenRune-Server

---

## 1. Upstream OpenRune Canonical Patterns

OpenRune has **five canonical data-driven patterns** that our ported code should follow:

### Pattern A — Content Group + TOML Params for Locs (THE PRIMARY PATTERN)

Used by: **Mining** (`content.ore`), **Woodcutting** (`content.tree`), Ladders, Doors, Staircases, Bank booths, Chicken coops.

```kotlin
// Kt script — register ONCE per content group, NO per-entity names
onOpContentLoc1("content.ore") { mine(it.loc, it.type) }
onOpContentLoc2("content.ore") { prospect(it.type) }
```

```toml
# loc.toml — ALL data lives here, NOT in Kotlin
[[object]]
id = "loc.copperrock1"
inherit = "loc.copperrock1"
contentGroup = "content.ore"

[object.params]
"param.levelrequire" = 1
"param.skill_xp" = 1750
"param.skill_productitem" = "obj.copper_ore"
"param.next_loc_stage" = "loc.rocks1"
"param.deplete_chance" = 32
"param.respawn_time" = 0
"param.respawn_time_low" = 3
"param.respawn_time_high" = 6
```

```kotlin
// Companion object — typed extension properties read from TOML params
val ObjectServerType.rockLevelReq: Int by locParam(params.levelrequire)
val ObjectServerType.rockOre: ItemServerType by locParam(params.skill_productitem)
val ObjectServerType.rockXp: Double by locXpParam(params.skill_xp)
val ObjectServerType.rockDepletedLoc: ObjectServerType by locParam(params.next_loc_stage)
val ObjectServerType.rockDepleteChance: Int by locParam(params.deplete_chance)
val ObjectServerType.rockRespawnTime: Int by locParam(params.respawn_time)
// Helper functions in companion
fun findPickaxe(player: Player): InvObj? { ... }
fun mineSuccessRate(type: ObjectServerType, pickaxe: ItemServerType): Pair<Int,Int> { ... }
```

### Pattern B — Content Group + TOML Params for NPCs

Used by: **Banker** (`content.banker`), **Sheep** (`content.sheep`), **Person** (`content.person`).

```kotlin
onOpContentNpc1("content.banker") { talkToBanker(it.npc) }
// NPCs tagged via contentGroup in NPC data files
```

### Pattern C — Content Group + TOML Params for Items

Used by: **Woodcutting axes** (`content.woodcutting_axe`), **Mining pickaxes** (`content.mining_pickaxe`).

```kotlin
onOpContentU("content.tree", "content.woodcutting_axe") { cut(it.loc, it.type) }
```

```kotlin
// objParam extension properties
val ItemServerType.pickaxeMiningReq: Int by objParam(params.levelrequire)
val ItemServerType.pickaxeMiningAnim: SequenceServerType by objParam(params.skill_anim)
// Content type check
fun ItemServerType.isUsablePickaxe(miningLevel: Int): Boolean =
    isContentType("content.mining_pickaxe") && miningLevel >= pickaxeMiningReq
```

### Pattern D — DBTable-driven Iteration

Used by: **Firemaking** (`FiremakingLogsRow`), **Fletching** (`FletchingBowCraftingRow`), **Herblore** (`HerbloreFinishedRow`), **Smithing** (`SmithingCannonBallsRow`).

```kotlin
// Auto-generated Row class from dbtable
FiremakingLogsRow.all().forEach { log ->
    onOpHeldU("obj.tinderbox", log.input) { startBurn(log, method = BurnMethod.Tinderbox) }
}
```

### Pattern E — SkillMulti (Multi-skill Interface)

Used by: **Crafting**, **Herblore**, **Fletching**, **Smithing**. This is the correct UI pattern but still needs data-driven backing.

### Content Groups Already Defined (content.rscm, IDs 0-51)

```
food=38
potion=39
ore=31
tree=32
woodcutting_axe=26
banker=17
bank_booth=18
sheep=41
cow=43
dairy_cow=44
chicken_coop=47
mining_pickaxe=51
cooking_range_standard=48
cooking_range_lumbridge=49
cooking_range_hosidius=50
// ...doors, ladders, stairs, empty containers, etc.
```

---

## 2. Phase-by-Phase Architecture Gap Analysis

---

### Phase 1: Consumables (FoodScript.kt + PotionScript.kt)

**Files:** `content/other/consumables/`

| Aspect | Current (Port) | Required (Upstream Pattern) |
|--------|---------------|---------------------------|
| **Registration** | `onOpHeld2(food.itemName)` per-item loop | `onOpHeld2` + `type.isContentType("content.food")` (content group lookups) |
| **Data location** | `FoodRegistry` / `PotionRegistry` companion objects | TOML item params in `items.toml` |
| **Heal amounts** | `FoodEntry.healAmount` in code | `param.heal_amount` on each food item |
| **Combo food flag** | `FoodEntry.isComboFood` in code | `param.combo_food` (bool/int) |
| **Replacement** | `FoodEntry.replacement` in code | `param.replacement_item` (string) |
| **Potion effects** | `PotionEffect` data class with all fields | `param.stat_boost`, `param.constant_boost`, `param.percent_boost`, `param.is_restore`, etc. |
| **Dose tracking** | `PotionType.doseNames` map | `param.dose_count` (4,3,2,1) + `param.next_dose_item` |
| **Anglerfish** | Hardcoded `calculateAnglerfishHeal()` | Keep as special-case override (acceptable) |
| **Available groups** | — | `content.food` (38), `content.potion` (39) already defined in content.rscm |

**What must change:**
1. Add `contentGroup = "content.food"` to all food items in `items.toml`
2. Add `contentGroup = "content.potion"` to all potion items in `items.toml`
3. Add item params to `items.toml`: `heal_amount`, `combo_food`, `replacement_item`, `dose_count`, `next_dose_item`
4. For potions: add params `stat_boost`, `constant_boost`, `percent_boost`, `is_restore`, `cures_poison`, `cures_venom`, `energy_restore_percent`, `poison_immunity_ticks`, `venom_immunity_ticks`
5. Rewrite FoodScript to use `onOpHeld2` with content group check, or iterate items from cache filtered by `isContentType("content.food")`
6. Rewrite PotionScript similarly
7. Delete `Food.kt` and `Potion.kt` (all data moves to TOML)
8. Add `objParam` extensions for food/potion params

---

### Phase 2: Fishing (Fishing.kt)

**Files:** `content/skills/fishing/scripts/Fishing.kt`

| Aspect | Current (Port) | Required (Upstream Pattern) |
|--------|---------------|---------------------------|
| **Registration** | `onOpNpc1("npc.0_50_50_freshfish")` with specific NPC names | `onOpContentNpc1("content.fishing_spot")` via NPC content group |
| **Tool data** | `FishingTool` data class in companion | Item params on fishing tools: `fishing_anim`, `fishing_bait` |
| **Catch data** | `FishCatch` data class in companion | Either loc params on fishing spots OR DBTable rows |
| **Spot action** | `SpotAction` pairing tool+catches in companion | TOML params on fishing spot NPCs |
| **Level reqs** | `FishCatch.levelReq` in code | `param.levelrequire` on spots |
| **XP values** | `FishCatch.xp` in code | `param.skill_xp` on spots |
| **Success rates** | `FishCatch.successLow/High` in code | Either param-based or computed from level/spot |

**What must change:**
1. Create new content group `content.fishing_spot` (ID 52) in `content.rscm`
2. Tag fishing spot NPCs with `contentGroup = "content.fishing_spot"` in NPC data files
3. Define fishing spot parameters in TOML (loc-like params on NPCs, or add fishing-specific params):
   - `param.levelrequire`, `param.skill_xp`, `param.skill_productitem`
   - `param.fishing_tool`, `param.fishing_bait`, `param.fishing_anim`
   - `param.catch_table` (reference to a DB table or embedded JSON)
4. Tag fishing tools with `content.fishing_tool` content group
5. Rewrite registration: `onOpContentNpc1("content.fishing_spot") { attemptFish(it.npc) }`
6. Move all data from companion `SpotAction`/`FishCatch`/`FishingTool` to TOML/DB
7. Delete companion object data classes (keep helper functions)

---

### Phase 3: Mining (MiningScript.kt) ✅ CLOSEST TO PATTERN

**Files:** `content/skills/mining/scripts/MiningScript.kt`

| Aspect | Current (Port) | Required (Upstream Pattern) |
|--------|---------------|---------------------------|
| **Registration** | `onOpContentLoc1("content.ore")` ✅ | ✅ Correct — matches upstream |
| **Content group** | `content.ore` ✅ | ✅ Already defined in content.rscm (31) |
| **Loc params** | `locParam`/`locXpParam` extensions ✅ | ✅ Correct — matches Woodcutting pattern |
| **Item params** | `objParam` extensions for pickaxes ✅ | ✅ Correct |
| **Content type check** | `isContentType("content.mining_pickaxe")` ✅ | ✅ Correct |
| **TOML data** | Rocks in `loc.toml` with all params ✅ | ✅ Correct |
| **Gem table** | Hardcoded `rollGemDrop()` with flat weights | Minor: could be TOML param, acceptable as-is |
| **Pickaxe tier bonus** | Hardcoded `when` block in companion | Should be `param.pickaxe_tier_bonus` on items |
| **Respawn calc** | `resolveRespawnTime()` in companion | Minor: could be param-driven, acceptable |

**What must change:**
1. Add `pickaxe_tier_bonus` param to items.toml for each pickaxe
2. Replace hardcoded `pickaxeTierBonus()` with `objParam(params.pickaxe_tier_bonus)` extension
3. (Optional) Move gem drop table to TOML param or DB table
4. Everything else is already upstream-correct — Mining is the model to emulate

---

### Phase 4: Crafting (LeatherworkingEvents.kt, GemCuttingEvents.kt, etc.)

**Files:** `content/skills/crafting/scripts/`

| Aspect | Current (Port) | Required (Upstream Pattern) |
|--------|---------------|---------------------------|
| **Leather registration** | `onOpNpc1("npc.ellis")`, `onOpHeldU("obj.needle", "obj.leather")` | DBTable-driven (like FletchingBowCraftingRow) or content groups |
| **Gem registration** | `onOpHeldU("obj.chisel", "obj.uncut_opal")` x8 | DBTable-driven (like Fletching) or content groups |
| **Data location** | `LeatherItem` enum, `GemType` enum, `StuddedItem` enum in code | DBTable rows or TOML item params |
| **Level reqs** | `item.levelReq` in enum | `param.levelrequire` on items or DB column |
| **XP values** | `item.xp` in enum | `param.skill_xp` on items or DB column |
| **Materials** | `item.leatherInternal` hardcoded in enum | DB columns: `primary_resource`, `secondary_resource` |
| **Tanning** | Hardcoded prices (1gp/3gp) | Could be param or DB table |

**What must change:**
1. Create `content.gem` content group (ID 55) for gems
2. Create `content.leather` content group (ID 56) for leather items
3. Create `content.hide` content group (ID 57) for animal hides
4. Create `content.spinning_material` content group (ID 60) for spinnables
5. Create `content.clay_product` content group (ID 61) for pottery
6. Create DB tables for each crafting sub-skill (following FletchingBowCraftingRow pattern):
   - `dbtable.gem_cutting` (uncut_item, cut_item, level_required, xp, can_crush)
   - `dbtable.leather_crafting` (leather_item, product_item, level_required, xp, materials_required)
   - `dbtable.spinning` (input_item, output_item, level_required, xp)
   - `dbtable.pottery` (unfired_item, fired_item, level_required, xp)
   - `dbtable.jewelry` (mould_item, product_item, level_required, xp, gems_allowed)
7. Rewrite each `*Events.kt` to iterate from DB rows: `GemCuttingRow.all().forEach { ... }`
8. Delete all enum-based data (LeatherItem, GemType, StuddedItem, etc.)

---

### Phase 5: Thieving (PickpocketScript.kt, StallScript.kt)

**Files:** `content/skills/thieving/`

| Aspect | Current (Port) | Required (Upstream Pattern) |
|--------|---------------|---------------------------|
| **Pickpocket registration** | `onOpNpc2("npc.man")` for each of 30 NPC names via map lookup | `onOpContentNpc1("content.pickpocket_target")` via content group |
| **Stall registration** | `onOpLoc3("loc.etc_veg_market")` for 40+ stall names via map | `onOpContentLoc3("content.stall")` via content group |
| **Data location** | `PickpocketTargets` companion, `StallData` companion | NPC/loc params in TOML |
| **Level reqs** | `PickpocketEntry.levelReq` | `param.thieving_level` on NPC/loc |
| **XP values** | `PickpocketEntry.xp` | `param.thieving_xp` on NPC/loc |
| **Success rates** | `baseSuccess` + `bonusPerLevel` | `param.thieving_success_base`, `param.thieving_success_bonus` |
| **Stun data** | `stunTicks`, `stunDamageMin/Max` | `param.thieving_stun_ticks`, `param.thieving_stun_damage_min/max` |
| **Loot tables** | `List<PickpocketLoot>` with weights | Complex: either DB table (thieving_loot) or JSON-encoded param |
| **Stall depletion** | `stall.emptyLoc`, `stall.respawnTicks` | `param.next_loc_stage`, `param.respawn_time` (reuse existing!) |
| **Tea stall** | Hardcoded op2 handling | Content group + op slot mapping |

**What must change:**
1. Create new content group `content.pickpocket_target` (ID 58) in `content.rscm`
2. Create new content group `content.stall` (ID 59) in `content.rscm`
3. Tag all pickpocketable NPCs with `contentGroup = "content.pickpocket_target"` in NPC data
4. Tag all thievable stalls with `contentGroup = "content.stall"` in `loc.toml`
5. Add TOML params to NPCs:
   - `param.thieving_level`, `param.thieving_xp`
   - `param.thieving_success_base`, `param.thieving_success_bonus`
   - `param.thieving_stun_ticks`, `param.thieving_stun_damage_min`, `param.thieving_stun_damage_max`
6. Add TOML params to stalls (reuse existing loc param infrastructure):
   - `param.thieving_level`, `param.thieving_xp`
   - `param.next_loc_stage` (empty stall) — **already exists!**
   - `param.respawn_time` — **already exists!**
7. Create DB table for loot tables: `dbtable.thieving_loot` (npc_id/stall_id, item_id, min, max, weight)
8. Rewrite PickpocketScript: `onOpContentNpc1("content.pickpocket_target") { pickpocket(it.npc) }`
9. Rewrite StallScript: `onOpContentLoc3("content.stall") { stealFromStall(it.loc) }`
10. Delete `PickpocketTargets.kt` and `StallData.kt` (all data moves to TOML/DB)
11. Use `locParam(params.thieving_level)` etc. for stall data
12. Create `npcParam` equivalents if they don't exist (or use a generic param system)

---

## 3. Summary: Content Groups and Params to Add

### New Content Groups (content.rscm)

| ID | Name | Used By |
|----|------|---------|
| 52 | `fishing_spot` | Fishing spot NPCs |
| 53 | `fishing_bait` | Bait items (optional) |
| 54 | `fishing_tool` | Fishing tool items |
| 55 | `gem` | Gem items (uncut/cut) |
| 56 | `leather` | Leather items |
| 57 | `hide` | Animal hides |
| 58 | `pickpocket_target` | Pickpocketable NPCs |
| 59 | `stall` | Thievable stalls |
| 60 | `spinning_material` | Spinnable items |
| 61 | `clay_product` | Pottery items |

### Items That Need `contentGroup` Set (items.toml)

- All food items: `contentGroup = "content.food"`
- All potion doses: `contentGroup = "content.potion"`
- Already set: mining pickaxes (`content.mining_pickaxe`), skill capes/hoods

### New TOML Params Needed

**Item params:**
| Param | Type | Used By |
|-------|------|---------|
| `param.heal_amount` | int | Food |
| `param.combo_food` | bool/int | Food (karambwan) |
| `param.replacement_item` | string | Food (pizza halves), potions (dose down) |
| `param.dose_count` | int | Potions (4,3,2,1) |
| `param.stat_boost` | string | Potions |
| `param.constant_boost` | int | Potions |
| `param.percent_boost` | int | Potions |
| `param.is_restore` | bool/int | Potions (prayer) |
| `param.cures_poison` | bool/int | Potions |
| `param.cures_venom` | bool/int | Potions |
| `param.energy_restore_percent` | int | Potions |
| `param.fishing_anim` | string | Fishing tools |
| `param.fishing_bait` | string | Fishing tools |
| `param.pickaxe_tier_bonus` | int | Pickaxes (replaces hardcoded `when` block) |

**NPC params:**
| Param | Type | Used By |
|-------|------|---------|
| `param.thieving_level` | int | Pickpocket targets |
| `param.thieving_xp` | double | Pickpocket targets |
| `param.thieving_success_base` | double | Pickpocket targets |
| `param.thieving_success_bonus` | double | Pickpocket targets |
| `param.thieving_stun_ticks` | int | Pickpocket targets |
| `param.thieving_stun_damage_min` | int | Pickpocket targets |
| `param.thieving_stun_damage_max` | int | Pickpocket targets |

**Loc params (reuse existing + add thieving):**
| Param | Type | Used By | Status |
|-------|------|---------|--------|
| `param.levelrequire` | int | Stalls | Already exists |
| `param.skill_xp` | double | Stalls | Already exists |
| `param.next_loc_stage` | string | Stall depletion | Already exists |
| `param.respawn_time` | int | Stall respawn | Already exists |

### New DB Tables

| Table | Columns | Purpose |
|-------|---------|---------|
| `dbtable.gem_cutting` | uncut_item, cut_item, level_required, xp, can_crush | Gem cutting |
| `dbtable.leather_crafting` | leather_item, product_item, level_required, xp, materials | Leatherworking |
| `dbtable.spinning` | input_item, output_item, level_required, xp | Spinning wheel |
| `dbtable.pottery` | unfired_item, fired_item, level_required, xp | Pottery |
| `dbtable.jewelry` | mould_item, product_item, level_required, xp, gem_slot | Jewelry making |
| `dbtable.thieving_loot` | npc_id/stall_id, item_id, min_amount, max_amount, weight | Loot tables |

---

## 4. Summary: Migration Priority

| Phase | Migration Effort | Current State | Priority |
|-------|-----------------|---------------|----------|
| **Phase 3 (Mining)** | Trivial (add 1 param) | ✅ **Already upstream-correct** | Low |
| **Phase 2 (Fishing)** | High (new NPC content groups, new params) | ❌ Hardcoded NPC names + data | Medium |
| **Phase 1 (Food/Potions)** | Medium (tag items, add params, rewrite scripts) | ❌ Hardcoded companion objects | Medium |
| **Phase 5 (Thieving)** | High (new content groups, NPC/loc params, loot DB table) | ❌ Hardcoded maps of 70+ entities | High |
| **Phase 4 (Crafting)** | High (multiple DB tables, complete rewrite of all sub-skills) | ❌ Hardcoded enums + item names | High |

**Key insight:** Phase 3 (Mining) is the model. All other phases should be refactored to match its `onOpContentLoc1` + TOML params + companion helper pattern. Each phase's companion object should contain ONLY helper functions (findTool, rollSuccess, rollLoot) — NEVER data constants.

---

## 5. Appendix: Existing Params System Available

From the upstream codebase, the following infrastructure is already available:

```kotlin
// locParam — reads params from loc.toml objects
val ObjectServerType.someField: Type by locParam(params.param_key)

// locXpParam — reads XP (double) params from loc.toml  
val ObjectServerType.someXp: Double by locXpParam(params.skill_xp)

// objParam — reads params from items.toml items
val ItemServerType.someField: Type by objParam(params.param_key)

// isContentType — checks contentGroup on any type
ItemServerType.isContentType("content.group")
ObjectServerType.isContentType("content.group")  
NpcServerType.isContentType("content.group")

// onOpContentLoc1/2/3 — register by loc content group
onOpContentLoc1("content.tree") { ... }
onOpContentLoc2("content.ore") { ... }
onOpContentLoc3("content.stall") { ... }

// onOpContentNpc1/2 — register by NPC content group
onOpContentNpc1("content.banker") { ... }

// onOpContentU — register by item+loc content groups
onOpContentU("content.tree", "content.woodcutting_axe") { ... }

// DBTable rows — auto-generated data access
FiremakingLogsRow.all().forEach { ... }
GemCuttingRow.all()  // would be generated after adding table

// Engine helpers (already used in some skills)
skillSuccess(low, high, level)  // success roll
statRandom("stat.fishing", low, high, invisibleLvls)  // level-scaled random
weakQueue("queue.name", ticks, task)  // tick-delayed execution queue
```

The `npcParam` extension does NOT currently exist in the codebase (NPCs don't seem to have a param system like locs/objs do). This would need to be created for pickpocket targets, or alternatively, the loot tables for thieving could be handled via DB tables.
