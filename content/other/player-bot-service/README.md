# Player Bot System - Autonomous World Players

Pluggable module framework for creating and managing autonomous world-population bot players on OpenRune.

AgentBridge/LLM agents are a separate system. They create and control their own agent-owned bots and must not attach to, tick, or mutate ambient playerbots.

## Modules

| Module | Purpose |
|--------|---------|
| `player-bot-service` | Core service: DB-backed ambient bot account creation, lifecycle, lookup |
| `progressive-bots` | Autonomous bot manager: personality-driven tick loop, trajectory capture |

## Architecture

### Lifecycle Invariant

Bot systems are separate from human player login/logout logic. Bots must use the same durable
account, character, registry, session event, save, and logout lifecycle that normal players use.

Allowed:
- Creating or loading bot accounts/characters through `CharacterAccountRepository`
- Applying normal character metadata pipelines
- Entering the world through `PlayerRegistry.add`
- Logging out through `forceDisconnect` so the normal logout/save/delete path runs

Forbidden:
- Direct writes to or removals from `PlayerList`
- Bot-only branches inside core human login/logout processors
- Changes to human account load/save semantics just to make bot spawning easier
- Separate in-memory bot identities that bypass account/character persistence

If bot behavior needs new lifecycle behavior, add it behind the bot service boundary while keeping
the human player path unchanged.

### System Boundary

Playerbots and AgentBridge bots are intentionally separate lanes:

- `player-bot-service` and `progressive-bots` own ambient autonomous players.
- AgentBridge owns LLM/QA agent bots through its own `AgentBotService`.
- LLM social chat for ambient playerbots should be added as a progressive/playerbot behavior plugin, not by giving AgentBridge control over playerbots.
- AgentBridge must not install client taps or soft timers on human players or ambient playerbots.

```
PlayerBotService (Singleton)
├── spawnBot(name, x, z) → Player
│   └── registerBotAccount(name) → CharacterMetadataList
│       ├── repository.selectAndCreateMetadataList()
│       ├── if missing: repository.insertOrSelectAccountId()
│       ├── if missing: repository.insertAndSelectCharacterId()
│       ├── repository.selectAndCreateMetadataList()
│       └── pipelines.append(...)
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
- **Reusable identities** — existing bot accounts/characters are loaded and reused across restarts
- **Full pipeline processing** — `CharacterAccountRepository` → metadata transformers → stat/inventory init
- **Tracked bot players** — bots are real `Player` objects, visible to all online players; they start with `NoopClient`, but bridge systems may wrap the client for telemetry
- **Autosave compatibility** — bots persist through the server's autosave cycle without NPEs
- **Automatic password hashing** — hardcoded bot password hash for instant non-login spawns
- **Registry lifecycle** — finds next free player slot, registers through `PlayerRegistry`, and fires normal session events

### API

```kotlin
// Spawn a bot with a DB account
val bot: Player? = playerBotService.spawnBot("fallenhero11", 3222, 3222)

// Remove from world
playerBotService.despawnBot("fallenhero11")

// Find by name
val bot: Player? = playerBotService.findBot("fallenhero11")

// Count active bot players spawned by this service
val count: Int = playerBotService.botCount()
```

### Spawn Flow

```
spawnBot("Mai", 3222, 3222)
  │
  ├─ registerBotAccount("mai")
  │   ├─ database.withTransaction { connection ->
  │   │   ├─ repository.selectAndCreateMetadataList(connection, "mai")
  │   │   ├─ if missing: repository.insertOrSelectAccountId(connection, "mai", bcrypt("bot_pass"))
  │   │   ├─ if missing: repository.insertAndSelectCharacterId(connection, accountId)
  │   │   ├─ repository.selectAndCreateMetadataList(connection, "mai")
  │   │   └─ for (pipeline in pipelines) pipeline.append(connection, metadataList)
  │   │
  │   └─ returns CharacterMetadataList with transformers
  │
  ├─ Player(client = NoopClient)
  ├─ for (transform in metadata.transformers) transform.apply(player)
  ├─ player.coords = CoordGrid(3222, 3222, 0)
  ├─ playerRegistry.add(player)
  │   └─ publishes SessionStateEvent.Initialize
  ├─ eventBus.publish(SessionStateEvent.Login(player))
  └─ eventBus.publish(SessionStateEvent.EngineLogin(player))
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
  agent-bridge-port: 43595
```

Start the server:
```bash
cd /root/Runescape/open_rune/OpenRune-Server
./gradlew :server:app:run
```

Watch the bots spawn:
```
[ProgressiveBots] Spawned 111/111 progressive bots
[ProgressiveBots] PlayerList reports 111 tracked bots
```
