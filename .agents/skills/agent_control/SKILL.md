---
name: agent_control
description: Provides utility scripts and specifications for LLM agents to connect to OpenRune via AgentBridge, spawn/control players, run QA tests, and manage the server.
---

# Agent Control & QA Scripting Skill

This skill equips any Antigravity LLM agent to connect to the OpenRune game server over WebSockets via **AgentBridge**, control player actions programmatically, and execute automated QA checks.

---

## 1. WebSocket Protocol Quick Reference

The AgentBridge WebSocket server runs on port **43595** (configurable in `game.yml`).

### Inbound Commands (Agent -> Server)
* `{"type": "spawn_bot", "name": "Joe", "x": 3222, "z": 3222}`: Spawns or claims a bot.
* `{"type": "walk", "player": "Joe", "x": 3215, "z": 3218}`: Commands the bot to walk.
* `{"type": "teleport", "player": "Joe", "x": 3215, "z": 3218, "plane": 0}`: Teleports the bot.
* `{"type": "spawn_item", "player": "Joe", "item_id": "bronze_axe", "count": 1}`: Spawns item.
* `{"type": "interact_loc", "player": "Joe", "id": 1276, "x": 3222, "z": 3222}`: Interacts with a world object (e.g. tree).
* `{"type": "wait_for_xp", "player": "Joe", "skill": "woodcutting", "min_amount": 25}`: Blocks action queue until woodcutting XP is gained.

### Outbound State Stream (Server -> Agent)
The server sends a JSON packet every game tick:
```json
{
  "type": "state",
  "tick": 1425,
  "player": {
    "name": "Joe",
    "position": { "x": 3222, "z": 3222, "plane": 0 },
    "skills": {
      "woodcutting": { "level": 1, "baseLevel": 1, "xp": 0 }
    },
    "inventory": []
  }
}
```

---

## 2. Using the Python Test Client

Use the helper client located at `scripts/bot_client.py` to write automated tests.

### Example test case: `test_woodcutting_qa.py`
```python
import asyncio
from bot_client import BotClient

async def test_woodcutting():
    client = BotClient("QAWoodcutter")
    await client.connect()
    
    # 1. Teleport to Lumbridge trees
    await client.teleport(3222, 3222)
    
    # 2. Give the bot an axe
    await client.spawn_item("bronze_axe", 1)
    
    # 3. Chop the tree
    # Tree loc ID is 1276 (standard tree)
    await client.interact_loc(1276, 3223, 3222)
    
    # 4. Wait for XP drop
    print("Waiting for Woodcutting XP...")
    success = await client.wait_for_xp("woodcutting", 25, timeout_seconds=15)
    
    if success:
        print("QA Test SUCCESS: Woodcutting XP gained!")
    else:
        print("QA Test FAILED: Timeout waiting for XP.")
        
    await client.disconnect()

asyncio.run(test_woodcutting())
```
