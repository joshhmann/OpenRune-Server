# OpenRune 239 — QA Bot Testing Framework

## Overview

Two parallel paths for automated playtesting:

**Path A — Scripted Bots (Phase 1, ready now)**
Run from in-game with `::botqa <botname> <task>`.
Tasks are Kotlin behavior trees — no external infrastructure needed.

**Path B — LLM/External Agent (Phase 2, ready when needed)**
A Python client connects to AgentBridge WebSocket on port 43595
and controls bots via JSON command protocol.

## Quick Start (Path A — In-Game)

```bash
# 1. Make sure bots are enabled in game.yml:
#    bots.enabled: true
#    bots.progressive: true

# 2. Start the server (if not running)

# 3. In-game, pick a progressive bot and run a QA task:
::botqa fallenhero11 qa_npc_hans             # Talk to Hans
::botqa steelchief29 qa_npc_cook              # Talk to Cook
::botqa arcana-king qa_lumbridge_npc_tour     # Full NPC tour
::botqa warriorboss qa_lumbridge_doors        # Test doors
::botqa sweetkiss qa_lumbridge_sweep          # Full sweep!

# 4. Watch the bot execute each step — it announces progress in chat
# 5. Check qa-test-results/ for detailed JSON reports
```

## Available Tasks

Run `::botqa` in-game to see all registered tasks.

**NPC Dialogue Tests:**
- `qa_npc_hans` — Talk to Hans in castle courtyard
- `qa_npc_cook` — Talk to Cook in kitchen
- `qa_npc_duke` — Talk to Duke Horacio in castle
- `qa_npc_father_aereck` — Talk to Father Aereck at church
- `qa_npc_bob` — Talk to Bob at axe shop
- `qa_npc_shopkeeper` — Talk to Shopkeeper at general store

**Multi-NPC Tests:**
- `qa_lumbridge_npc_tour` — Visit all 6 NPCs above in sequence
- `qa_lumbridge_sweep` — Full sweep: NPCs + doors + items

**Door/Interaction Tests:**
- `qa_lumbridge_doors` — Test castle door, church door

**Item Tests:**
- `qa_item_test` — Spawn items into inventory and verify

**Legacy Tasks:**
- `test_woodcutting`, `test_mining`, `test_smithing`, `test_combat`, `test_shop`

## Quick Start (Path B — External Agent)

```bash
# 1. Install Python dependency
pip install websocket-client

# 2. Spawn a bot and run a test
python3 tools/qa-agent/agent_bridge_client.py spawn TestBot
python3 tools/qa-agent/agent_bridge_client.py walk TestBot 3209 3216
python3 tools/qa-agent/agent_bridge_client.py get-state TestBot

# 3. Monitor a bot's state
python3 tools/qa-agent/agent_bridge_client.py monitor TestBot --duration 60

# 4. Run full test suites from JSON specs
#    (run-test command — coming soon; for now use individual commands)
```

## Test Results

All test results are written to `qa-test-results/` as JSON files:
- `{timestamp}-{test-name}.json` — Individual test runs
- `latest.json` — Last test result (overwritten each run)

## Architecture

See `docs/qa-bot-testing-architecture.md` for full design.

### Key Files

```
content/other/agent-bridge/
├── src/main/kotlin/.../agentbridge/
│   ├── AgentBridgeServer.kt    — WebSocket server on port 43595
│   ├── AgentBridgeScript.kt    — Tick-based bot execution engine
│   ├── BotAction.kt            — Full action protocol definition
│   ├── StateSnapshot.kt        — State broadcast format
│   └── testing/
│       ├── TestPresets.kt      — Lumbridge NPC/loc/route presets
│       ├── TestResultReporter.kt — JSON result writer
│       ├── ActionRetry.kt      — Retry logic for flaky actions
│       ├── WaitConditions.kt   — Named wait condition presets
│       ├── SaveStateManager.kt — Bot state save/restore
│       └── LearningDocs.kt     — Learned knowledge about the game

content/other/progressive-bots/
├── src/main/kotlin/.../progressivebots/
│   ├── BotManager.kt           — Bot lifecycle and tick processing
│   ├── BotConfig.kt            — Bot definitions
│   └── qa/
│       ├── BotQaSystem.kt      — QA task registry (expanded)
│       └── QaTestNodes.kt      — QA behavior tree nodes

tools/qa-agent/
├── agent_bridge_client.py      — Python AgentBridge client
└── tests/
    ├── lumbridge_npc_tour.json   — NPC dialogue test spec
    ├── lumbridge_doors.json      — Door interaction test spec
    └── cook_assistant.json       — Cook's Assistant quest test spec

docs/qa-bot-testing-architecture.md — Full design document
```

## Known Issues (from HYX-161 Lumbridge Layer 10)

See `content/other/agent-bridge/.../testing/LearningDocs.kt` for runtime state.

- Windmill hopper — not implemented
- Flour bin — not implemented
- Fishing spots — not functional
- Wizards' Tower door — unresponsive
- Castle door (north) — unresponsive
- Castle door (south) — unresponsive
- Windmill door — unresponsive
- Cook's Assistant quest journal crash — FIXED
