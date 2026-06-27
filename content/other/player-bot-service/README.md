# Player Bot System — Autonomous & LLM-Controlled Players

Pluggable module framework for creating, managing, and controlling game bot players on OpenRune. Supports **progressive autonomous bots** (world population) and **LLM-controlled agents** (through AgentBridge).

## Modules

| Module | Purpose |
|--------|---------|
| `player-bot-service` | Core service: DB-backed bot account creation, lifecycle, lookup |
| `progressive-bots` | Autonomous bot manager: personality-driven tick loop, trajectory capture |

## Architecture

```
PlayerBotService (Singleton)
├── spawnBot(name, x, z) → Player
│   └── registerBotAccount(name) → CharacterMetadataList
│       ├── repository.insertOrSelectAccountId()
│       ├── repository.insertAndSelectCharacterId()
│       ├── repository.selectAndCreateMetadataList()
│       └── pipelines (LOGIN_LOAD_ALL, etc.)
├── despawnBot(name) → Boolean
├── findBot(name) → Player?
└── botCount() → Int

BotManager (PluginScript)
├── startup() → defers spawn to first game tick (after DB is ready)
├── onTick() → every LateCycle
│   └── tickBot(player, state)
│       ├── position tracking + outcome capture
│       ├── personality.pickAction(view, state)
│       ├── TrajectoryCapture (state + decision + outcome logging)
│       └── executeBotAction(player, action)

TrajectoryCapture
├── capture(tick, state, personality, action) → JSONL entry
├── captureOutcome(tick, botName, x, z)
├── paused (runtime toggle)
└── targetCount (auto-pause at N entries)
```

---

## PlayerBotService

Core service that bridges a bot player's existence between the database and the game world.

### Features

- **DB-backed accounts** — bots get real `account` + `character` rows in PostgreSQL, same as human players
- **Full pipeline processing** — `AccountRepository.createAccount` → `CharacterAccountApplier` → stat/inventory init
- **NoopClient bots** — bots are real `Player` objects with `NoopClient`, visible to all online players
- **Autosave compatibility** — bots persist through the server's autosave cycle without NPEs
- **Automatic password hashing** — hardcoded bot password hash for instant non-login spawns
- **Slot management** — finds next free player slot, registers and inserts

### API

```kotlin
// Spawn a bot with a DB account
val bot: Player? = playerBotService.spawnBot("fallenhero11", 3222, 3222)

// Remove from world
playerBotService.despawnBot("fallenhero11")

// Find by name
val bot: Player? = playerBotService.findBot("fallenhero11")

// Count active NoopClient bots
val count: Int = playerBotService.botCount()
```

### Spawn Flow

```
spawnBot("Mai", 3222, 3222)
  │
  ├─ registerBotAccount("mai")
  │   ├─ database.withTransaction { connection ->
  │   │   ├─ repository.insertOrSelectAccountId(connection, "mai", bcrypt("bot_pass"))
  │   │   ├─ repository.insertAndSelectCharacterId(connection, accountId)
  │   │   ├─ repository.selectAndCreateMetadataList(connection, "mai")
  │   │   └─ for (pipeline in pipelines) pipeline.append(connection, metadataList)
  │   │
  │   └─ returns CharacterMetadataList with transformers
  │
  ├─ Player(client = NoopClient)
  ├─ for (transform in metadata.transformers) transform.apply(player)
  ├─ player.coords = CoordGrid(3222, 3222, 0)
  ├─ invMapInit.init(player)
  └─ playerList[slot] = player
```

### Configuration

```yaml
# game.yml
bots:
  enabled: true
  postgres:
    pool-size: 10
```

---

## Progressive Bots

Autonomous bot system that populates the world with AI-driven player characters. Each bot has a personality archetype that determines its behavior.

### Features

- **110 configurable bots** — defined in `BotConfig` with 6 personality types
- **Personality-driven decisions** — each bot picks actions based on its archetype every 25 ticks
- **DB-backed identities** — every bot gets a real account + character on first spawn
- **Runtime trajectory capture** — logs state + decision + outcome as JSONL for LLM training
- **Runtime toggles** — `::trajectory pause|resume|status|target N` in-game commands
- **Auto-despawn on server stop** — bots cleaned up through shutdown hooks
- **Visible to all players** — bots are real Player objects, appear on the world map

### Bot Personalities

| Personality | Behavior | Count |
|------------|----------|-------|
| **Skiller** | Gathers resources (woodcutting, mining, fishing areas) | 25 |
| **Fighter** | Seeks combat NPCs and trains combat skills | 20 |
| **Balanced** | Mix of gathering (40%), fighting (30%), socializing (15%), wandering (15%) | 20 |
| **Social** | Walks through high-traffic areas, congregates with other players | 15 |
| **Vendor** | Walks to shop areas, simulates trading | 15 |
| **PKer** | Wanders in Wilderness area | 10 |

### Bot Actions

| Action | Behavior |
|--------|----------|
| `Wander` | Random walk within bounded area (3200-3270) |
| `Idle` | Stand still, do nothing |
| `Socialize` | Walk to Lumbridge center (3218-3225) |
| `Gather` | Walk to resource locations (trees, fishing spots) |
| `Fight` | Walk to combat training spots |
| `Shop` | Walk to shop area (General Store) |

### Trajectory Capture

Every 25 ticks, each bot's state + decision is logged as JSONL:

```
trajectories/progressive/{YYYY-MM-DD}/trajectory.jsonl
```

**Entry types:**

Decision entry (state → action):
```json
{
  "tick": 25,
  "bot_name": "fallenhero11",
  "personality": "Skiller",
  "state": {
    "position": {"x": 3222, "z": 3222, "plane": 0},
    "skills": {"attack": {"level": 1, "base_level": 1, "xp": 0}, ...},
    "in_combat": false,
    "animating": true
  },
  "decision": {"action": "Gather"},
  "outcome": null
}
```

Outcome entry:
```json
{
  "tick": 26,
  "bot_name": "fallenhero11",
  "outcome": {"status": "moved", "new_x": 3223, "new_z": 3223}
}
```

#### Runtime Controls

Logged-in players can control trajectory capture with:

| Command | Effect |
|---------|--------|
| `::trajectory status` | Show current state (paused/running + entry count) |
| `::trajectory pause` | Stop writing new entries |
| `::trajectory resume` | Start writing again |
| `::trajectory target 100000` | Auto-pause after N entries |
| `::trajectory target 0` | Clear target (unlimited) |

### Configuration

```yaml
# game.yml
bots:
  enabled: true
  progressive: true   # Enable autonomous progressive bots
```

### Bot Definitions

Bots are defined in `BotConfig.kt` with:
```kotlin
BotDef(
    username = "fallenhero11",
    planner = BotPlanner.Skiller,
    spawnX = 3222,
    spawnZ = 3222,
    spawnPlane = 0,
    male = true,
    head = 0,
    body = 18,
    legs = 26,
    skinColor = 0,
)
```

Spawn positions, looks, and personalities can be extended by adding entries to the `BotConfig.bots` list.

### Deferred Spawning

Bots spawn on the first game tick (not during plugin startup) to ensure embedded PostgreSQL is fully initialized before any DB transactions occur.

---

## Example: Starting the System

```yaml
# game.yml
bots:
  enabled: true
  progressive: true
  agent-bridge: true
  agent-bridge-port: 43595
```

Start the server:
```bash
cd /root/Runescape/open_rune/OpenRune-Server
./gradlew :server:app:run
```

Watch the bots spawn:
```
[ProgressiveBots] Spawned 110/110 progressive bots
[ProgressiveBots] PlayerList reports 110 NoopClient bots
```
