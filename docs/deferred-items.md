# Deferred Items

Items that are known gaps but not blocking current work.

## DEF-001: Castle elf-fashioned doors not interactable

**Layer:** 4 — Interactables
**Zone:** Lumbridge
**Status:** 🟢 RESOLVED — Not a bug, matches official OSRS

### Discovery
On official OSRS, the Duke's room elf-fashioned door (loc ID 1544,
`elfdooropen`) also says "It's stuck." when clicked. These doors are
intentionally non-functional — they're decorative.

### Fix
Added `onOpLoc1("loc.elfdoor")` and `onOpLoc1("loc.elfdooropen")` handlers
in LumbridgeScript that display "It's stuck." with a lever sound, matching
official behavior.

### What about other doors?
- **Castle double doors** (loc.castledoubledoorl/r) — have handlers via
  DoubleDoorScript content groups. Need in-game testing to confirm.
- **Single doors** (loc.poshdoor / loc.poordoor) — have handlers via
  DoorScript content groups.
- The Duke's room doors specifically use a DIFFERENT loc type
  (`elfdoor`/`elfdooropen`, ID 1543/1544) that has no content group and no
  swappable state — they're purely decorative.
