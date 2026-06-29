# QA Bot Testing Architecture — OpenRune 239

**Status:** Proposed · **Date:** 2025-06-29  
**Author:** Mai (Hermes Agent)  
**Context:** Josh is sick, manual RuneLite testing blocked. Need autonomous playtesting.

---

## 1. Executive Summary

Automate playtesting by using the existing server-side bot chassis to run **scripted QA test sequences** and optionally connect an **LLM agent** for exploratory testing. The architecture has two complementary paths:

| Path | Approach | Speed | Coverage |
|------|----------|-------|----------|
| **A. Scripted Bots** | Kotlin behavior trees + test scripts in progressive-bots | ⚡ Immediate | Deterministic, reproducible |
| **B. LLM Agent** | External agent → AgentBridge WebSocket | 🧪 Requires LLM setup | Exploratory, adaptive |

**Phase 1 (NOW):** Build and run Path A — scripted bots that walk to NPCs, verify dialogue, interact with objects, and report findings. This runs entirely server-side with zero external infrastructure.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│               OpenRune 239 Server                     │
│                                                       │
│  ┌─────────────────────┐    ┌──────────────────────┐ │
│  │  Progressive Bots   │    │    AgentBridge        │ │
│  │  (BotManager)        │    │  (WebSocket :43595)   │ │
│  │  55 personality bots │    │  ┌────────────────┐  │ │
│  │  + QA test bots      │    │  │ Testing Module │  │ │
│  │                      │    │  │ - TestPresets  │  │ │
│  │  ┌────────────────┐  │    │  │ - TestRunner   │  │ │
│  │  │ BotQaSystem    │  │    │  │ - Reporter     │  │ │
│  │  │ (test tasks)   │◄─┼────┼──┤ - Retry        │  │ │
│  │  └────────────────┘  │    │  │ - SaveState    │  │ │
│  └──────┬───────────────┘    │  └────────────────┘  │ │
│         │                    └──────────────────────┘ │
│         ▼                                              │
│  ┌──────────────────────────────────────────────────────┐
│  │           Player Bot Service                         │
│  │  (spawn/despawn/lifecycle for ALL bots)              │
│  └──────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────┘
           │
           ▼ (WebSocket :43595)
┌──────────────────────┐
│   External Agent     │  ← Optional: local LLM (Phase 2)
│  (Python/TS script)  │
└──────────────────────┘
```

---

## 3. Phase 1: Scripted QA Bot Framework

### 3.1 Components

#### A. Test Task Registry (`BotQaSystem.kt` — already exists, needs expansion)

Registered named tasks that any progressive bot can execute via `::botqa <username> <task>`.

**Existing tasks:** test_woodcutting, test_mining, test_smithing, test_combat, test_shop

**New Lumbridge QA tasks to add:**

| Task Name | Description | Verification Points |
|-----------|-------------|---------------------|
| `qa_lumbridge_npc_tour` | Walk to all Lumbridge NPCs, talk to them, capture dialogue | NPC response text, dialogue opens/closes |
| `qa_cook_assistant` | Complete Cook's Assistant quest | Quest journal updates, item rewards |
| `qa_romeo_juliet` | Start Romeo & Juliet, verify dialogue | NPC responses correct |
| `qa_lumbridge_doors` | Open every door in Lumbridge castle | Door open/close events fire |
| `qa_item_pickup` | Spawn item on ground, walk to it, pick it up | GroundItem events, inventory change |
| `qa_skill_test` | Perform woodcutting/mining/fishing | XP gain events, item in inventory |
| `qa_lumbridge_walk` | Walk a full circuit of Lumbridge | Pathfinding succeeds, no stuck detection |
| `qa_shop_test` | Open Lumbridge general store, browse items | Shop interface opens, items visible |

#### B. Test Runner (`testing/` module — currently stubs)

The `agent-bridge/testing/` module has skeleton classes. Fill them:

- **TestPresets** — Named coordinate/entity presets for Lumbridge (NPC IDs, loc IDs, positions)
- **TestResultReporter** — Collect and write test results to `qa-test-results/` as JSON
- **ActionRetry** — Retry with configurable backoff for flaky interactions
- **WaitConditions** — Named conditions for common wait scenarios
- **SaveStateManager** — Save/restore bot state between test sequences
- **LearningDocs** — Bootstrap documentation for what was learned during testing

#### C. Test Report Format

Results written to `qa-test-results/{timestamp}-{test-name}.json`:

```json
{
  "test": "qa_cook_assistant",
  "startedAt": "2025-06-29T10:00:00Z",
  "completedAt": "2025-06-29T10:02:30Z",
  "status": "PASS",
  "steps": [
    { "action": "spawn_bot", "result": "OK" },
    { "action": "walk", "target": [3209, 3216], "result": "OK", "ticks": 15 },
    { "action": "talk_to", "npc": "Cook", "dialogShown": ["What would you like?", "I'm looking for a quest"] },
    { "action": "get_item", "item": "Bucket of milk", "result": "OK" },
    { "action": "use_item_on_loc", "item": "Bucket of milk", "loc": "Larder", "result": "OK" },
    { "action": "complete_quest", "quest": "Cook's Assistant", "result": "OK" }
  ],
  "bugs": [
    { "severity": "MINOR", "description": "Dialogue option 3 text truncated",
      "expected": "Can you help me make the cake?",
      "actual": "Can you help me make...",
      "loc": [3209, 3216], "npc": "Cook" }
  ],
  "errors": []
}
```

### 3.2 Test Workflow

```
┌─────────┐   ┌──────────┐   ┌──────────┐   ┌────────────┐
│ 1. Pick │   │ 2. Spawn │   │ 3. Walk  │   │ 4. Interact│
│  Test   │──▶│   QA Bot │──▶│  to      │──▶│  with NPC/ │
│  Spec   │   │          │   │  Target  │   │  Object    │
└─────────┘   └──────────┘   └──────────┘   └─────┬──────┘
                                                   │
                                          ┌────────▼───────┐
                                          │ 5. Verify      │
                                          │ State/Output   │
                                          └────────┬───────┘
                                                   │
                                          ┌────────▼───────┐
                                          │ 6. Report      │
                                          │ Pass/Fail+Bugs │
                                          └────────────────┘
```

### 3.3 Triggering Tests

Three ways to trigger a test:

1. **Command:** `::botqa <botname> qa_lumbridge_npc_tour` in-game
2. **Cron schedule:** Auto-run nightly via Hermes cron job
3. **AgentBridge:** External script sends test action type over WebSocket

---

## 4. Phase 2: LLM Agent Bots (Future)

### 4.1 Architecture

```
┌─────────────────────────────────────┐
│   Local LLM (e.g., Nous Hermes 70B) │
│   Running on homelab GPU            │
└────────────┬────────────────────────┘
             │ WebSocket connect :43595
             ▼
┌─────────────────────────────────────┐
│   Agent Controller Script           │
│  (Python asyncio)                   │
│  - Receives state snapshots         │
│  - Sends action commands            │
│  - Maintains conversation context   │
└────────────┬────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│   OpenRune AgentBridge :43595       │
│   - Broadcasts state every tick     │
│   - Queues actions from agent       │
│   - Reports action results          │
└─────────────────────────────────────┘
```

### 4.2 Agent Prompt Template

```
You are a QA bot for OpenRune 239 (OSRS private server).
Your task: EXPLORE and TEST.

You see: {state_snapshot}
Available actions: walk(x,z), interact_npc(index,option), interact_loc(id,x,z,option),
                   talk_to(npcName), wait_ticks(n), get_state, spawn_item(id,count)

Your goal: {test_goal}
Examples:
- "Go to Lumbridge castle, talk to every NPC, report what each one says"
- "Find and open every door in Lumbridge ground floor"
- "Walk to the chicken farm, attack a chicken, loot feathers"

Respond with ONE action command as JSON.
```

### 4.3 Why Both Paths

| Scenario | Best Path | Why |
|----------|-----------|-----|
| Regression test after code change | A (Scripted) | Fast, deterministic, catches known issues |
| Exploring new content | B (LLM) | Finds unexpected bugs, no scripts needed |
| Reproducing a bug report | A (Scripted) | Can script exact steps to reproduce |
| Stress testing server | A (Scripted) | Can spawn 50 bots and run coordinated tests |
| Understanding NPC dialogue | B (LLM) | LLM can parse and summarize all dialogue |

---

## 5. Implementation Plan

### Sprint 1 (Now — Josh is out)
1. ✅ Fill in `testing/` stubs (TestPresets, TestResultReporter, WaitConditions, ActionRetry, SaveStateManager)
2. ✅ Add Lumbridge QA tasks to `BotQaSystem`
3. ✅ Create test spec for `qa_lumbridge_npc_tour` and `qa_lumbridge_doors`
4. ✅ Create `qa-test-results/` output directory
5. 🔄 Run test: `::botqa <any_progressive_bot> qa_lumbridge_npc_tour`

### Sprint 2 (This week)
1. Wire up AgentBridge testing module to cron-scheduled test runs
2. Add quest-specific test tasks (Cook's Assistant, Romeo & Juliet, Rune Mysteries)
3. Add more loc interaction tests (windmill hopper, fishing spots)
4. Build HTML dashboard from test result JSON files

### Sprint 3 (Next week)
1. Deploy local LLM on homelab
2. Write AgentBridge Python client
3. Run exploratory "wander and report" sessions
4. Compare LLM-found bugs vs scripted-test coverage

---

## 6. Key Coordinates & Entity IDs (Lumbridge)

### NPCs for dialogue verification
| NPC | Location | Notes |
|-----|----------|-------|
| Cook | (3209, 3216) | Cook's Assistant quest |
| Father Aereck | (3241, 3207) | Church, Restless Ghost |
| Farmer Fred | (3188, 3270) | Sheep Shearer |
| Shopkeeper | (3214, 3246) | General Store |
| Bob (axe shop) | (3231, 3203) | Axe shop |
| Hans | (3222, 3219) | Castle courtyard |
| Duke Horacio | (3214, 3224) | Castle top floor |
| Guide | (3221, 3222) | Starting area |

### Key Locations (for interact_loc)
| Location | Coords | ID |
|----------|--------|-----|
| Lumbridge Castle Door (main) | (3212, 3220) | — |
| Lumbridge Castle Door (back) | (3220, 3228) | — |
| Cook's range | (3209, 3215) | — |
| Lumbridge church door | (3243, 3205) | — |
| General store counter | (3215, 3244) | — |
| Fishing shop door | (3240, 3229) | — |
| Tree (normal) | (3215, 3222) | — |

---

## 7. Integration with Existing Systems

### Hermes Profile Integration
- Tests can be scheduled via `hermes-cron` for nightly runs
- Results stored in `~/.hermes/profiles/mai/qa-test-results/`
- Notifications on test failure via existing alert system

### CI Integration
- `npm run qa-test` (or gradle task) runs all registered QA tests
- Output goes to `qa-test-results/latest.json`
- Failures block merging in milestone workflow

---

## 8. Quick Start

```bash
# 1. Add a QA test bot to progressive_bots.yml
# 2. Restart server or reload config
# 3. In-game: ::botqa <botname> qa_lumbridge_npc_tour
# 4. Watch bot execute and report results
# 5. Check qa-test-results/ for report

# Alternative — connect AgentBridge client:
# python3 tools/qa-agent/agent_bridge_client.py
# Then send: {"type": "spawn_bot", "name": "TestBot", "x": 3222, "z": 3222}
# Then send: {"player": "TestBot", "type": "walk_with_doors", "x": 3209, "z": 3216}
```

---

## 9. Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Bots interfere with player experience | QA bots use prefixed names (qa-*), despawn automatically after test |
| Bots get stuck on obstacles | StuckDetectorDecorator already exists; timeout kills stuck bots |
| Test results unreliable | ActionRetry with configurable backoff; SaveStateManager for reset |
| AgentBridge WS overload with 55 bots | QA bots are separate from progressive bots; only QA bots use AgentBridge |
| No LLM available for Phase 2 | Phase 1 is fully autonomous without LLM |
