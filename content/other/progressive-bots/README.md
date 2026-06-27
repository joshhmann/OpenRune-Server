# OpenRune Progressive Bots Module

The **Progressive Bots** module is a completely standalone, plug-and-play plugin for OpenRune. It replicates a living, breathing MMO environment populated by autonomous player bots. 

Bots grind skills, fight monsters, manage their own gear progression, path to shops, chat with real players, rate-limit responses, and run fallback behaviors like begging or low-level farming when broke.

---

## 1. System Architecture

The module utilizes a **Hybrid Utility & Behavior Tree (BT) Engine** to drive bot actions:
1. **Utility Decision (Personality)**: High-level planners (`Skiller`, `Fighter`, `Balanced`, `Social`) evaluate their goals and select an active Behavior Tree.
2. **Behavior Tree Execution**: The active tree ticks on the bot, using safety decorators to detect oscillation or experience stalls.

---

## 2. Core Functional Modules

### 2.1 Skilling & Production Loops
Bots support both gathering and multi-step production pipelines:
* **Gathering Trees (`GatherTree.kt`)**: Woodcutting and Mining loops. Bots identify their best available target based on level, search the surrounding `LocRegistry` for active nodes, claim them to avoid pile-ups, and interact.
* **Production Trees (`ProductionTree.kt`)**: Composite Mining $\rightarrow$ Smelting $\rightarrow$ Smithing logic:
  1. Checks for metal bars. If present, walks to the nearest anvil (using `LocRegistry` scanning) and smiths them into daggers.
  2. Checks for ores. If present, walks to the nearest furnace and smelts them into bars.
  3. If missing both, triggers the Mining BT to gather copper, tin, or iron ore.

### 2.2 Combat Loop (`FightTree.kt`)
* Bots query the global `NpcList` to find suitable combat targets based on progression brackets (e.g., Goblins $\rightarrow$ Guards $\rightarrow$ Giants).
* Replicates player interactions by injecting `RouteRequestPathingEntity` and `InteractionNpcOp` into OpenRune's engine.

### 2.3 Simulated Economy & Shops (`EconomyManager.kt`)
* **Shop Visits**: When a bot's inventory is full of skilling products (logs, ore, bars, daggers), it paths to the nearest designated shop (GE, Lumbridge/Varrock general store) to sell items for GP.
* **Upgrades**: GP is spent to purchase better axes, pickaxes, and swords.
* **GP Fallbacks**: If a bot is broke (< 100 GP), it automatically enters a temporary "Begging" or "GP Combat Farming" state.

### 2.4 Safety & Robustness Decorators
Every chosen goal is automatically wrapped in safety nodes:
* `StuckDetectorDecorator`: Escapes pathing locks by forcing a random-walk if coordinates remain unchanged while executing a goal.
* `XPWatchdogDecorator`: Aborts the active goal if zero XP drops are recorded for 150 consecutive ticks, preventing bots from standing idle at depleted resources.
* `GateSweeperDecorator`: Checks for closed doors/gates in a 5-tile radius and opens them if pathing is blocked.

### 2.5 Proximity Social Chat & Asynchronous LLM (`ChatResponseSystem.kt`)
* **Proximity Hearing**: Intercepts player public chat using `PlayerChatEvent`. If a player speaks within 8 tiles of a bot, the bot formulates a response.
* **Rate Limiting**: Enforces a strict 10-second per-player cooldown.
* **LLM Hook**: Asynchronously POSTs a prompt to a local LLM (such as Ollama on `http://localhost:11434/api/generate`) with system instructions matching OSRS slang.
* **Fallback rules**: If the LLM is offline or times out, it immediately matches keyword intents against `chat_responses.json` on a background thread.

---

## 3. Configuration

### 3.1 Server Settings (`bots.yml` or `game.yml`)
Enable progressive bots and point to your LLM API:
```yaml
bots:
  enabled: true
  progressive: true
  llm-url: "http://localhost:11434/api/generate"  # Ollama completions endpoint
  llm-model: "llama3"
```

### 3.2 Spawns Configuration (`progressive_bots.yml`)
Configure individual bot spawns, usernames, and archetypes in a root-level `progressive_bots.yml` file:
```yaml
- username: "woodcutter99"
  planner: "Skiller"
  spawnX: 3222
  spawnZ: 3222
  male: true

- username: "beggar_joe"
  planner: "Social"
  spawnX: 3165
  spawnZ: 3485 # Grand Exchange
```
