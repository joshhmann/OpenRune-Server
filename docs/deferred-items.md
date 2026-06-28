# Deferred Items

Items that are known gaps but not blocking current work.

## DEF-001: Castle doors not interactable

**Layer:** 4 — Interactables
**Zone:** Lumbridge
**Status:** 🔴 Deferred — needs root cause investigation

### What's wrong
All doors in Lumbridge Castle (double doors + interior single doors) return
"nothing interesting happens" when clicked. Generic single doors in other
buildings (poshdoor/poordoor via content groups) may or may not work.

### Root cause investigation
- Content groups ARE set in cache definitions (loc.toml):
  - `loc.castledoubledoorl` → `content.closed_left_door`
  - `loc.poshdoor` → `content.closed_single_door`
- DoubleDoorScript and DoorScript ARE loaded (214 scripts verified)
- `onOpLoc1("loc.castledoubledoorl")` handlers also registered but don't fire
- Winch and log-axe loc interactions work fine (same `onOpLoc1` API)

**Suspected cause:** The placed locs in the landscape binary data may use
different object type IDs than expected. The landscape decoder reads binary
data that maps to object types — if this mapping uses different type IDs
than what the TOML definitions describe, handlers won't match.

### To fix
1. Decode landscape binary for Lumbridge region to find actual object type IDs
   at door coordinates
2. Compare against known door loc types in loc.toml
3. Either add missing content groups or register per-ID handlers
4. Alternative: test with a debug handler that logs all loc interactions by
   object type ID to identify the actual types used

### Cross-zone impact
Doors will be broken in all zones until this is fixed — it's not
Lumbridge-specific.
