# Zone Content Workflow — Template

**Purpose:** Repeatable process for making an OSRS zone feel feature-complete.
**Derived from:** Lumbridge (Phase 1 — template run)
**Target:** OpenRune 239.1

---

## Layer Status — Lumbridge (Current Sprint)

| Layer | What | Status | There | Missing | Needs Implementation | Blocked / Deferred |
|:-----:|------|:------:|:-----:|:-------:|:-------------------:|:------------------:|
| **1** | **Audit** | 🟢 DONE | 83 NPCs catalogued from corpus, symbol names resolved via cache TOML | — | — | — |
| **2** | **NPCs** | 🟡 IN PROGRESS | 13 handlers (12 upstream + Duke Horacio) | Father Aereck, Lumbridge Guide, Doomsayer, Gelin, Combat Tutors, Restless Ghost, skill tutors | Dialogue handlers for Tier 1 NPCs | Quest-gated NPCs need quests first (Restless Ghost, Prince Ali Rescue) |
| **3** | **Shops** | 🟢 GOOD | General store, Bob's Brilliant Axes, Blue Moon Inn bar | None critical | — | Deferred: full shop inventory pricing TBD |
| **4** | **Interactables** | 🟡 PARTIAL | LumbridgeScript (winch, log→axe), generic doors/gates/ladders/stairs/banks/bookcases/signposts/coops/pickables/search | Castle range, anvil (smithy), furnace, spinning wheel, graves, crates, cupboard, swamp cave entrance | Zone-specific loc handlers | None |
| **5** | **Monsters** | 🟡 PARTIAL | Generic cows/sheep/ducks work; chicken coop exists | Goblins (north), rats (basement), men, giant rats/spiders (swamp), Wizard (tower) | Combat NPC spawn verification & missing spawns | Needs combat formula tuning? |
| **6** | **Items** | 🔴 NOT STARTED | Ground item spawns not inventoried | All ground item spawns | Inventory from OSRS wiki data | None |
| **7** | **Skills** | 🟢 GOOD | Cooking range, woodcutting trees, firemaking, fishing, smithing anvil? | Verify each skill works at its Lumbridge location | — | Deferred: skill tutors with training guidance |
| **8** | **Quests** | 🟡 PARTIAL | Cook's Assistant ✅ complete | Prince Ali Rescue, Rune Mysteries, The Restless Ghost start here | Quest implementations (separate Phase) | Major scope — deferred to post-NPC layer |
| **9** | **Template** | 🟡 IN PROGRESS | This document seeded | Workflow steps being filled as we execute first NPC | — | Will solidify after first full NPC cycle |
| **10** | **Verify** | 🔴 NOT STARTED | — | — | Compile, boot, walk-through click test | After layers 1-4 |

### Legend
🟢 GOOD = no action needed | 🟡 PARTIAL = some work done, more needed | 🟡 IN PROGRESS = actively being worked | 🔴 NOT STARTED = hasn't been touched

---

## Audit

### Tools
- **Corpus:** `tools/data/corpus/parsed-data/npc_by_name.json` — filter by `location` containing zone name
- **Corpus:** `tools/data/corpus/parsed-data/npc_spawns.json` — spawn coordinates
- **Corpus:** `tools/data/corpus/parsed-data/shops.json` — shop definitions
- **Corpus:** `tools/data/corpus/parsed-data/item_by_name.json` — item IDs
- **osrs-mcp:** `./gradlew :tools:osrs-mcp:runMcp` — cache symbol lookups

### What to capture
- NPCs in zone (name, location, F2P/P2P, quest involvement)
- Existing handlers vs missing handlers
- Shops needed
- Interactable locs (anvils, ranges, doors, etc.)
- Monster spawns
- Ground item spawns

### Gap format
```yaml
npc_name:
  tier: 1/2/3          # 1 = immediate player encounter
  location: "description"
  f2p: true/false
  quest: "quest_name or null"
  handler_exists: true/false
  notes: "dialogue complexity, quest gating, etc."
```

---

## NPC Handler Creation

### Pattern
```kotlin
// content/areas/city/<zone>/npcs/<NpcName>.kt
class NpcName : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.<symbol_name>") { startDialogueWith(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogueWith(npc: Npc) {
        startDialogue(npc) { mainDialogue(npc) }
    }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        // Simple: greet → goodbye
        // With quest: branch on quest state
        // With shop: trade option
    }
}
```

### How to find symbol names
1. Check the NPC's cache symbol in `.data/raw-cache/server/npcs.toml` or map spawn TOMLs like `.data/raw-cache/map/npcs/<zone>.toml`
2. Try the NPC's wiki name lowercased with underscores: `"npc.duke_horacio"`
3. Search the spawn files for known NPC names

### Worked Example — Duke Horacio

**Step 1: Find symbol name**
```bash
grep -i "horacio\|duke" .data/raw-cache/map/npcs/lumbridge.toml
# Result: npc = "npc.duke_of_lumbridge"
```

**Step 2: Find spawn location**
```bash
grep -B2 -A2 'npc.duke_of_lumbridge' .data/raw-cache/map/npcs/lumbridge.toml
# Result: coords = "1_50_50_10_24" (Lumbridge Castle, ground floor [UK])
```

**Step 3: Get dialogue** — Use OSRS wiki transcripts or write authentic dialogue based on game knowledge

**Step 4: Create handler** — See `content/areas/city/lumbridge/npcs/DukeHoracio.kt`

**Step 5: Compile**
```bash
./gradlew :content:areas:city:lumbridge:compileKotlin
```

**Key observations from Duke Horacio:**
- Symbol name may differ from wiki name (`npc.duke_of_lumbridge` not `npc.duke_horacio`)
- Some NPCs have multiple cache IDs for different states/variants — verify the right one
- NPC handlers auto-register via PluginScript — no manual wiring needed
- Use `onOpNpc1` for "Talk-to", `onOpNpc3` for "Pickpocket" or other options

### Quest-gated dialogue pattern
```kotlin
when {
    quest.isQuestCompleted(player) -> afterQuest(npc)
    quest.questState(player) == QuestProgressState.IN_PROGRESS -> duringQuest(npc)
    else -> preQuest(npc)
}
```

---

## Registration

All NPC handlers are their own `PluginScript` class. They auto-register — just create the file and add `onOpNpc1()` in that class's `startup()`. No manual wiring needed.

---

## Compile & Verify

```bash
# Compile just the zone module (fast)
./gradlew :content:areas:city:<zone>:compileKotlin

# Full server build (slow — only after zone passes)
./gradlew :server:app:compileKotlin
```

### Runtime verification
- Boot server
- Walk to NPC location
- Click Talk-to → confirm dialogue opens
- Test all dialogue branches
- For quest NPCs: test pre-quest, in-progress, and post-completion states

---

## Commit

```bash
git add content/areas/city/<zone>/
git commit -m "feat(<zone>): add <npc> handler with dialogue"
git push joshhmann main
```
