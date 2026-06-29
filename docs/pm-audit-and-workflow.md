# OpenRune PM Audit + Strict Workflow

Date: 2026-06-28
Owner: Nei / Knowledge + Continuity
Scope: `/root/Runescape/open_rune/OpenRune-Server/`

## Executive state

OpenRune is nearly through Milestone 0 stabilization.

Immediate gate — resolved:
1. ~~Fix `RuneMysteries.kt` compile failure.~~ ✅ Done (267405c4)
2. ~~Full server compile green.~~ ✅ Done
3. ~~Server boot clean.~~ ✅ Done — 55 bots, 0 errors
4. **Next:** Lumbridge Layer 10 runtime walkthrough
5. **Then:** Decide DEF-001 castle elf doors fix vs defer

## Evidence gathered

### Git / board

- Branch: `main`
- `git status --short`: clean before this PM audit file was written.
- Recent relevant HEAD: `575678ca docs: finalize session — worklog, zone template, nei handoff`
- Kanban open states checked:
  - `todo`: 0
  - `ready`: 0
  - `running`: 0
  - `blocked`: 0

### Docs inventory

Relevant docs in `docs/`:

- `nei-handoff.md` — latest handoff; good starting point, but one compile diagnosis is stale.
- `worklog-239.md` — session work summary for Lumbridge.
- `zone-workflow.md` — current 10-layer zone template.
- `deferred-items.md` — known deferrals, currently `DEF-001` castle elf doors.
- `doors.md`, `gates.md`, `drops.md`, `instances.md`, `boss-hp-bar.md`, `quirks.md`, `RELEASE_CI.md` — subsystem/reference docs.

### Build checks

Commands run from repo root:

```bash
./gradlew :content:quest:compileKotlin --console=plain
```

Result: PASS.

```bash
./gradlew :server:app:compileKotlin --console=plain
```

Result: PASS.

```bash
./gradlew :server:app:run --console=plain
```

Result: PASS — boots clean, 55 bots, 0 errors, port 43594 listening.

QuestScript fix: `QuestScript.startup()` no longer registers shared UI handlers per-subclass — they register once via `registerSharedUIHandlers()` and dispatch by companion object lookup + player attribute.

### Code inventory

- Lumbridge Kotlin files: 27.
- `content/areas/city/lumbridge` compiles.
- Quest Kotlin files total: 15.
- Actual quest content observed:
  - `area/lumbridge/CooksAssistant.kt`
  - `area/wizards_tower/RuneMysteries.kt`
  - quest manager/framework files
- `Aubury.kt` exists in Varrock and participates in Rune Mysteries.

Important mismatch:

Older Hermes roadmap docs claim rev233/F2P foundation is complete, including `22/22 F2P quests`. That is not true for this OpenRune 239 working tree as inspected here. Treat those old `.hermes/plans/2026-06-20_rsmod-*` plans as historical strategy only, not current source of truth.

## Current roadmap truth

### Milestone 0 — Stabilize current repo

Status: active / blocking.

Done:
- Lumbridge NPC pass is implemented and compiles.
- 3-tier NPC methodology documented.
- Lumbridge zone workflow template exists.
- Castle elf-door root cause documented as `DEF-001`.
- Rune Mysteries exists as an implementation draft.
- Duke Horacio and Aubury have quest-related dialogue code.

Not done:
- `:content:quest:compileKotlin` is red.
- Full server compile not verified after latest quest work.
- Server restart / clean boot not done after latest code.
- Runtime walkthrough not done.
- Lumbridge Layer 5/6/7 status is mostly assumed from cache/modules, not player-tested.

Exit criteria:
- Quest module compile green.
- Full server compile green.
- Server boots cleanly.
- Lumbridge Layer 10 walkthrough logged.
- `zone-workflow.md` Lumbridge status updated from evidence.

### Milestone 1 — Close Lumbridge

Status: next after Milestone 0.

Work:
- Runtime test NPCs, shops, doors/gates/ladders/stairs, monsters, ground items, skills, and quest branches.
- Decide `DEF-001`:
  - Option A: fix now with a core mapping/eventbus solution.
  - Option B: keep deferred, explicitly mark Lumbridge as complete-with-known-deferral.
- Produce a Lumbridge completion snapshot.

Exit criteria:
- Every layer has PASS / DEFERRED / FAIL evidence.
- No silent assumptions remain.
- Any deferred item has owner, reason, and revisit trigger.

### Milestone 2 — Draynor Village as second template run

Status: do not start until Lumbridge closes.

Work:
- Run the same 10-layer workflow.
- Use Lumbridge's NPC tier method.
- Capture all deviations so the template improves.

Exit criteria:
- Draynor has the same layer table quality as Lumbridge.
- Any new patterns become skill/docs updates.

### Milestone 3 — Reconcile macro roadmap

Status: blocked by current repo truth.

Work:
- Replace/revise stale rev233 roadmap claims.
- Build a current OpenRune 239 roadmap from filesystem evidence, not old board/task summaries.
- Split into player-facing milestones: Lumbridge -> Draynor -> Misthalin loop -> F2P quest set -> early bot loop.

Exit criteria:
- One current roadmap exists.
- Old/stale plans are marked historical or superseded.
- Kanban only contains tasks derived from verified gaps.

## Strict PM workflow

### Canonical surfaces

Use these, in this order:

1. `docs/pm-audit-and-workflow.md` — current PM operating state and workflow.
2. `docs/worklog-239.md` — append-only work history for OpenRune 239.
3. `docs/zone-workflow.md` — reusable zone-completion template and current zone layer table.
4. `docs/deferred-items.md` — accepted known gaps that are not blocking the current milestone.
5. Kanban — executable tasks only.
6. `.hermes/plans/` — strategic plans only; stale plans must be marked historical before reuse.

Rule: if a claim conflicts, live repo evidence wins over docs, docs win over board summaries, board summaries win over memory.

### Phase gate

No new content milestone starts unless all gates pass:

- Gate A — Build green: affected module compile passes.
- Gate B — Full compile green: `:server:app:compileKotlin` passes, unless blocked by unrelated pre-existing failures that are documented.
- Gate C — Runtime status known: interactive content is either tested or explicitly marked `runtime-test: required`.
- Gate D — Docs updated: layer table, worklog, and deferred list reflect reality.
- Gate E — Review status known: code is either reviewed or explicitly waiting for review.

### Research gate

Before any implementation task is written, run a research pass and attach a small evidence snapshot.

Minimum research snapshot:

```yaml
research_snapshot:
  zone_or_system: draynor
  corpus_files_checked:
    - tools/data/corpus/parsed-data/npc_directory.json
    - tools/data/corpus/parsed-data/shops.json
    - tools/data/corpus/parsed-data/quest_lookup.json
  raw_cache_checked:
    - .data/raw-cache/map/npcs/draynor.toml
    - .data/raw-cache/map/objs/draynor.toml
  osrs_mcp_checked:
    - gameval_search: "npc.morgan"        # if symbol/name unclear
    - cache_search: "npc id/name"         # if combat/actions unclear
    - wiki_npc_spawns: "Count Draynor"    # if spawn coordinates unclear
  findings:
    - "Draynor raw cache has 88 NPC spawns / 58 unique symbols."
    - "Vampyre Slayer requires Morgan, stake/hammer/beer, Count Draynor combat."
  runtime_required: true
```

Rule: no new zone/quest/skill task may be scoped from memory alone. Use corpus/raw-cache first; use osrs-mcp when symbol, cache, varp/varbit, or NPC detail is ambiguous.

### Task spec template

Every implementation task must include:

```markdown
## Objective
One player-facing outcome.

## Scope
- Zone/quest/system:
- Layer:
- In scope:
- Out of scope:

## Files
- Create:
- Modify:
- Do not touch:

## Acceptance criteria
- [ ] Module compile command passes.
- [ ] Full compile command run or blocker documented.
- [ ] Runtime verification status recorded.
- [ ] Docs updated.

## Verification commands
```bash
./gradlew :content:<module>:compileKotlin --console=plain
./gradlew :server:app:compileKotlin --console=plain
```

## Runtime test
- required: yes/no
- exact walkthrough:

## Handoff
- What changed:
- Evidence:
- Remaining gaps:
```

### Execution rules

1. One active milestone at a time.
2. No task is "done" because a worker said done; it is done when evidence is attached.
3. Red build freezes new content. Only repair/review/docs tasks may proceed.
4. Large requests must be decomposed before dispatch. No `implement all`, `full system`, `all F2P`, `teleports`, `run energy`, or `boat travel` mega-cards.
5. Runtime-required tasks cannot complete silently. If no client walkthrough happened, mark `runtime-test: required` and leave the milestone open.
6. Deferred is allowed, but only with a ticket-style entry: ID, layer, impact, reason, owner, revisit trigger.
7. Every close updates docs before the next card is opened.
8. Every new pattern gets added to docs or a skill before it is reused.

### Role split

- Nei: PM state, roadmap, specs, docs, closeout discipline.
- Tai: implementation on scoped tasks.
- Rei: QA/review, build evidence, regression checks.
- Mai: debugging, infra, orchestration, runtime/server support.

### Daily/session ritual

Kickoff:
1. Read this file.
2. Check `git status --short`.
3. Check open kanban states.
4. Run the smallest relevant compile check.
5. Pick exactly one next gate.

During work:
1. Comment/checkpoint when a gate changes.
2. Do not expand scope mid-card.
3. If a blocker appears, record it in `deferred-items.md` or create a repair task.

Close:
1. Re-run verification commands.
2. Update `docs/worklog-239.md`.
3. Update layer/deferred status docs.
4. If code changed and needs review, block for review instead of declaring complete.
5. Only then move to the next milestone.

## Immediate next task package

Title: `fix: Rune Mysteries quest compile gate`

Assignee: Tai for implementation, Rei for review.

Objective: Make `:content:quest:compileKotlin` pass without weakening the quest framework.

Likely fix area:
- `content/quest/src/main/kotlin/org/rsmod/content/quest/area/wizards_tower/RuneMysteries.kt`
- Possibly `content/quest/src/main/kotlin/org/rsmod/content/quest/manager/QuestScript.kt` if framework support is needed.

Known failure:
- `RuneMysteries.kt` line 43 attempts `player.questState = prog.varp` but `Player.questState` is private inside `QuestScript`.

Acceptance:
- `./gradlew :content:quest:compileKotlin --console=plain` passes.
- `./gradlew :server:app:compileKotlin --console=plain` passes or unrelated failures are documented.
- `docs/nei-handoff.md` and/or `docs/worklog-239.md` updated with the corrected diagnosis.

## Open decisions for Josh

1. After Rune Mysteries compiles, should `DEF-001` castle doors block Lumbridge completion, or be accepted as deferred?
2. Should I create kanban cards for the strict next sequence now, or keep this as the manual operating doc until you approve the workflow?
