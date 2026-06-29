package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.skills.thieving.configs.StallData
import org.rsmod.content.skills.thieving.configs.StallEntry
import org.rsmod.content.skills.thieving.configs.StallLoot
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Stall thieving script via `onOpLoc2` / `onOpLoc3` ("Steal-from" right-click
 * options on market stalls).
 *
 * Ported from rsmod-233 `Thieving.kt` with 239 API adaptation:
 * - Typed Loc references → inline RSCM string names
 * - Typed Obj references → inline RSCM string names
 * - Typed stat ref → inline "stat.thieving"
 * - `locRepo.change(loc, "loc.rag_market_stall", ticks)` for stall depletion
 *
 * Note: Most market stalls use op 3 ("Steal-from"). Tea stalls use op 2.
 * The handler map below registers each stall loc with its correct op slot.
 */
class StallScript
@Inject
constructor(
    private val random: GameRandom,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        // Op 3 is the standard "Steal-from" option for most market stalls
        for ((locId, entry) in StallData.locToEntry) {
            registerHandler(locId, entry, opSlot = 3)
        }

        // Tea stalls use op 2 instead of op 3
        registerTeaStall("loc.tea_stall")
        registerTeaStall("loc.icthalarins_tea_stall")
        registerTeaStall("loc.contact_tea_stall")
    }

    private fun ScriptContext.registerHandler(locId: String, entry: StallEntry, opSlot: Int) {
        when (opSlot) {
            3 -> onOpLoc3(locId) { stealFromStall(it.loc, entry) }
            2 -> onOpLoc2(locId) { stealFromStall(it.loc, entry) }
            else -> error("Unsupported op slot $opSlot for stall $locId")
        }
    }

    private fun ScriptContext.registerTeaStall(locId: String) {
        onOpLoc2(locId) { stealFromStall(it.loc, StallData.TEA) }
    }

    private suspend fun ProtectedAccess.stealFromStall(loc: BoundLocInfo, stall: StallEntry) {
        val level = player.thievingLvl

        if (level < stall.levelReq) {
            mes("You need a Thieving level of ${stall.levelReq} to steal from this stall.")
            return
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to steal from this stall.")
            return
        }

        anim("seq.human_pickpocket")
        delay(2)

        statAdvance("stat.thieving", stall.xp)

        val loot = rollLoot(stall.loot)
        val amount = if (loot.min == loot.max) loot.min else random.of(loot.min, loot.max)
        invAddOrDrop(objRepo, loot.obj, amount)

        mes("You steal from the stall.")

        // Replace the full stall with an empty placeholder for respawnTicks, then restore.
        locRepo.change(loc, stall.emptyLoc, stall.respawnTicks)
    }

    private fun rollLoot(table: List<StallLoot>): StallLoot {
        if (table.size == 1) return table.first()
        val totalWeight = table.sumOf { it.weight }
        val roll = random.randomDouble() * totalWeight
        var cumulative = 0.0
        for (entry in table) {
            cumulative += entry.weight
            if (roll < cumulative) return entry
        }
        return table.last()
    }
}
