# OpenRune Agent Instructions

This file mirrors the Hermes project context so non-Hermes coding agents and spawned workers follow the same gates.

Repo: `/root/Runescape/open_rune/OpenRune-Server/`

## Read first

1. `docs/pm-audit-and-workflow.md` — roadmap truth and strict PM workflow.
2. `docs/worklog-239.md` — append-only work history.
3. `docs/zone-workflow.md` — 10-layer zone workflow and evidence states.
4. `docs/deferred-items.md` — accepted known gaps.

If sources conflict: live repo evidence wins over docs; docs win over board summaries; board summaries win over memory.

## Current gate

Milestone 0 stabilization is active. Do not start new content milestones until these are resolved:

1. Fix `RuneMysteries.kt` quest compile failure.
2. Run `./gradlew :content:quest:compileKotlin --console=plain`.
3. Run `./gradlew :server:app:compileKotlin --console=plain` or document unrelated failures.
4. Boot/restart server only after compile is green.
5. Complete Lumbridge Layer 10 runtime walkthrough.
6. Decide whether `DEF-001` castle elf doors block Lumbridge completion or remain deferred.

## Operating rules

- One active milestone at a time.
- Red build freezes new content; only repair/review/docs tasks proceed.
- Do not scope tasks from memory alone. Attach corpus/raw-cache/osrs-mcp evidence first.
- Runtime-required work cannot close without walkthrough evidence or `runtime-test: required`.
- Every close updates `docs/worklog-239.md` and relevant status/deferred docs.

## Role split

- Nei: PM state, roadmap, specs, docs, closeout discipline.
- Mai: OpenRune dispatch lead: intake, worker readiness, runtime/server support, handoff validation, evidence enforcement.
- Tai: implementation on scoped tasks.
- Rei: QA/review, build evidence, regression checks.
