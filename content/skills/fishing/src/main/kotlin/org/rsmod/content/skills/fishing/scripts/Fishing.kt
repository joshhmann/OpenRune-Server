package org.rsmod.content.skills.fishing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// ---------------------------------------------------------------------------
// Implementation notes
// ---------------------------------------------------------------------------
// Fishing spots are NPCs (not locs), so the interaction pattern differs from
// Woodcutting's onOpContentLoc1. We use onOpNpc1 / onOpNpc2 bound to specific
// fishing spot NPC types registered in the server cache.
//
// Tick loop pattern mirrors Woodcutting:
//   - actionDelay = mapClock + 3  (3-tick initial wait)
//   - skillAnimDelay = mapClock + 4 (re-animate every 4 ticks)
//   - Success roll on actionDelay == mapClock
//   - Re-queue via opNpc1 / opNpc2 to continue
//
// Cache NPCs for fishing spots:
//   npc.0_50_50_freshfish  — freshwater net/bait spot
//   npc.0_50_49_saltfish   — saltwater cage/harpoon spot
//
// TODO (items): Many basic fishing tools and raw fish item IDs may need to
// be registered in the server cache / items.toml if not present. Update those
// data files before testing runtime behaviour.
//
// TODO (future):
//   - Lure/bait river spots (trout/salmon + pike) — requires npc.0_37_53_freshfish in cache
//   - Big net / harpoon member spots
//   - Monkfish, anglerfish, dark crab spots
//   - Fishing spot despawn/move mechanics
//   - Barbarian fishing (Agility + Strength XP)
//   - Dragon / infernal / crystal harpoon bonuses
//   - Minnows
// ---------------------------------------------------------------------------

class Fishing
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val mapClock: MapClock,
) : PluginScript() {

    override fun ScriptContext.startup() {
        // -------------------------------------------------------------------
        // Freshwater spot: net (op1) or bait (op2)
        // Lumbridge swamp, Draynor
        // -------------------------------------------------------------------
        onOpNpc1("npc.0_50_50_freshfish") {
            attemptFish(it.npc, NET_BAIT_NET_ACTION, opSlot = 1)
        }
        onOpNpc2("npc.0_50_50_freshfish") {
            attemptFish(it.npc, NET_BAIT_BAIT_ACTION, opSlot = 2)
        }

        // -------------------------------------------------------------------
        // Saltwater spot: cage (op1) or harpoon (op2)
        // Member areas: Karamja, Catherby, etc.
        // -------------------------------------------------------------------
        onOpNpc1("npc.0_50_49_saltfish") {
            attemptFish(it.npc, CAGE_HARPOON_CAGE_ACTION, opSlot = 1)
        }
        onOpNpc2("npc.0_50_49_saltfish") {
            attemptFish(it.npc, CAGE_HARPOON_HARPOON_ACTION, opSlot = 2)
        }
    }

    // -----------------------------------------------------------------------
    // Core tick loop — entry point called every time the player ops a spot
    // -----------------------------------------------------------------------

    /**
     * Main fishing handler.
     *
     * On first call (actionDelay < mapClock): sets a 3-tick initial delay and
     * plays the opening animation, then re-queues.
     *
     * On subsequent calls at the action tick (actionDelay == mapClock):
     * performs the success roll against the highest-level eligible fish.
     *
     * Re-queues via the same op slot to continue fishing until a stop
     * condition (full inventory, out of bait, level too low) is met.
     */
    private suspend fun ProtectedAccess.attemptFish(
        npc: Npc,
        action: SpotAction,
        opSlot: Int,
    ) {
        val (tool, catches) = action

        // Level check against the lowest-tier catch available at this spot
        val lowestReq = catches.minOf { it.levelReq }
        if (player.fishingLvl < lowestReq) {
            mes("You need a Fishing level of at least $lowestReq to fish here.")
            return
        }

        // Tool check
        if (!inv.contains(tool.obj)) {
            mes("You need ${tool.toolName} to fish here.")
            return
        }

        // Bait check — give immediate feedback before starting animation
        if (tool.baitItem != null && !inv.contains(tool.baitItem)) {
            mes("You need ${tool.baitName} to fish here.")
            return
        }

        // Inventory full
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more fish.")
            return
        }

        // -------------------------------------------------------------------
        // Animation — re-play every 4 ticks (matches Woodcutting cadence)
        // -------------------------------------------------------------------
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + 4
            anim(tool.anim)
        }

        // -------------------------------------------------------------------
        // Tick gate:
        //   actionDelay < mapClock   → first call, set initial 3-tick delay
        //   actionDelay != mapClock  → not yet time, wait and re-queue
        //   actionDelay == mapClock  → attempt the catch
        // -------------------------------------------------------------------
        if (actionDelay < mapClock) {
            actionDelay = mapClock + 3
            requeue(npc, opSlot)
            return
        }

        if (actionDelay != mapClock) {
            requeue(npc, opSlot)
            return
        }

        // actionDelay == mapClock: attempt the catch
        actionDelay = mapClock + 3

        // Find the highest-level eligible catch (OSRS behaviour: best fish rolls first)
        val eligible =
            catches
                .filter { player.fishingLvl >= it.levelReq }
                .sortedByDescending { it.levelReq }

        for (catch in eligible) {
            val success =
                statRandom("stat.fishing", catch.successLow, catch.successHigh, invisibleLvls)
            if (success) {
                // Consume bait on successful catch
                if (tool.baitItem != null) {
                    val deleted = invDel(inv, tool.baitItem, count = 1, strict = false)
                    if (deleted.failure) {
                        mes("You need ${tool.baitName} to fish here.")
                        return
                    }
                }

                val xp = catch.xp * xpMods.get(player, "stat.fishing")
                spam("You catch ${articleFor(catch.name)} ${catch.name}.")
                statAdvance("stat.fishing", xp)
                invAdd(inv, catch.obj, count = 1)
                break
            }
        }

        // Post-catch checks — stop conditions
        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more fish.")
            resetAnim()
            return
        }

        if (tool.baitItem != null && !inv.contains(tool.baitItem)) {
            mes("You need ${tool.baitName} to fish here.")
            resetAnim()
            return
        }

        // Continue fishing loop
        requeue(npc, opSlot)
    }

    /**
     * Re-dispatches interaction to the same op slot the player originally used,
     * so the correct handler (net vs bait vs cage vs harpoon) fires every tick.
     */
    private fun ProtectedAccess.requeue(npc: Npc, opSlot: Int) {
        when (opSlot) {
            1 -> opNpc1(npc)
            2 -> opNpc2(npc)
            else -> opNpc1(npc)
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun articleFor(name: String): String {
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        return if (name.first().lowercaseChar() in vowels) "an" else "a"
    }

    // -----------------------------------------------------------------------
    // Data tables — tools, catches, spot actions
    // -----------------------------------------------------------------------

    companion object {

        // --
        // FishingTool: describes a tool the player must have equipped / in inventory
        //
        // @param obj      RSCM string ID of the tool item
        // @param anim     RSCM string ID of the fishing animation sequence
        // @param baitItem (optional) RSCM string ID of the consumable bait item
        // @param toolName Display name for the tool in chat messages
        // @param baitName Display name for the bait in chat messages
        // --
        data class FishingTool(
            val obj: String,
            val anim: String,
            val baitItem: String? = null,
            val toolName: String,
            val baitName: String? = null,
        )

        // --
        // FishCatch: a single catchable fish definition
        //
        // @param obj         RSCM string ID of the raw fish item
        // @param levelReq    Fishing level required to attempt this catch
        // @param xp          Experience granted per successful catch
        // @param successLow  Low bound of the success rate range (0-255)
        // @param successHigh High bound of the success rate range (0-255)
        // @param name        Display name for chat messages (lowercase, singular)
        //
        // successLow / successHigh are scaled to the [0..255] integer range used
        // by RSMod's SkillingSuccessRate formula:
        //   rate = low + (high - low) * (level / 99)
        // Baseline values cross-referenced from OSRS wiki catch-rate mechanics.
        // --
        data class FishCatch(
            val obj: String,
            val levelReq: Int,
            val xp: Double,
            val successLow: Int,
            val successHigh: Int,
            val name: String,
        )

        // --
        // SpotAction: pairs a FishingTool with the list of fish catchable at a spot
        // --
        data class SpotAction(val tool: FishingTool, val catches: List<FishCatch>)

        // -------------------------------------------------------------------
        // Tool definitions
        // -------------------------------------------------------------------

        private val SMALL_NET =
            FishingTool(
                obj = "obj.small_fishing_net",
                anim = "seq.human_smallnet",
                baitItem = null,
                toolName = "a small fishing net",
            )

        private val FISHING_ROD =
            FishingTool(
                obj = "obj.fishing_rod",
                anim = "seq.human_fish_onspot",
                baitItem = "obj.fishing_bait",
                toolName = "a fishing rod",
                baitName = "fishing bait",
            )

        private val LOBSTER_POT =
            FishingTool(
                obj = "obj.lobster_pot",
                anim = "seq.human_lobster",
                baitItem = null,
                toolName = "a lobster pot",
            )

        private val HARPOON =
            FishingTool(
                obj = "obj.harpoon",
                anim = "seq.human_harpoon",
                baitItem = null,
                toolName = "a harpoon",
            )

        // -------------------------------------------------------------------
        // Fish catch definitions
        //
        // Level reqs and XP rates are OSRS wiki-accurate for all F2P fish.
        // successLow / successHigh are approximations scaled to 0-255 range.
        // Tune with live data when available.
        // -------------------------------------------------------------------

        // Net catches
        private val SHRIMPS =
            FishCatch(
                obj = "obj.raw_shrimp",
                levelReq = 1,
                xp = 10.0,
                successLow = 64,
                successHigh = 164,
                name = "shrimp",
            )

        private val ANCHOVIES =
            FishCatch(
                obj = "obj.raw_anchovies",
                levelReq = 15,
                xp = 40.0,
                successLow = 56,
                successHigh = 140,
                name = "anchovies",
            )

        // Bait catches (freshwater)
        private val SARDINE =
            FishCatch(
                obj = "obj.raw_sardine",
                levelReq = 5,
                xp = 20.0,
                successLow = 64,
                successHigh = 152,
                name = "sardine",
            )

        private val HERRING =
            FishCatch(
                obj = "obj.raw_herring",
                levelReq = 10,
                xp = 30.0,
                successLow = 64,
                successHigh = 140,
                name = "herring",
            )

        // Lure catches — unpinned until river fishing spots are added to cache
        // val TROUT = FishCatch(...)
        // val SALMON = FishCatch(...)

        // Bait catch (pike) — unpinned until river spot added
        // val PIKE = FishCatch(...)

        // Big net catches — member area
        // val MACKEREL = FishCatch(...)
        // val COD = FishCatch(...)
        // val BASS = FishCatch(...)

        // Cage catch
        private val LOBSTER =
            FishCatch(
                obj = "obj.raw_lobster",
                levelReq = 40,
                xp = 90.0,
                successLow = 64,
                successHigh = 150,
                name = "lobster",
            )

        // Harpoon catches
        private val TUNA =
            FishCatch(
                obj = "obj.raw_tuna",
                levelReq = 35,
                xp = 80.0,
                successLow = 64,
                successHigh = 140,
                name = "tuna",
            )

        private val SWORDFISH =
            FishCatch(
                obj = "obj.raw_swordfish",
                levelReq = 50,
                xp = 100.0,
                successLow = 56,
                successHigh = 130,
                name = "swordfish",
            )

        // Shark / monkfish / anglerfish — P2P, uncomment when spots exist in cache
        // val SHARK = FishCatch(obj = "obj.raw_shark", levelReq = 76, xp = 110.0, ...)
        // val MONKFISH = FishCatch(obj = "obj.raw_monkfish", levelReq = 62, xp = 120.0, ...)
        // val ANGLERFISH = FishCatch(obj = "obj.raw_anglerfish", levelReq = 82, xp = 120.0, ...)

        // -------------------------------------------------------------------
        // Spot actions
        // -------------------------------------------------------------------

        val NET_BAIT_NET_ACTION =
            SpotAction(tool = SMALL_NET, catches = listOf(SHRIMPS, ANCHOVIES))

        val NET_BAIT_BAIT_ACTION =
            SpotAction(tool = FISHING_ROD, catches = listOf(SARDINE, HERRING))

        val CAGE_HARPOON_CAGE_ACTION =
            SpotAction(tool = LOBSTER_POT, catches = listOf(LOBSTER))

        val CAGE_HARPOON_HARPOON_ACTION =
            SpotAction(tool = HARPOON, catches = listOf(TUNA, SWORDFISH))
    }
}
