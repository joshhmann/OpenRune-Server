# Deferred Items

Items that are known gaps but not blocking current work.

## DEF-001: Castle elf-fashioned doors not interactable

**Layer:** 4 — Interactables
**Zone:** Lumbridge
**Status:** 🔴 NEEDS CORE FIX — RSCM symbol mapping limitation

### Discovery
On official OSRS, the Duke's room elf-fashioned door (loc ID 1544,
`elfdooropen`) says "It's stuck." The crafting tutor door (closed variant,
ID 1543 `elfdoor`) should be openable.

### Root Cause
The RSCM symbol table doesn't have entries for loc IDs 1543/1544. When a
player clicks the door, `RSCM.getReverseMapping("loc", 1543)` throws because
the ID isn't in the mapping table, and the interaction falls through to
"nothing interesting happens."

**Cannot fix with `.rscm` files:** `GameValProvider.putMapping` requires
custom values to exceed `maxBaseID` (60849 for loc table). IDs 1543/1544
are within the existing range and get rejected.

**Cannot fix with TOML overlay:** TOML entries for loc types use RSCM
string names which require the reverse mapping to work — same root issue.

### Required Fix
Add the missing reverse mappings to the ConstantProvider at the right layer.
Either:
1. Insert entries into `ConstantProvider.mappings["loc"]` during
   `GameValProvider` processing (requires modifying the build pipeline)
2. Register handlers directly with `EventBus.subscribeSuspend(1543L, ...)`
   during script startup (bypasses RSCM entirely)
3. Add a custom MappingProvider with `ConstantProvider.addProvider()`
   during server init (requires server code change)

### Workaround
For now, these doors are decorative. The Duke's room door was already open,
and other elf doors serve as visual barriers. This affects only elf-fashioned
door types — all other loc types work normally (winch, log-axe, ladders,
gates, stairs, banks, bookcases, signposts, coops, pickables, search).
