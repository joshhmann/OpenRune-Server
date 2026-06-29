package org.rsmod.content.skills.mining.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.SequenceServerType
import jakarta.inject.Inject
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentLoc2
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Mining
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val mapClock: MapClock,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpContentLoc1("content.ore") { mine(it.loc, it.type) }
        onOpContentLoc2("content.ore") { prospect(it.type) }
    }

    private suspend fun ProtectedAccess.mine(rock: BoundLocInfo, type: ObjectServerType) {
        val pickaxe = findPickaxe(player)
        if (pickaxe == null) {
            mes("You need a pickaxe to mine this rock.")
            mes("You do not have a pickaxe which you have the Mining level to use.")
            soundSynth("synth.pillory_wrong")
            return
        }

        if (player.miningLvl < type.rockLevelReq) {
            mes("You need a Mining level of ${type.rockLevelReq} to mine this rock.")
            soundSynth("synth.pillory_wrong")
            return
        }

        if (inv.isFull()) {
            val productName = resolveOreProductName(type)
            mes("Your inventory is too full to hold any more $productName.")
            soundSynth("synth.pillory_wrong")
            return
        }

        // First click: set up the action delay and queue the loop.
        if (actionDelay < mapClock) {
            actionDelay = mapClock + 3
            skillAnimDelay = mapClock + 3
            spam("You swing your pickaxe at the rock.")
            opLoc1(rock)
            return
        }

        // Refresh animation every ~4 ticks.
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + 4
            anim(RSCM.getReverseMapping(RSCMType.SEQ, getInvObj(pickaxe).pickaxeMiningAnim.id))
        }

        var minedOre = false

        if (actionDelay == mapClock) {
            val (low, high) = mineSuccessRate(type, getInvObj(pickaxe))
            minedOre = statRandom("stat.mining", low, high, invisibleLvls)
            actionDelay = mapClock + 3
        }

        // Roll for rock depletion.
        val depletes = minedOre && random.of(1, 255) <= type.rockDepleteChance

        if (minedOre) {
            val productRscm = resolveOreProduct(type)
            val productName = if (productRscm == "obj.uncut_sapphire" && type.rockLevelReq == 40) {
                "gems"
            } else {
                type.rockOre.name.lowercase()
            }
            val xp = type.rockXp * xpMods.get(player, "stat.mining")
            spam("You manage to mine some $productName.")
            statAdvance("stat.mining", xp)
            invAdd(inv, productRscm)
        }

        if (depletes) {
            val respawnTime = type.resolveRespawnTime(random)
            locRepo.change(rock, RSCM.getReverseMapping(RSCMType.LOC, type.rockDepletedLoc.id), respawnTime)
            resetAnim()
            return
        }

        // Rock still up — check inv space before re-queuing.
        if (inv.isFull()) {
            val productName = resolveOreProductName(type)
            mes("Your inventory is too full to hold any more $productName.")
            soundSynth("synth.pillory_wrong")
            resetAnim()
            return
        }

        opLoc1(rock)
    }

    private suspend fun ProtectedAccess.prospect(type: ObjectServerType) {
        val name = type.rockOre.name.lowercase()
        mes("This rock contains $name.")
    }

    // --- Gem handling ---

    private fun resolveOreProduct(type: ObjectServerType): String {
        return if (isGemRock(type)) {
            rollGemDrop()
        } else {
            RSCM.getReverseMapping(RSCMType.OBJ, type.rockOre.id)
        }
    }

    private fun resolveOreProductName(type: ObjectServerType): String {
        return if (isGemRock(type)) {
            "gems"
        } else {
            type.rockOre.name.lowercase()
        }
    }

    private fun isGemRock(type: ObjectServerType): Boolean {
        return type.rockLevelReq == 40 && type.rockOre.id == RSCM.getRSCM("obj.uncut_sapphire")
    }

    /**
     * Gem rock drop rates (OSRS approximate):
     * Opal ~46.9%, Jade ~22.7%, Red topaz ~14.8%, Sapphire ~7.0%,
     * Emerald ~4.7%, Ruby ~3.1%, Diamond ~0.8%
     */
    private fun rollGemDrop(): String {
        val roll = random.of(1, 128)
        return when {
            roll <= 60 -> "obj.uncut_opal"
            roll <= 89 -> "obj.uncut_jade"
            roll <= 108 -> "obj.uncut_red_topaz"
            roll <= 117 -> "obj.uncut_sapphire"
            roll <= 123 -> "obj.uncut_emerald"
            roll <= 127 -> "obj.uncut_ruby"
            else -> "obj.uncut_diamond"
        }
    }

    companion object {
        // --- Loc param extensions ---

        val ObjectServerType.rockLevelReq: Int by locParam(params.levelrequire)
        val ObjectServerType.rockOre: ItemServerType by locParam(params.skill_productitem)
        val ObjectServerType.rockXp: Double by locXpParam(params.skill_xp)
        val ObjectServerType.rockDepletedLoc: ObjectServerType by locParam(params.next_loc_stage)
        val ObjectServerType.rockDepleteChance: Int by locParam(params.deplete_chance)
        val ObjectServerType.rockRespawnTime: Int by locParam(params.respawn_time)
        val ObjectServerType.rockRespawnTimeLow: Int by locParam(params.respawn_time_low)
        val ObjectServerType.rockRespawnTimeHigh: Int by locParam(params.respawn_time_high)

        // --- Obj param extensions ---

        val ItemServerType.pickaxeMiningReq: Int by objParam(params.levelrequire)
        val ItemServerType.pickaxeMiningAnim: SequenceServerType by objParam(params.skill_anim)

        // --- Pickaxe selection ---

        fun findPickaxe(player: Player): InvObj? {
            val worn = player.wornPickaxe()
            val carried = player.carriedPickaxe()
            return when {
                worn != null && carried != null ->
                    if (getInvObj(worn).pickaxeMiningReq >= getInvObj(carried).pickaxeMiningReq) worn
                    else carried
                else -> worn ?: carried
            }
        }

        private fun Player.wornPickaxe(): InvObj? {
            val righthand = righthand ?: return null
            return righthand.takeIf { getInvObj(it).isUsablePickaxe(miningLvl) }
        }

        private fun Player.carriedPickaxe(): InvObj? {
            return inv.filterNotNull { getInvObj(it).isUsablePickaxe(miningLvl) }
                .maxByOrNull { getInvObj(it).pickaxeMiningReq }
        }

        private fun ItemServerType.isUsablePickaxe(miningLevel: Int): Boolean =
            isContentType("content.mining_pickaxe") && miningLevel >= pickaxeMiningReq

        // --- Success rate ---

        fun mineSuccessRate(type: ObjectServerType, pickaxe: ItemServerType): Pair<Int, Int> {
            val difficulty = type.rockDepleteChance.coerceIn(1, 255)
            val bonus = pickaxe.pickaxeTierBonus()
            val low = (bonus * difficulty / 512).coerceIn(1, 64)
            val high = ((bonus + 24) * difficulty / 384).coerceIn(low + 1, 255)
            return low to high
        }

        private fun ItemServerType.pickaxeTierBonus(): Int {
            return when {
                pickaxeMiningReq >= 71 -> 50
                pickaxeMiningReq >= 61 -> 42
                pickaxeMiningReq >= 41 -> 36
                pickaxeMiningReq >= 31 -> 30
                pickaxeMiningReq >= 21 -> 26
                pickaxeMiningReq >= 11 -> 21
                pickaxeMiningReq >= 6 -> 14
                pickaxeMiningReq >= 1 -> 9
                else -> 5
            }
        }

        private fun ObjectServerType.resolveRespawnTime(random: GameRandom): Int {
            val fixed = rockRespawnTime
            if (fixed > 0) return fixed
            val low = rockRespawnTimeLow
            val high = rockRespawnTimeHigh
            if (low > 0 && high > 0) return random.of(low, high)
            return 50
        }
    }
}
