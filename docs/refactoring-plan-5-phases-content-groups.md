# Refactoring Plan: 5 Ported Phases → Content-Group + TOML-Param Architecture

**Date:** 2026-06-29
**Author:** Agent (architectural analysis)
**Target:** OpenRune 239 (rsmod Kotlin architecture)
**Scope:** 5 phases ported from rsmod-233 that use hardcoded data tables instead of OpenRune's data-driven content-group + TOML-param architecture.

---

## Current Architecture (What We Have)

Our 5 ported phases use inline companion-object data tables (233 style). OpenRune's architecture replaces hardcoded data with:

1. **Content groups** (`.data/gamevals/content.rscm`) — tag locs/NPCs/items with group IDs
2. **TOML params** (`.data/raw-cache/server/*.toml`) — data stored as param key-value pairs on locs/items/npcs
3. **`onOpContent*` handlers** (`onOpContentLoc1`, `onOpContentNpc1`, `onOpContentHeld1`, etc.) — bind all entities in a group via one registration
4. **DBTable rows** (for complex data like cooking foods, smithing bars, firemaking logs) — game-packed DB tables with Kotlin row accessors

### Available Content Groups (Existing)

From `.data/gamevals/content.rscm`:
```
food=38             — for food items (currently unused by our code)
potion=39           — for potion items (currently unused)
ore=31              — for mining rocks (USED by MiningScript.kt ✅)
tree=32             — for trees (used by Woodcutting ✅)
mining_pickaxe=51   — for pickaxe items (USED ✅)
woodcutting_axe=26  — for axe items (used by Woodcutting ✅)
person=21           — for generic person NPCs
banker=17           — for banker NPCs
sheep=41            — for sheep NPCs
cooking_range_standard=48, cooking_range_lumbridge=49, cooking_range_hosidius=50
```

### Available Scripting APIs

All verified from the codebase:
- `onOpContentLoc1("content.X")` — bind all locs with contentGroup X (Mining, Woodcutting)
- `onOpContentNpc1("content.X")` — bind all NPCs with contentGroup X (Sheep, GenericPerson)
- `onOpContentHeld1("content.X")` — bind all held items with contentGroup X
- `onOpContentHeldU("content.tool", "content.material")` — use tool on material (both item content groups)
- `onOpContentMixedLocU("content.X", "obj.Y")` — use item on content-group loc
- `onOpLocCategory1("category.X")` — bind all locs in a loc category (Smithing anvil)
- `locParam(params.*)` / `objParam(params.*)` — access TOML-defined params
- DBTable-driven: `CookingFoodsRow.all()`, `FiremakingLogsRow.all()`, `SmithingBarsRow.all()`

---

## Current Assessment: 5 Phases

### Phase 3 — Mining (MiningScript.kt) ✅ ALREADY CORRECT
- Uses `onOpContentLoc1("content.ore")` with content group
- Uses `locParam`/`objParam` for all rock/pickaxe data
- Correctly uses `isContentType("content.mining_pickaxe")` for tool checking
- **Only issue:** 12 of 13 ore product items and 7 gem items are missing from the 239 cache
- **What's needed:** Add missing ore/gem items to `items.toml` with correct content groups and params
- **No refactoring needed** — just cache data population

### Phase 2 — Fishing (Fishing.kt) ❌ NEEDS REFACTOR
- **Current:** Hardcoded `FishingTool`, `FishCatch`, `SpotAction` data classes in companion object
- **Current:** Uses `onOpNpc1`/`onOpNpc2` with specific NPC names
- **Target:** Use `onOpContentNpc1("content.fishing_spot")` + NPC params for spot data, OR DBTable-driven approach
- **Key challenge:** Fishing spots are NPCs, not locs — but `onOpContentNpc1` API exists and works
- **Cache issues:** All 5 tool items and 7 fish items missing from cache

### Phase 5 — Thieving (PickpocketScript, StallScript) ❌ NEEDS REFACTOR
- **Current:** Hardcoded `PickpocketEntry`, `StallEntry`, `PickpocketLoot`, `StallLoot` data classes in config objects
- **Current:** Uses `onOpNpc2` with specific NPC names (pickpocket) and `onOpLoc2`/`onOpLoc3` with specific locs (stalls)
- **Target (pickpocket):** Use `onOpContentNpc1("content.thievable_npc")` + NPC params for pickpocket data, OR DBTable
- **Target (stalls):** Use `onOpContentLoc1("content.stall")` + loc params for stall data
- **Cache issues:** 11 NPCs + 9 loot items missing; wrong goblin mapping

### Phase 1 — Consumables: Food (FoodScript.kt, Food.kt) ❌ NEEDS REFACTOR
- **Current:** Hardcoded `FoodEntry` data class + `FoodRegistry` companion object with ALL food data
- **Current:** Uses `onOpHeld2("obj.foo")` for each food item individually
- **Target:** Use `onOpContentHeld2("content.food")` + item params for heal amount, OR DBTable-driven like Cooking
- **Cache issues:** 22 of 25 food items missing from cache
- **Note:** `content.food=38` already exists in content.rscm but is unused by our code

### Phase 1 — Consumables: Potions (PotionScript.kt, Potion.kt) ❌ NEEDS REFACTOR
- **Current:** Hardcoded `PotionEffect`, `PotionType` data classes + `PotionRegistry` companion object
- **Current:** Uses `onOpHeld2("obj.foo")` for each potion dose individually
- **Target:** Use `onOpContentHeld2("content.potion")` + item params for effect data, OR DBTable
- **Cache issues:** All 38 potion dose variants completely missing; naming convention mismatched
- **Note:** `content.potion=39` already exists but is unused

### Phase 4 — Crafting: Leatherworking (LeatherworkingEvents.kt) ❌ NEEDS REFACTOR
- **Current:** Hardcoded `LeatherItem` enum + `StuddedItem` enum with all product data
- **Current:** Uses `onOpHeldU("obj.needle", "obj.leather")` with specific items
- **Target:** Use `onOpContentHeldU("content.crafting_tool", "content.leather_material")` + item params
- **Cache issues:** 5 crafting inputs + 1 NPC missing; some products exist
- **Leatherworking is the most complex:** Requires multi-step (tan → craft → stud)

### Other Crafting Sub-phases (not in 5-phase scope but relevant)
- GemCuttingEvents.kt, PotteryEvents.kt, SpinningEvents.kt, JewelryEvents.kt — all use hardcoded enums
- These should be refactored alongside Leatherworking for consistency

---

## Refactoring Order Recommendation

### Phase 0: Data Population (Pre-requisite for ALL phases)

Before ANY Kotlin refactoring, populate the 239 cache with missing items. The audit found ~73 missing item symbols across all phases. Without items in `items.toml`, no phase can function at runtime.

**Content groups that need CREATING** (add to `.data/gamevals/content.rscm`):

| Group Name | Proposed ID | Used By | Notes |
|------------|------------|---------|-------|
| `fishing_spot` | 52 | Fishing | NPC content group for fishing spot NPCs |
| `fishing_tool` | 53 | Fishing | Item content group for nets/rods/pots/harpoons |
| `thievable_npc` | 54 | Thieving (pickpocket) | NPC content group for pickpocketable NPCs |
| `stall` | 55 | Thieving (stalls) | Loc content group for market stalls |
| `crafting_tool` | 56 | Crafting | Item content group for needle, shears, hammer |
| `crafting_material` | 57 | Crafting | Item content group for leather, thread, studs, etc. |
| `spinning_wheel` | 58 | Crafting (spinning) | Loc content group for spinning wheels |
| `pottery_wheel` | 59 | Crafting (pottery) | Loc content group for pottery wheels |
| `pottery_oven` | 60 | Crafting (pottery) | Loc content group for pottery ovens |
| `furnace` | 61 | Crafting (jewelry), Smelting | Loc content group for furnaces (or reuse smithing's) |
| `crafting_food` | — | Cooking (future) | Already have `food=38` |

**Params that need DEFINING** (used in TOML, accessed via `params.*`):

| Param Name | Used By | Type | Notes |
|------------|---------|------|-------|
| `param.fishing_tool_anim` | Fishing | SequenceServerType | Animation for the fishing tool |
| `param.fishing_tool_bait` | Fishing | ItemServerType | Bait item (optional) |
| `param.fishing_spot_catches` | Fishing | Enum or custom | Which catches this spot offers |
| `param.fish_level_req` | Fishing | Int | Level required for this fish |
| `param.fish_xp` | Fishing | Double | XP per catch |
| `param.fish_success_low` | Fishing | Int | Success rate low bound |
| `param.fish_success_high` | Fishing | Int | Success rate high bound |
| `param.pickpocket_level` | Thieving | Int | Level required to pickpocket |
| `param.pickpocket_xp` | Thieving | Double | XP per pickpocket |
| `param.pickpocket_stun_ticks` | Thieving | Int | Stun duration on failure |
| `param.pickpocket_stun_damage` | Thieving | Int | Damage on failure |
| `param.pickpocket_success_base` | Thieving | Int/Enum | Base success rate |
| `param.pickpocket_loot_table` | Thieving | Enum or struct | Weighted loot table |
| `param.stall_empty_loc` | Thieving | ObjectServerType | Depleted stall loc type |
| `param.stall_respawn_ticks` | Thieving | Int | Respawn delay for stall |
| `param.food_heal` | Consumables | Int | HP healed by food |
| `param.food_delay` | Consumables | Int | Eat delay in ticks (3 normal, 1 combo) |
| `param.food_replacement` | Consumables | ItemServerType | Replacement item (pizza→half) |
| `param.potion_dose_4` | Consumables | ItemServerType | 4-dose variant |
| `param.potion_dose_3` | Consumables | ItemServerType | 3-dose variant |
| `param.potion_dose_2` | Consumables | ItemServerType | 2-dose variant |
| `param.potion_dose_1` | Consumables | ItemServerType | 1-dose variant |
| `param.potion_stat` | Consumables | String | Which stat to boost/restore |
| `param.potion_boost_constant` | Consumables | Int | Flat boost amount |
| `param.potion_boost_percent` | Consumables | Int | Percentage boost |

**Several of these params already exist** (used by mining/woodcutting):
- `param.levelrequire` — already exists (used by mining, woodcutting)
- `param.skill_xp` — already exists
- `param.skill_productitem` — already exists
- `param.skill_anim` — already exists (used on pickaxe/axe items)
- `param.next_loc_stage` — already exists (depleted variations)
- `param.deplete_chance` — already exists
- `param.respawn_time` / `param.respawn_time_low` / `param.respawn_time_high` — already exist

### Phase 1: Fishing (Easiest refactoring — NPC content group pattern)

**Why first:** Simplest pattern. Fishing spots are already NPCs with cache entries. The `onOpContentNpc1` API is proven (Sheep, GenericPerson use it). Fishing is a core early-game loop.

**Dependencies:** Need `content.fishing_spot` and `content.fishing_tool` content groups + items/params in TOML.

**Refactoring steps:**
1. Add `content.fishing_spot=52` and `content.fishing_tool=53` to content.rscm
2. Tag fishing spot NPCs with `contentGroup = "content.fishing_spot"` in npcs.toml
3. Tag fishing tool items with `contentGroup = "content.fishing_tool"` in items.toml
4. Add catch data as params on fishing spot NPCs (or use DBTable)
5. Rewrite Fishing.kt:
   - Replace `onOpNpc1("npc.0_50_50_freshfish")` with `onOpContentNpc1("content.fishing_spot")`
   - Replace hardcoded `FishingTool`/`FishCatch`/`SpotAction` with `npcParam()` lookups
   - Get tool requirements, catches, animations from params
6. Add all missing fishing items to items.toml

### Phase 2: Thieving — Stalls (Loc content group pattern)

**Why second:** Stalls are locs, perfect for `onOpContentLoc1`. Simpler than pickpocket (NPC-based).

**Dependencies:** Need `content.stall` content group + stall params in loc.toml.

**Refactoring steps:**
1. Add `content.stall=55` to content.rscm
2. Tag stall locs with `contentGroup = "content.stall"` in loc.toml
3. Add params for level, xp, empty loc, respawn ticks, loot to stall locs
4. Rewrite StallScript.kt:
   - Replace `onOpLoc3("loc.silkthiefstall")` with `onOpContentLoc1("content.stall")`
   - Replace hardcoded `StallEntry`/`StallLoot` with `locParam()` lookups
5. Add missing NPCs and loot items to cache

### Phase 3: Thieving — Pickpocket (NPC content group pattern)

**Why third:** Similar to fishing pattern. Pickpocket targets are NPCs.

**Dependencies:** Need `content.thievable_npc` content group + NPC params.

**Refactoring steps:**
1. Add `content.thievable_npc=54` to content.rscm
2. Tag pickpocketable NPCs with `contentGroup = "content.thievable_npc"` in npcs.toml
3. Add pickpocket params (level, xp, stun, success rate, loot) to NPC toml
4. Rewrite PickpocketScript.kt:
   - Replace `onOpNpc2("npc.man")` with `onOpContentNpc1("content.thievable_npc")`
   - Replace hardcoded `PickpocketEntry`/`PickpocketLoot` with `npcParam()` lookups
5. Fix goblin mapping (remove `npc.goblin_unarmed_melee_1..8` from pickpocket map)
6. Add missing NPCs and loot items to cache

### Phase 4: Consumables — Food (Item content group pattern)

**Why fourth:** `content.food=38` already exists in content.rscm. Food items just need tagging + params.

**Dependencies:** Items must exist in cache. Food build on Fishing (fish must be cookable).

**Refactoring steps:**
1. Tag food items with `contentGroup = "content.food"` in items.toml
2. Add heal amount, delay, replacement params to food items in items.toml
3. Rewrite FoodScript.kt + Food.kt:
   - Replace `onOpHeld2("obj.shrimp")` with `onOpContentHeld2("content.food")`
   - Remove `FoodEntry` data class and `FoodRegistry` — use `objParam()` lookups
   - Keep anglerfish special-case logic (dynamic heal) as hardcoded exception
4. Add all missing food items to items.toml

### Phase 5: Consumables — Potions (Item content group + DBTable pattern)

**Why fifth:** `content.potion=39` exists but potions have complex dose tracking. Potions are lowest priority for F2P gameplay loop.

**Dependencies:** Items must exist. No gameplay dependency on other phases.

**Refactoring steps:**
1. Tag potion items with `contentGroup = "content.potion"` in items.toml
2. Add dose-chain params to potion items (which item is next dose, effect params)
3. Rewrite PotionScript.kt + Potion.kt:
   - Replace `onOpHeld2("obj.attack_potion4")` with `onOpContentHeld2("content.potion")`
   - Remove `PotionEffect`/`PotionType` data classes and `PotionRegistry`
   - Use `objParam()` for effect data
   - Dose tracking via `getNextDoseName` from params
4. Add all missing potion items to items.toml (59 items)

### Phase 6: Crafting — Leatherworking + sub-crafting (Mixed content group + U patterns)

**Why last:** Most complex — multi-step process, uses both NPC and item interactions, and depends on items existing.

**Dependencies:** Items must exist (leather, needle, thread, studs, cowhide). Ellis NPC must be in cache.

**Refactoring steps:**
1. Add `content.crafting_tool=56` and `content.crafting_material=57` to content.rscm
2. Tag items with content groups in items.toml
3. Tag crafting locs (spinning wheels, pottery wheels, ovens, furnaces) with loc content groups
4. Rewrite LeatherworkingEvents.kt:
   - Replace `onOpHeldU("obj.needle", "obj.leather")` with `onOpContentHeldU("content.crafting_tool", "content.crafting_material")`
   - Replace `LeatherItem`/`StuddedItem` enums with param lookups
   - Ellis tanning: use `onOpContentNpc1("content.crafting_tanner")` or keep specific NPC
5. Similarly refactor GemCuttingEvents.kt, PotteryEvents.kt, SpinningEvents.kt, JewelryEvents.kt

---

## Dependencies Between Phases

```
Phase 0: Data Population (cache: items.toml, npcs.toml, loc.toml, content.rscm)
├── Phase 1: Fishing ──────────────────► Phase 4: Food (provides raw fish)
│                                              │
├── Phase 2: Stalls (standalone)                │
├── Phase 3: Pickpocket (standalone)            │
│                                                ▼
└── Phase 6: Crafting (standalone)     Phase 5: Potions (standalone)
```

**Key dependency chain:**
1. Phase 0 (data) → ALL phases — without items in cache, nothing works
2. Fishing → Food — Fishing provides raw fish that FoodScript cooks
3. Mining (already done) → Smithing (already done) — already architected, just needs ore items
4. All other phases are standalone — they don't depend on each other

**Parallelization opportunities:**
- Phases 1 (Fishing), 2 (Stalls), 3 (Pickpocket) can be done in parallel — all use content groups, no inter-dependencies
- Phase 4 (Food) and Phase 5 (Potions) can be done in parallel — both use item content groups
- Phase 6 (Crafting) is standalone but most complex — can be done last or in parallel with consumables

---

## Recommended Sprint Order

### Sprint A: Data Foundation + Mining Fix (Week 1)
**Gate:** Build must stay green
1. Add missing content groups to `content.rscm` (fishing_spot, fishing_tool, thievable_npc, stall, crafting_tool, crafting_material, etc.)
2. Add params to `param.rscm` (or equivalent params definition)
3. Add ALL missing items (~73) to `items.toml` with proper names matching 239 convention
4. Add missing NPCs to `npcs.toml`
5. Add missing locs to `loc.toml` (stall variants, depleted stall variants)
6. Tag existing items/locs/NPCs with content groups
7. Fix Mining: Add ore products + gems to items.toml (12 items) — Mining will then be fully functional

### Sprint B: Fishing + Stalls (Week 2)
**Gate:** Phase 0 complete
1. Refactor Fishing.kt → `onOpContentNpc1("content.fishing_spot")` + params
2. Refactor StallScript.kt → `onOpContentLoc1("content.stall")` + params
3. Both can be done in parallel by separate workers
4. Compile + test

### Sprint C: Pickpocket + Food (Week 2-3)
**Gate:** Sprint B compile green
1. Refactor PickpocketScript.kt → `onOpContentNpc1("content.thievable_npc")` + params
2. Refactor FoodScript.kt + Food.kt → `onOpContentHeld2("content.food")` + params
3. Fix goblin NPC mapping bug
4. Both in parallel
5. Compile + test

### Sprint D: Potions + Crafting (Week 3-4)
**Gate:** Sprint C compile green
1. Refactor PotionScript.kt + Potion.kt → `onOpContentHeld2("content.potion")` + params
2. Refactor LeatherworkingEvents.kt → `onOpContentHeldU("content.crafting_tool", "content.crafting_material")` + params
3. Refactor GemCuttingEvents.kt, PotteryEvents.kt, SpinningEvents.kt, JewelryEvents.kt
4. Compile + runtime test all phases

### Sprint E: Integration Testing + Polish (Week 4)
1. Full server compile: `./gradlew :server:app:compileKotlin`
2. Server boot + runtime walkthrough for each refactored skill
3. Fix any remaining issues
4. Update all docs (worklog, deferred-items, layer tables)

---

## Coordination Plan

### Roles (per AGENTS.md)
- **Mai (dispatch lead):** In charge of Phase 0 data work — defining content groups, params, populating TOML files. This is the enabling work that unblocks everything.
- **Tai (workers):** Implement Kotlin refactoring per phase. Each phase is a single task card.
- **Rei (QA):** Audit each refactored phase against:
  - RSCM symbol correctness (no dangling references)
  - Runtime behavior matches OSRS
  - No regressions in existing skills
- **Nei (PM):** Track phase gates, update docs, manage sprint boundaries.

### Worker Dispatch Strategy
- **Phase 0 + Mining fix:** 1 worker (highest priority — data foundation)
- **Sprint B (Fishing + Stalls):** 2 workers in parallel
- **Sprint C (Pickpocket + Food):** 2 workers in parallel
- **Sprint D (Potions + Crafting):** 2-3 workers (more complex)
- **Sprint E (Integration):** All workers + QA

### Gate Enforcement
Each phase must pass these gates:
1. `./gradlew :content:skills:<phase>:compileKotlin` — BUILD SUCCESSFUL
2. `./gradlew :server:app:compileKotlin` — BUILD SUCCESSFUL
3. Full server boot (no crashes on startup)
4. Runtime walkthrough: Functional test with actual items/locs in world
5. Docs updated: worklog-239.md, this refactoring plan status

### Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Content group ID conflicts** (two teams add same ID) | Build/runtime crash | Assign ID ranges per phase (Fishing: 52-59, Thieving: 54-55, Crafting: 56-65, etc.) |
| **Missing params break existing skills** | Compile fail or runtime crash | Add params to central param.rscm before any Kotlin refactoring |
| **Item naming convention mismatch** | Runtime failure (items not found) | Cross-reference all obj.* names against items.toml; never invent names |
| **Runtime regression in existing phases** | Mining/woodcutting stop working | Run full compile before merging any phase |
| **Phase takes longer than sprint** | Schedule slip | Decompose further; defer potions or gem cutting to next sprint |
| **NPC content groups not supported** | Architecture can't apply | Fallback: Use NPC category system or keep per-NPC `onOpNpc1` but move data to TOML params |

---

## Summary: What Needs Creating

### New Content Groups (in content.rscm)
```
fishing_spot=52
fishing_tool=53
thievable_npc=54
stall=55
crafting_tool=56
crafting_material=57
spinning_wheel=58
pottery_wheel=59
pottery_oven=60
furnace=61
```

### New Params (in param.rscm and TOML files)
See params table above (Phase 0 section). Many can reuse existing params (`param.levelrequire`, `param.skill_xp`, `param.skill_productitem`, `param.skill_anim`).

### Cache Data to Populate
- ~73 missing item symbols (foods, potions, ores, gems, fish, fishing tools, crafting inputs)
- ~11 missing NPCs (pickpocket targets, Ellis)
- ~5+ missing locs (stall variants, depleted stall variants)
- Content group tagging on all relevant entities

### Kotlin Files to Refactor
1. `content/skills/fishing/scripts/Fishing.kt` — rewrite to content-group + param pattern
2. `content/skills/thieving/scripts/StallScript.kt` — rewrite to content-group + param pattern
3. `content/skills/thieving/scripts/PickpocketScript.kt` — rewrite to content-group + param pattern
4. `content/skills/thieving/configs/PickpocketTargets.kt` — **DELETE** (data moves to TOML)
5. `content/skills/thieving/configs/StallData.kt` — **DELETE** (data moves to TOML)
6. `content/other/consumables/food/FoodScript.kt` — rewrite to content-group + param pattern
7. `content/other/consumables/food/Food.kt` — **DELETE** (data moves to TOML)
8. `content/other/consumables/potions/PotionScript.kt` — rewrite to content-group + param pattern
9. `content/other/consumables/potions/Potion.kt` — **DELETE** (data moves to TOML)
10. `content/skills/crafting/scripts/LeatherworkingEvents.kt` — rewrite to content-group + param pattern
11. `content/skills/crafting/scripts/GemCuttingEvents.kt`, `PotteryEvents.kt`, `SpinningEvents.kt`, `JewelryEvents.kt` — follow same pattern

### Kotlin Files to KEEP as-is (already correct)
- `content/skills/mining/scripts/MiningScript.kt` — ✅ Already content-group architected, just needs ore items in cache
