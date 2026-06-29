# QA Bot Testing Roadmap — Automated Testing for OpenRune

**Date:** 2026-06-29  
**Author:** Mai (subagent review)  
**Status:** Draft for review by Josh  
**Scope:** `/root/Runescape/open_rune/OpenRune-Server/`

---

## 1. Current State Assessment

### 1.1 Project Status (Milestone 0)

| Gate | Status |
|:-----|:------:|
| ~~Quest compile fixed~~ | ✅ PASS (267405c4) |
| ~~Full server compile green~~ | ✅ PASS |
| ~~Server boots clean~~ | ✅ PASS — 55 bots, 0 errors |
| Lumbridge Layer 10 runtime walkthrough | 🔴 NOT DONE — Josh got sick, manual attempt incomplete |
| DEF-001 castle doors decision | 🔴 PENDING |

### 1.2 Known Runtime Blockers (HYX-161)

From manual testing, these are the verified failures that any automated QA must detect:

| Issue | Zone | Layer | Impact |
|:------|:-----|:-----:|:-------|
| Castle doors not interactable (not just elf doors) | Lumbridge | 4 | Blocks castle access for quests |
| Mill hopper/flour bin not implemented | Lumbridge → Cook's Assistant | 8 | Blocks quest completion |
| Fishing spots not functional | Lumbridge | 7 | Blocks fishing skill |
| Wizards' Tower door broken | Wizards' Tower | 4 | Blocks Rune Mysteries access |
| Quest journal text truncation | Global | 8 | Cosmetic but noticeable |
| Windmill doors placed wrong direction | Lumbridge | 4 | Blocks mill interior access |

### 1.3 Existing QA Infrastructure

#### Already Working:
| System | Details | Status |
|:-------|:--------|:------:|
| **AgentBridge WebSocket** | Port 43595, 50+ action types (walk, teleport, interact_loc, interact_npc, spawn_item, combat, banking, shops, ground items, prayer) | ✅ WORKING |
| **AgentBotService** | DB-backed bot lifecycle — spawn/despawn/find/count | ✅ WORKING |
| **AgentBridgeScript** | Per-tick loop — dequeues actions, broadcasts state snapshots, detects events | ✅ WORKING |
| **PlayerBotService** | Shared ambient bot spawning with real DB accounts | ✅ WORKING |
| **Progressive Bots** | 110 autonomous bots, 6 personalities, trajectory capture to JSONL | ✅ WORKING |
| **DoorDatabase + PathfindingService** | Door-aware pathfinding | ✅ WORKING |
| **Ironman Mode** | Enforcement over AgentBridge actions | ✅ WORKING |

#### Existing but Stub/Empty:
| Component | File | Status |
|:----------|:-----|:------:|
| `BotQaSystem.kt` | `content/other/progressive-bots/qa/BotQaSystem.kt` | ⚠️ Stub — only 4 test tasks registered (woodcutting, mining, smithing, combat) |
| `TestPresets.kt` | `content/other/agent-bridge/testing/TestPresets.kt` | 🔴 Empty object |
| `WaitConditions.kt` | `content/other/agent-bridge/testing/WaitConditions.kt` | 🔴 Empty object |
| `TestResultReporter.kt` | `content/other/agent-bridge/testing/TestResultReporter.kt` | 🔴 Empty class |
| `SaveStateManager.kt` | `content/other/agent-bridge/testing/SaveStateManager.kt` | 🔴 Partial impl — broken compile (uses wrong `Obj` constructor). Capture works, restore broken. |
| `ActionRetry.kt` | `content/other/agent-bridge/testing/ActionRetry.kt` | 🔴 Stub — returns false/default |
| `LearningDocs.kt` | `content/other/agent-bridge/testing/LearningDocs.kt` | 🔴 Empty |
| `TestingModule.kt` | `content/other/agent-bridge/testing/TestingModule.kt` | ⚠️ Binds empty singletons |

#### Existing Unit/Integration Tests:
| Framework | Scope | Status |
|:----------|:------|:------:|
| **GameTestState** (JUnit5) | Per-script integration tests for skills (woodcutting), interfaces (bank, emotes), travel (canoe) | ✅ WORKING — but only 5 test classes exist |
| **test-conventions.gradle.kts** | Base test config — JUnit5, parallel execution, 2GB heap | ✅ WORKING |
| **integration-test-suite.gradle.kts** | Integration test suite config depending on `api:testing` | ⚠️ Unused by most modules |
| **meta-test-suite.gradle.kts** | Meta test suite convention | ❓ Present but unclear usage |

---

## 2. Work Required — Modules, Files, Configs

### 2.1 Phase 1: Scripted Bot Tests (Quick Win — ~3-5 days)

Build on the existing `BotQaSystem` pattern to create deterministic test scenarios that run via `::botqa <username> <task>` admin command.

#### Files to Create:

| # | File | Purpose | Effort |
|:-:|:-----|:--------|:------:|
| 1 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/TestRunner.kt` | Orchestrator: runs test sequence, tracks pass/fail per step, reports summary | Medium |
| 2 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/LumbridgeDoorTest.kt` | Walk to each known door in Lumbridge, attempt open, verify state change | Medium |
| 3 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/CooksAssistantTest.kt` | Complete Cook's Assistant: collect egg/milk/flour, deliver, verify completion | Large |
| 4 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/NpcDialogueTest.kt` | Talk-to each Tier 1 NPC, verify dialogue appears, test all branches | Medium |
| 5 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/SkillFunctionalityTest.kt` | Test woodcutting (trees), fishing (spots), cooking (range) | Medium |
| 6 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/ShopInteractionTest.kt` | Open shop, buy/sell items, verify stock changes | Small |
| 7 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/MonsterSpawnTest.kt` | Attack various monsters, verify damage, death, drops | Medium |
| 8 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/tests/InteractableLocTest.kt` | Ladders, stairs, gates, pickables, searchables | Medium |
| 9 | `content/other/progressive-bots/src/main/kotlin/org/rsmod/content/other/progressivebots/qa/TestReport.kt` | Data class for structured test results (pass/fail/error/message/timestamp) | Small |

#### Files to Modify:

| # | File | Change | Effort |
|:-:|:-----|:-------|:------:|
| 1 | `BotQaSystem.kt` | Register new test tasks, add task listing support | Small |
| 2 | `BotManager.kt` (progressive-bots) | Add `::botqa run <testname>` command for sequential execution | Small |
| 3 | `BotAction.kt` (agent-bridge) | Add `RunQaTest` action type (or keep via porcelain) | Small |
| 4 | `game.yml` | Add `bots.qa-tests: true` config flag | Tiny |

#### No config changes needed:
- `progressive_bots.yml` — stays as-is (provides bot definitions)
- `game.yml` bots section — mostly already configured for progressive + agent-bridge

### 2.2 Phase 2: AgentBridge Testing Framework (Unlocks LLM Control — ~5-7 days)

Build out the stub AgentBridge testing module into a real framework that external LLM agents can use for automated QA.

#### Files to Create:

| # | File | Purpose | Effort |
|:-:|:-----|:--------|:------:|
| 1 | `content/other/agent-bridge/src/main/kotlin/org/rsmod/content/other/agentbridge/testing/TestScenario.kt` | Scenario definition DSL — sequence of steps with assertions | Large |
| 2 | `content/other/agent-bridge/src/main/kotlin/org/rsmod/content/other/agentbridge/testing/TestAssertion.kt` | Assertion types: position check, inventory contents, message seen, skill level, dialogue open | Medium |
| 3 | `content/other/agent-bridge/src/main/kotlin/org/rsmod/content/other/agentbridge/testing/TestRunner.kt` | Executes test scenarios through AgentBridge actions, collects results | Medium |
| 4 | `content/other/agent-bridge/src/main/kotlin/org/rsmod/content/other/agentbridge/testing/TestReport.kt` | Structured result with steps, pass/fail, screenshots (state snapshots), timing | Small |
| 5 | `content/other/agent-bridge/src/main/kotlin/org/rsmod/content/other/agentbridge/testing/SaveStateManager.kt` | Save/restore bot state (inventory, position, quest state) for test isolation | Medium |

#### Files to Modify:

| # | File | Change | Effort |
|:-:|:-----|:-------|:------:|
| 1 | `TestingModule.kt` | Wire real implementations instead of stubs | Small |
| 2 | `TestPresets.kt` | Add predefined test scenarios (Lumbridge smoke test, Cook's Assistant flow, etc.) | Medium |
| 3 | `WaitConditions.kt` | Add rich wait conditions: quest state, interface open, animation complete, position reached | Medium |
| 4 | `TestResultReporter.kt` | Implement report generation: JSON output, console summary, failure detail | Medium |
| 5 | `ActionRetry.kt` | Implement door retry, blocked path retry, NPC interaction retry | Small |
| 6 | `LearningDocs.kt` | Runtime documentation of test failures for LLM context | Small |
| 7 | `AgentBridgeScript.kt` | Add `run_test` action that triggers scenario execution | Small |
| 8 | `AgentBridgeServer.kt` | Add `test_report` outbound message type | Small |

### 2.3 Phase 3: LLM Agent QA Scripts (External — ~3-5 days)

Write Python/TypeScript agents that connect to AgentBridge WebSocket and run intelligent QA.

#### Files to Create:

| # | File | Purpose | Effort |
|:-:|:-----|:--------|:------:|
| 1 | `tools/qa/run_smoke_test.py` | Python agent: spawn bot, walk Lumbridge, verify all layers | Medium |
| 2 | `tools/qa/test_cooks_assistant.py` | Python agent: complete full quest flow via WebSocket commands | Medium |
| 3 | `tools/qa/test_doors_gates.py` | Python agent: exhaustive door/gate interaction test | Medium |
| 4 | `tools/qa/qa_lib.py` | Shared library: bot lifecycle, action helpers, assertion helpers | Medium |
| 5 | `tools/qa/README.md` | Documentation for running LLM QA tests | Small |
| 6 | `tools/qa/requirements.txt` | Python dependencies (websockets, asyncio, argparse, json) | Tiny |

#### No server-side changes needed for Phase 3 — AgentBridge protocol already supports all required actions.

### 2.4 Phase 4: HYX-161 Fixes + Verification (Depends On Fixes — ~5-10 days)

Before QA tests can fully pass, the underlying bugs must be fixed. QA tests verify the fixes.

| Issue | Fix Location | Effort | Dependencies |
|:------|:------------|:------:|:------------|
| Castle doors not interactable | `LumbridgeScript.kt` or engine `ConstantProvider` | Medium | DEF-001 decision |
| Mill hopper/flour bin | New module: `content/mechanics/windmill/` or `content/areas/city/lumbridge/windmill/` | Medium | Generic content module pattern |
| Fishing spots | `content/skills/fishing/` — check existing module | Unknown | May already exist but need debugging |
| Wizards' Tower door | Check loc handler in tower zone | Small | Depends on Wizards' Tower zone |
| Quest journal text truncation | `QuestScript.kt` or journal renderer | Small | Quest framework |
| Windmill doors direction | `LumbridgeScript.kt` door rotation params | Small | None |

---

## 3. Dependencies and Ordering

```
Phase 1: Scripted Bots (Quick Win)
├── No server-side infrastructure changes needed
├── BotQaSystem already exists (stub)
├── AgentBridge already provides all interaction primitives
├── Blocked by: HYX-161 fixes for full test passes
└── Unlocks: repeatable regression testing, CI gate
     │
     ▼
Phase 2: Testing Framework (AgentBridge stubs → real)
├── Depends on Phase 1 patterns for test design
├── Requires server rebuild for new agent-bridge testing code
├── Blocked by: nothing — stub infrastructure already there
└── Unlocks: structured test definitions, save/restore, retry
     │
     ▼
Phase 3: LLM Agent Scripts (External)
├── Depends on Phase 2 (test framework) or can run directly on Phase 1
├── No server changes needed
├── Blocked by: AgentBridge port accessibility (port 43595)
└── Unlocks: intelligent adaptive testing, exploratory QA
     │
     ▼
Phase 4: HYX-161 Fixes + Verification
├── Depends on Phases 1-3 providing test scaffolding
├── QA tests validate each fix
├── Blocked by: DEF-001 architectural decision
└── Unlocks: Milestone 0 completion, Green Layer 10
```

### Critical Path:
```
Day 1-2: Phase 1 (Scripted Bots)
Day 3-4: Phase 2 (Testing Framework) — parallel if separate team
Day 5-7: Phase 3 (LLM Agents) — can start after Phase 1
Day 5-14: Phase 4 (Fixes) — requires PM decision on DEF-001
```

---

## 4. Rollout Plan

### Phase 1: Scripted Bot Smoke Tests (Recommended: Start Now)
**Goal:** Replace manual Lumbridge walkthrough with repeatable bot-driven verification.

**Deliverables:**
- `::botqa lumbridge_smoke` — walks through Lumbridge, tests all NPCs, doors, stairs, shops, quest start
- `::botqa cooks_assistant` — runs through full Cook's Assistant flow
- `::botqa list` — shows available QA tests
- Console output: step-by-step pass/fail with tick timestamps

**Success Criteria:**
- Test suite catches the 6 known HYX-161 failures
- Tests complete within 5 minutes
- Zero false positives

**Effort:** 3-5 days (one dev)

### Phase 2: AgentBridge Test Framework (Recommended: After Phase 1)
**Goal:** Enable structured test scenarios accessible via WebSocket.

**Deliverables:**
- `TestScenario` DSL for defining multi-step test sequences
- `SaveStateManager` for test isolation (inventory/position save/restore)
- `TestResultReporter` for structured JSON output
- Predefined scenarios in `TestPresets`

**Success Criteria:**
- External script can define a test scenario in < 50 lines
- Tests are isolated (bot state restored after each test)
- Results include per-step timing and failure messages

**Effort:** 5-7 days (one dev)

### Phase 3: LLM Agent Integration (Recommended: After Phase 1, Run in Parallel with Phase 2)
**Goal:** Intelligent QA agents that can explore and detect regressions.

**Deliverables:**
- Python client library (`tools/qa/qa_lib.py`)
- Smoke test agent
- Quest flow test agent
- Ad-hoc exploratory agent

**Success Criteria:**
- Agent can spawn bot, execute multi-step scenario, and report results
- Integration with CI (optional — can run manually)

**Effort:** 3-5 days (one dev, Python experience)

### Phase 4: HYX-161 Issue Resolution (Recommended: Parallel to Phases 1-3)
**Goal:** Fix known blockers so QA tests can go green.

**Deliverables:**
- DEF-001 decision documented and resolved
- Mill hopper/flour bin implementation
- Fishing spot fix
- Other HYX-161 fixes

**Success Criteria:**
- All scripted bot tests pass
- Lumbridge Layer 10 walkthrough complete

**Effort:** 5-10 days (depends on complexity)

---

## 5. Relative Effort Estimates

| Phase | Estimated Dev-Days | Risk | Parallelizable | Dependencies |
|:------|:------------------:|:----:|:--------------:|:------------|
| Phase 1 — Scripted Bots | 3-5 | Low | No (foundation) | None |
| Phase 2 — Test Framework | 5-7 | Medium | After Phase 1 | Phase 1 patterns |
| Phase 3 — LLM Agents | 3-5 | Low | With Phase 2 | Phase 1 (or direct AgentBridge) |
| Phase 4 — HYX-161 Fixes | 5-10 | Medium-High | With Phases 1-3 | DEF-001 decision |
| **Total** | **16-27** | | | |

---

## 6. Risks and Unknowns

| Risk | Impact | Likelihood | Mitigation |
|:-----|:------:|:----------:|:-----------|
| **DEF-001 architectural decision blocked** — castle doors need engine-level fix that may take weeks | High | Medium | Defer to Phase 4; accept DEF-001 as deferred for Phase 1-3 tests (exclude from test scope) |
| **Port 43595 not accessible from external** — AgentBridge may be LAN-only | High | Low | SSH tunnel / port forward or run QA scripts on-server |
| **AgentBridge actions not comprehensive enough** — some interactions may be missing primitives | Medium | Low | Extend `BotAction` sealed class with new types; add porcelain implementations |
| **Test isolation complexity** — restoring bot state may be difficult due to DB-backed persistence | Medium | Medium | Phase 2 `SaveStateManager` can use teleport + spawn_item + clear_inventory as soft reset |
| **Progressive bots interfere with QA tests** — 55 ambient bots may crowd test area | Low | Medium | Use dedicated QA test accounts on separate plane or area; or temporarily despawn progressive bots during tests |
| **Quest framework state management** — quest progress is persistent in DB; hard to reset | Medium | High | Phase 2 save/restore must include quest varp state; or use `assume-completed` mode |
| **Timing-dependent tests** — tick-based assertions may be flaky | Medium | Medium | Use wait_ticks + wait_for_condition for synchronization; add tolerance windows |
| **No CI/CD pipeline** — tests run manually, no automated regression | Medium | High | Not a blocker but limits long-term value; propose Jenkins/GitHub Actions as future work |

---

## 7. Recommendation: Scripted Bots First, Then LLM Agents

### Why Scripted Bots First (Phase 1)

1. **Quickest path to value** — 3-5 days vs 5-7 for the full framework
2. **No external dependencies** — runs entirely inside the game server via `::botqa` command
3. **Immediately replaces manual testing** — Josh can run `::botqa lumbridge_smoke` and get results
4. **Built on existing infrastructure** — BotQaSystem, BotManager, BotPersonality all ready
5. **Finds real bugs** — the 6 HYX-161 issues will be detected consistently
6. **Low risk** — no network, no external LLM API, no new dependencies

### Why LLM Agents Later (Phase 3)

1. **More flexible** — LLM can adapt to failures, try alternative approaches, explore edge cases
2. **Natural language test definition** — describe tests in English, agent figures out steps
3. **Better regression detection** — can notice unexpected behavior changes
4. **Scales to many test scenarios** — each scenario is just a Python script
5. **Can test dynamic content** — quest dialogue variations, combat timing, skill randomness

### How They Complement Each Other

```
Scripted Bots (Phase 1)
├── Deterministic — same steps every time
├── Fast — no LLM inference latency
├── Brittle — breaks if UI changes
└── Baseline — smoke tests, regression gates

LLM Agents (Phase 3)
├── Adaptive — adjusts to game state
├── Slow — LLM calls add latency
├── Flexible — explores edge cases
└── Deep QA — quest flows, edge cases, anomalies
```

---

## 8. Specific Files to Watch for Conflicts

### Files that must NOT be modified:
| File | Reason |
|:-----|:-------|
| `progressive_bots.yml` | Defines 110 bot identities; changing breaks persistent accounts |
| `game.yml` `bots.*` | Only add new config flags; don't change existing ones |
| `engine/*` | Core engine — changes need careful review; all QA work should live in `content/other/` |
| `api/account/*` | Account lifecycle — AgentBotService and PlayerBotService already handle this |

### Files that WILL conflict if worked on simultaneously:
| File | Conflict Risk |
|:-----|:-------------|
| `AgentBridgeScript.kt` | Both Phase 2 (testing module wiring) and Phase 3 (new actions) modify this |
| `BotQaSystem.kt` | Phase 1 adds test registrations; Phase 2 may refactor the interface |
| `TestingModule.kt` | Phase 2 completely rewrites this from stub to real implementation |
| `BotAction.kt` | Phase 2 adds `RunQaTest` action; separate teams need coordination |

### Safe to work on in parallel:
- Phase 1 scripts + Phase 2 testing framework (different submodules: progressive-bots vs agent-bridge)
- Phase 3 Python agents + any server-side work (separate repo location)
- Phase 4 HYX-161 fixes + any QA work (fixes don't touch QA infrastructure)

---

## 9. Quickstart: What To Build First

If you approve this plan, the immediate next steps are:

1. **Today:** Create the `TestRunner.kt` orchestration class in progressive-bots/qa/
2. **Day 1-2:** Build `LumbridgeDoorTest.kt` — exhaustively test every door in Lumbridge
3. **Day 2-3:** Build `CooksAssistantTest.kt` — full quest flow
4. **Day 3-4:** Build `NpcDialogueTest.kt` + `SkillFunctionalityTest.kt`
5. **Day 4-5:** Wire everything through `::botqa` commands, verify against HYX-161 bugs
6. **Day 5+:** Start Phase 2 (test framework) and/or Phase 3 (LLM agents) in parallel

The existing `::botqa` admin command already accepts a task name and broadcasts SUCCESS/FAILURE in public chat. Phase 1 is just: write real test logic behind each task name.
