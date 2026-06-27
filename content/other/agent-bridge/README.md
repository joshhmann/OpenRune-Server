# AgentBridge — LLM Agent Control Interface

WebSocket-based protocol for external LLM agents to control in-game players on OpenRune.

## Overview

AgentBridge is a real-time bidirectional bridge between autonomous agents (LLMs, scripts, or human testing tools) and in-game player characters. Agents connect via WebSocket, spawn or claim bot players, send action commands, and receive per-tick state streams.

```
┌─────────────┐    WebSocket      ┌──────────────┐
│  LLM Agent  │ ◄──── JSON ────► │  OpenRune    │
│  (Python)   │    :43595        │  Game Server │
└─────────────┘                   └──────────────┘
```

## Features

### Core
- **WebSocket server** on port 43595 — single persistent connection per agent
- **Multi-bot support** — one agent can control multiple bots simultaneously
- **Auto-cleanup** — bots despawn when their controlling agent disconnects
- **Thread-safe** — action queue is ConcurrentLinkedQueue, consumed on game thread
- **Port conflict detection** — skips startup if port is already in use

### Actions (Inbound Commands)

| `type` | Params | Description |
|--------|--------|-------------|
| `spawn_bot` | `name`, `x?`, `z?` | Create a new bot player with DB-backed account |
| `despawn_bot` | `name` | Remove a bot player from the world |
| `list_bots` | — | List all active bot players |
| `walk` | `player`, `x`, `z` | Walk to coordinates |
| `teleport` | `player`, `x`, `z`, `plane?` | Instantly teleport |
| `interact_loc` | `player`, `id`, `x`, `z`, `option?` | Interact with a world object |
| `interact_npc` | `player`, `index`, `option?` | Interact with an NPC by index |
| `interact_held` | `player`, `selected_slot`, `target_slot` | Use item on another item |
| `spawn_item` | `player`, `item_id`, `count?` | Give an item to the bot |
| `clear_inventory` | `player` | Remove all items |
| `delete_item` | `player`, `item_id`, `count?` | Remove specific items |
| `ensure_item` | `player`, `item_id`, `count?` | Make sure bot has X of an item |
| `get_state` | `player` | Request immediate state snapshot |
| `wait_ticks` | `player`, `ticks` | Pause action execution for N ticks |
| `wait_for_animation` | `player`, `animation_id`, `timeout?` | Wait for an animation to play |
| `wait_for_xp` | `player`, `skill`, `min_amount`, `timeout?` | Wait for XP gain |
| `wait_for_item` | `player`, `item_id`, `timeout?` | Wait for item to appear in inventory |
| `wait_for_ready` | `player`, `timeout?` | Wait until not animating/in combat |
| `wait_for_position` | `player`, `x`, `z`, `tolerance?`, `timeout?` | Wait until at position |
| `wait_for_condition` | `player`, `condition`, `timeout?` | Wait for a named condition |
| `find_path` | `player`, `x`, `z`, `plane?`, `max_waypoints?` | Calculate path without walking |
| `check_walkable` | `player`, `x`, `z`, `plane?` | Check if tile is reachable |
| `open_door` | `player`, `x`, `z`, `plane?`, `timeout?` | Open a door and walk through |
| `block_door` | `player`, `x`, `z`, `plane?` | Force a door open (PvP scramble) |
| `walk_with_doors` | `player`, `x`, `z` | Walk path, auto-opening doors |
| `attack_npc` | `player`, `index`, `timeout?` | Engage NPC in combat |
| `fight_until_hp` | `player`, `threshold`, `timeout?` | Fight until HP drops |
| `eat_food` | `player`, `food_item?` | Eat food to restore HP |
| `set_combat_style` | `player`, `style` | Change combat stance |

### State Streaming (Outbound)

Every game tick, the server broadcasts a JSON state packet to all connected agents:

```json
{
  "type": "state",
  "tick": 1425,
  "timestamp": 1782503921991,
  "player": {
    "name": "mai",
    "position": { "x": 3222, "z": 3222, "plane": 0 },
    "skills": {
      "attack": { "level": 1, "baseLevel": 1, "xp": 0 },
      "strength": { "level": 1, "baseLevel": 1, "xp": 0 },
      ...
    },
    "inventory": [ ... ],
    "animating": false,
    "inCombat": false
  },
  "combat": { "inCombat": false, "health": 10, "maxHealth": 10 },
  "gameMessages": [...],
  "dialog": "...",
  "events": [...],
  "lastAction": { "success": true, "message": "Teleported to (3219, 3216, 0)" }
}
```

### Telemetry

- **Game messages** — last 20 chat/game messages (filtered to non-blank)
- **Interface text** — last 80 interface text snapshots
- **Action results** — success/failure + message returned per action

## Architecture

```
AgentBridgeServer (Singleton)
├── WebSocketServer (Java-WebSocket)
│   ├── onOpen → track client connection
│   ├── onMessage → parseAndEnqueue()
│   ├── onClose → cleanup bots + remove client
│   └── onError → log silently
│
├── broadcast() — per-tick state push (called from game thread)
├── pollAction() — dequeue next action (called from game thread)
├── pollSystemAction() — dequeue system actions
└── ensureClientTap() — wire AgentBridgeTapClient for telemetry
```

### Key Components

| Class | Role |
|-------|------|
| `AgentBridgeServer` | WebSocket server + action queuing + state broadcasting |
| `AgentBridgeTapClient` | Client wrapper that captures game messages + interface text |
| `AgentBridgeScript` | PluginScript that hooks into game lifecycle for action execution |
| `BotAction` (sealed class) | Typed action hierarchy (Walk, Teleport, InteractLoc, etc.) |
| `AgentBridgeTapSink` | Telemetry callback for game messages |

## Configuration

```yaml
# game.yml
bots:
  enabled: true
  agent-bridge: true           # Enable the AgentBridge module
  agent-bridge-port: 43595     # WebSocket port
```

## Dependencies

- `Java-WebSocket 1.5.7` — WebSocket server implementation
- `Jackson` — JSON serialization
- `player-bot-service` — bot account creation

## Getting Started

```python
import json, asyncio, websockets

async def agent():
    async with websockets.connect("ws://192.168.0.187:43595") as ws:
        # Spawn a bot
        await ws.send(json.dumps({"type": "spawn_bot", "name": "Mai"}))
        state = json.loads(await ws.recv())
        print(f"Bot at ({state['player']['position']['x']}, {state['player']['position']['z']})")

        # Walk somewhere
        await ws.send(json.dumps({"type": "walk", "player": "Mai", "x": 3215, "z": 3218}))

        # Read state stream
        async for msg in ws:
            data = json.loads(msg)
            print(f"Tick {data['tick']}: {data['player']['position']}")

asyncio.run(agent())
```
