# RSMod Plugin Architecture → OSRS Data Mapping

## Architecture Overview

RSMod v2 is a Kotlin-based OSRS server emulator with a **modular plugin architecture**:

- **Engine layer** — core game systems (networking, collision, routing, game loop)
- **API layer** — plugin-facing interfaces (events, services, DI)
- **Content layer** — all game content as plugins (skills, NPCs, quests, shops)

Plugins are proper Kotlin classes extending `PluginScript` that use an **event bus** system:

```kotlin
class MyPlugin : PluginScript() {
    override fun ScriptContext.startup() {
        // Subscribe to events during startup
    }
}
```

Alternatively, in v1 (Tomm0017/rsmod, archived), plugins are `.plugin.kts` KotlinScript files
with free-function hooks like `on_obj_option`, `on_npc_option`, etc.

## Key Systems

### Event Bus (`engine/events/`)
Three event types:
- **`UnboundEvent`** — no key, broadcasts to all subscribers
- **`KeyedEvent`** — keyed by a `Long` ID (e.g., NPC ID, object ID, item ID)
- **`SuspendEvent<K>`** — keyed, with coroutine suspend support (for timed/scheduled actions)

### Entity IDs (Symbol Files)
All game entities are referenced by their **OSRS cache IDs** via `.sym` files in `.data/symbols/`:

| Symbol File | Contents | Size |
|---|---|---|
| `obj.sym` | Item IDs (947KB) | 16K+ items |
| `npc.sym` | NPC IDs (403KB) | ~7K NPCs |
| `loc.sym` | Object IDs (1.8MB) | ~30K objects |
| `seq.sym` | Animation IDs (368KB) | |
| `spotanim.sym` | Graphic IDs (113KB) | |
| `varbit.sym` | Varbit IDs (394KB) | |
| `varp.sym` | VarPlayer IDs (57KB) | |
| `inv.sym` | Inventory IDs (23KB) | |
| `stat.sym` | Skill IDs | |
| `interface.sym` | Interface IDs | |
| `component.sym` | Interface component IDs | |
| `enum.sym` | Enum IDs | |
| `param.sym` | Param IDs | |

### Content Module Structure
```
content/
├── skills/
│   ├── woodcutting/    build.gradle.kts + src/main/kotlin/...
│   ├── fishing/
│   ├── cooking/
│   └── magic/
├── areas/
│   ├── city/           City-specific plugins
│   └── misc/           Other areas/regions
├── generic/
│   ├── generic-locs/   Object interaction handlers
│   └── generic-npcs/   NPC interaction handlers
├── interfaces/          UI interface handlers
├── travel/              Transportation (spells, boats, etc.)
└── other/               Miscellaneous content
```


## Content Type Mapping → Automation Feasibility

### 1. DROP TABLES — 100% Automatable
**Our data**: `drops_by_source.json` (2,061 monster sources, 38,707 drop entries)
**Pattern**: Subscribe to NPC death event, roll against drop table

```kotlin
// Auto-generated: Abyssal demon drop table
class AbyssalDemonDrops : PluginScript() {
    override fun ScriptContext.startup() {
        eventBus.subscribeKeyed(NpcDeathEvent::class.java, npcId = 415) {
            val drops = listOf(
                Drop("Abyssal whip", itemId = 4151, rarity = 1.0/512, quantity = "1"),
                Drop("Abyssal dagger", itemId = 13265, rarity = 1.0/32768, quantity = "1"),
                Drop("Coins", itemId = 995, rarity = 1.0/8, quantity = "1-250"),
                // ... 60+ more drops
            )
            val rolled = rollDrops(drops)
            rolled.forEach { player.inventory.add(it.itemId, it.quantity) }
        }
    }
}
```

**What we need from our data**:
- Monster name → OSRS NPC ID (from `npc.sym` or `monster_by_name.json`)
- Item name → OSRS item ID (from `obj.sym` or `item_by_name.json`)
- Rarity string → probability conversion ("Common"=1/8, "Uncommon"=1/32, "Rare"=1/128, etc.)
- Quantity range parsing

**Challenge**: Rarity strings from OSRS wiki use qualitative terms ("Common", "Uncommon",
"Rare", "Very rare") with inconsistent actual probabilities. Need a lookup table per wiki
convention. Also, quantity strings like "1-3" or "3-5 (noted)" need parsing.

---

### 2. NPC / MONSTER DEFINITIONS — 100% Automatable
**Our data**: `monster_by_name.json` (1,372 monsters with complete combat stats)

```kotlin
// Auto-generated: Abyssal demon NPC definition
class NpcDefinitions : PluginScript() {
    override fun ScriptContext.startup() {
        registerNpc(npcId = 415) {
            name = "Abyssal demon"
            combatLevel = 124
            hitpoints = 150
            maxHit = 8
            attackSpeed = 4
            aggressive = true
            aggressiveRadius = 5
            stats {
                attack = 97; strength = 90; defence = 75
                ranged = 1; magic = 1
            }
            bonuses {
                stab = 0; slash = 0; crush = 0
                magic = 0; range = 0
            }
            slayer {
                level = 85
                category = "abyssal demon"
                xp = 264
            }
            drops = loadDropTable(415)
        }
    }
}
```

**What we have**: Exact combat levels, all offensive/defensive stats, slayer requirements,
attack styles, elemental weaknesses — all in `monster_compendium.json`.

**What we need**: RSMod's `NpcType` registration API (need to verify the exact method).

---

### 3. SHOPS — 100% Automatable
**Our data**: `shops.json` (508 shops with items, prices, stock)

```kotlin
// Auto-generated: Varrock Sword Shop
class VarrockSwordShop : PluginScript() {
    override fun ScriptContext.startup() {
        eventBus.subscribeKeyed(NpcInteractionEvent::class.java, npcId = 525) {
            if (option == "trade") {
                shop("Varrock Swordshop") {
                    currency = Items.COINS
                    restockTimer = 30  // seconds
                    stock(1215, amount = 5, buyPrice = 52, sellPrice = 30)    // Dragon dagger
                    stock(1301, amount = 3, buyPrice = 769, sellPrice = 30)   // Rune longsword
                    stock(1277, amount = 10, buyPrice = 35, sellPrice = 30)   // Bronze sword
                    // ... all items
                }
            }
        }
    }
}
```

**Our data fields**: item name → ID, sell/buy values, stock amounts, shop location → NPC.

**Challenge**: Need to map shop names from wiki to specific NPC IDs. Some shops are
objects (shelves, stalls) rather than NPCs.

---

### 4. SPELLBOOK — 100% Automatable
**Our data**: `spellbook.json` (201 spells across 6 books: standard, ancient, lunar,
arceuus, necromancy, skilling)

```kotlin
// Auto-generated: Standard spellbook
class StandardSpells : PluginScript() {
    override fun ScriptContext.startup() {
        registerSpell(Spellbook.STANDARD, spellId = 1) {
            name = "Wind Strike"
            level = 1
            type = SpellType.COMBAT
            runes = mapOf(Runes.AIR to 1, Runes.MIND to 1)
            xp = 5.5
            maxHit = 2
            animation = 1162
            graphic = 91
        }
        registerSpell(Spellbook.STANDARD, spellId = 27) {
            name = "High Level Alchemy"
            level = 55
            type = SpellType.UTILITY
            runes = mapOf(Runes.FIRE to 5, Runes.NATURE to 1)
            xp = 65.0
        }
    }
}
```

**Our data fields**: spell name, level, rune costs, XP, max hit, spellbook, type — all present.

**Challenge**: Need to understand RSMod's spell registration API (autocast, client
animations, spell IDs from cache).

---

### 5. CONSTRUCTION (POH) — 100% Automatable
**Our data**: `construction.json` (730 POH hotspot items)

Maps to POH hotspot definitions — level requirements, materials, XP, and room
placement for all 730 items across all POH rooms.

---

### 6. COMBAT ACHIEVEMENTS — 100% Automatable
**Our data**: `combat_achievements_by_tier.json` (637 achievements, 6 tiers)

Maps directly to RSMod achievement system — each entry has tier, task description,
requirement, and rewards.

---

### 7. MONEY MAKING GUIDES — Reference Only
**Our data**: `money_making.json` (627 methods with GP/hr, requirements, skill levels)

Not directly mappable to plugin code, but useful as **design reference** for server
economy balancing — shows what OSRS expects for GP rates at various levels.

---

### 8. SKILL TRAINING — 90% Automatable
**Our data**: 23 skill training HTML pages + XP table + quest skill requirements

```kotlin
// Auto-generated: Normal tree woodcutting
class NormalTreeWoodcutting : PluginScript() {
    override fun ScriptContext.startup() {
        // Tree object → logs mapping
        val treeData = listOf(
            TreeInfo(locId = 1276, name = "Tree", level = 1, xp = 25.0, logs = 1511, respawn = 4, animation = 879),
            TreeInfo(locId = 1278, name = "Oak", level = 15, xp = 37.5, logs = 1521, respawn = 5, animation = 879),
            TreeInfo(locId = 1286, name = "Willow", level = 30, xp = 67.5, logs = 1519, respawn = 6, animation = 879),
            // ... all trees
        )

        treeData.forEach { tree ->
            eventBus.subscribeKeyed(LocInteractionEvent::class.java, locId = tree.locId) {
                if (option == "chop") {
                    player.queue {
                        if (player.skills.level(Skills.WOODCUTTING) < tree.level) {
                            player.message("You need Woodcutting level ${tree.level} to cut this tree.")
                            return@queue
                        }
                        player.animate(tree.animation)
                        wait(tree.respawn.cycles)
                        player.animate(-1)
                        player.inventory.add(tree.logs, 1)
                        player.skills.addXp(Skills.WOODCUTTING, tree.xp)
                    }
                }
            }
        }
    }
}
```

**Our data fields**: tree/rock/fish names → object IDs (from skill HTML pages).
Levels, XP rates, item rewards, respawn timers.

**Challenge**:
- Need to map wiki names to actual OSRS `loc.sym` object IDs.
- **Nuanced mechanics** that need human tuning: tree falling (random depletion),
  fishing spot movement, rock depletion timers, cooking burn rates per fish per level,
  pickpocketing stun chance, etc. These are **behavioral**, not data-driven — we can
  stub them with reasonable defaults but the exact timings/rates need iteration.

---

### 9. QUEST DEPENDENCY TREE — 95% Automatable
**Our data**: `quest_lookup.json` (217 quests), `quest_dependencies.json`, `progression_graph.json`

```kotlin
// Auto-generated: Quest unlock tracking
class QuestPrerequisites : PluginScript() {
    override fun ScriptContext.startup() {
        val questDefs = listOf(
            QuestDef("Cook's Assistant", qpReward = 1, skillReqs = emptyList(),
                      questReqs = emptyList(), difficulty = "Novice"),
            QuestDef("Dragon Slayer II", qpReward = 5, difficulty = "Master",
                      skillReqs = listOf(SkillReq(Skills.MINING, 68), /* ... */),
                      questReqs = listOf("Dragon Slayer I", "Heroes' Quest", /* ... */)),
            // ... all 217 quests
        )

        questDefs.forEach { quest ->
            quest.bindUnlockConditions()
        }
    }
}
```

**Our data fields**: Complete dependency graph with skill requirements (level, skill),
quest prerequisites, item requirements, enemy requirements.

**Challenge**: RSMod uses varbits/varps for quest completion tracking. Need to map
quests to their client-side completion varbits. The varbits we scraped (2,897 varbits)
could help here, but the quest→varbit mapping is non-trivial.

**The progression_graph.json** gives us an ordered list of quests sorted by
prerequisites — directly usable as a suggested unlock order for the server.

---

### 10. QUEST WALKTHROUGHS — 60% Automatable
**Our data**: 217 quest detail HTML pages with full walkthroughs

Can generate **skeleton plugins**:
```kotlin
// Generated skeleton: Cook's Assistant
class CooksAssistant : PluginScript() {
    override fun ScriptContext.startup() {
        // Prerequisites check
        eventBus.subscribeKeyed(NpcInteractionEvent::class.java, npcId = 4620) {
            if (option == "talk-to") {
                player.queue {
                    chatNpc("Oh dear, oh dear! I've lost my cook's helper!")
                    chatPlayer("What happened?")
                    chatNpc("My helper has gone missing, and the Duke's birthday is tomorrow!")
                    chatPlayer("I'll help you find your helper.")
                    chatNpc("Oh thank you! First, I need: 1 egg, 1 bucket of milk, 1 pot of flour.")
                }
            }
        }

        // Item delivery
        eventBus.subscribeKeyed(NpcInteractionEvent::class.java, npcId = 4620) {
            if (option == "give-items") {
                player.queue {
                    if (player.inventory.contains(1944, 1) &&  // egg
                        player.inventory.contains(1927, 1) &&  // milk
                        player.inventory.contains(1933, 1)) {  // flour
                        player.message("You give the ingredients to the cook.")
                        player.inventory.remove(1944, 1)
                        player.inventory.remove(1927, 1)
                        player.inventory.remove(1933, 1)
                        player.skills.addXp(Skills.COOKING, 300.0)
                        quests.complete("Cook's Assistant")
                    } else {
                        player.message("You don't have all the required ingredients.")
                    }
                }
            }
        }
    }
}
```

**What's automatable from walkthroughs**:
- NPC → NPC interaction stubs
- Item delivery requirements
- Reward declarations (items, XP, quest points)
- Dialog chains (rough — would need post-processing)

**What needs human work**:
- Complex branching dialog logic
- Cutscene/area triggers
- Multi-stage quest state tracking
- Unique mechanics (puzzle boxes, mazes, instanced boss fights)
- Quest journal/interface updates

---

### 11. GAME MECHANICS (content/ HTML pages) — Reference Only
**Our data**: 95 game mechanics pages

Purely reference material for server developers. Pages cover:
- Combat triangle, special attacks, attack speeds
- Slayer task weights, masters, unlocks
- Equipment stats, set effects (void, obsidian, etc.)
- Skill mechanics (farming patches, hunter traps, etc.)
- Minigame rules (Temple Trekking, Pest Control, etc.)
- Achievement diary tasks

Not directly automatable but **critical reference** for accurate implementations.

---

### 12. IRONMAN GUIDE PROGRESSION — Design Guide
**Our data**: `progression_timeline.json` + 13 HTML pages of Oziris v4 guide

Provides the **optimal player journey** through the content — useful for designing
server progression, economy gates, and content unlock order. The 6-phase structure:

| Phase | Key Gate | Skill Targets |
|---|---|---|
| 1.1 Early Game | Wintertodt, Ardy 1 | FM 50+, QP ~100 |
| 1.2 Gathering Skills | GP farming | Thieving 83+, Fishing 58 |
| 1.3 Mid Game | Fairy rings, 43 Prayer | Prayer 43, Thieving 88 |
| 1.4 Skilling | Graceful, 50 Con, 60 Smith | Construction 50, Smithing 60 |
| 1.5 Diaries & RFD | Barrows Gloves, ancients | 60 Att, ~70 Cook |
| 2.0 Endgame | SotE, DS2, CG | Mining 70, Herb 77, Range 87+ |

Can be used to generate **progression-based content unlocks**: "you need X skill level
before you should attempt Y quest." This maps directly to RSMod's quest prerequisite
system.


## Summary: Automation Pipeline

```
Our JSON Data → [Python Code Generator] → RSMod Kotlin Plugins (.kt)
                                            ↓
                                     Compiled into server
                                            ↓
                                     RSMod game world
```

### Pipeline Components Needed

1. **Symbol Mapper** — Loads `.sym` files to map entity names → OSRS cache IDs.
   Critical because our data uses wiki names, RSMod uses numerical IDs.

2. **Template Renderer** — Jinja2 (or similar) templates for each content type:
   - `drop_table.plugin.kt.j2`
   - `npc_definition.plugin.kt.j2`
   - `shop_plugin.kt.j2`
   - `skill_action.plugin.kt.j2`
   - `quest_skeleton.plugin.kt.j2`
   - `spell_definition.plugin.kt.j2`

3. **Generator Orchestrator** — Reads parsed JSON, matches names to IDs via sym files,
   renders templates, outputs `.kt` files into the correct `content/*/` subdirectory.

### Estimated Output
| Content Type | Files Generated | LOC Estimate |
|---|---|---|
| Drop tables | ~2,000 | ~80,000 |
| NPC definitions | ~1,500 | ~60,000 |
| Shops | ~500 | ~15,000 |
| Spell definitions | ~200 | ~6,000 |
| Skill actions | ~23 | ~5,000 |
| Quest skeletons | ~217 | ~25,000 |
| Construction | ~1 | ~5,000 |
| **Total** | **~4,500** | **~196,000** |

### What We Can't Auto-Generate
- Boss mechanics (Zulrah phases, God Wars faction, Corrupted Gauntlet prep)
- Minigame logic (Pest Control point system, Trouble Brewing recipe)
- Unique quest mechanics (Underground Pass traps, MM1 platform agility)
- Dialog trees with branching (though dialog text is extractable)
- Area triggers and collision map edits

These require bespoke Kotlin code per feature.
