package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc2
import org.rsmod.content.skills.thieving.configs.PickpocketEntry
import org.rsmod.content.skills.thieving.configs.PickpocketLoot
import org.rsmod.content.skills.thieving.configs.PickpocketTargets
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pickpocket script — NPC thieving via `onOpNpc2` ("Pickpocket" right-click
 * option).
 *
 * Ported from rsmod-233 `Thieving.kt` with 239 API adaptation:
 * - Typed NPC references → inline RSCM string names
 * - Typed Obj references → inline RSCM string names
 * - Typed stat ref → inline "stat.thieving"
 */
class PickpocketScript
@Inject
constructor(
    private val random: GameRandom,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        // Register handlers for every known pickpocket target.
        // Each NPC name resolved from the lookup map gets its own onOpNpc2.
        for ((npcId, entry) in PickpocketTargets.npcToEntry) {
            registerHandler(npcId, entry)
        }
    }

    private fun ScriptContext.registerHandler(npcId: String, entry: PickpocketEntry) {
        onOpNpc2(npcId) { pickpocket(it.npc, entry) }
    }

    private suspend fun ProtectedAccess.pickpocket(npc: Npc, entry: PickpocketEntry) {
        val level = player.thievingLvl

        if (level < entry.levelReq) {
            mes("You need a Thieving level of ${entry.levelReq} to pickpocket this target.")
            return
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to pickpocket.")
            return
        }

        anim("seq.human_pickpocket")
        delay(2)

        val success = rollPickpocketSuccess(level, entry)

        if (success) {
            onPickpocketSuccess(npc, entry)
        } else {
            onPickpocketFailure(npc, entry)
        }
    }

    private fun rollPickpocketSuccess(level: Int, entry: PickpocketEntry): Boolean {
        val successChance = computePickpocketChance(level, entry)
        return random.randomDouble() <= successChance
    }

    /**
     * Compute success probability clamped to [0.05, 0.95].
     *
     * Uses Alter's linear formula: baseSuccess + max(0, level - levelReq) * bonusPerLevel.
     * This approximates the OSRS hidden-level formula closely enough for RSPS purposes.
     */
    private fun computePickpocketChance(level: Int, entry: PickpocketEntry): Double {
        val levelsAbove = (level - entry.levelReq).coerceAtLeast(0)
        val chance = entry.baseSuccess + levelsAbove * entry.bonusPerLevel
        return chance.coerceIn(0.05, 0.95)
    }

    private fun ProtectedAccess.onPickpocketSuccess(npc: Npc, entry: PickpocketEntry) {
        statAdvance("stat.thieving", entry.xp)

        val loot = rollLoot(entry.loot)
        val amount = if (loot.min == loot.max) loot.min else random.of(loot.min, loot.max)
        invAddOrDrop(objRepo, loot.obj, amount)

        mes("You pick the ${npc.name.lowercase()}'s pocket.")
    }

    private suspend fun ProtectedAccess.onPickpocketFailure(npc: Npc, entry: PickpocketEntry) {
        mes("You fail to pick the ${npc.name.lowercase()}'s pocket.")
        // npc.say("Hands off!") — requires dialogue system, deferred

        val stunDamage =
            if (entry.stunDamageMin == entry.stunDamageMax) {
                entry.stunDamageMin
            } else {
                random.of(entry.stunDamageMin, entry.stunDamageMax)
            }

        // Queue the typeless stun hit before delaying so it lands on the first stun tick.
        if (stunDamage > 0) {
            queueHit(delay = 1, type = HitType.Typeless, damage = stunDamage)
        }

        // Stun: the protected-access delay holds the player locked for the stun duration.
        if (entry.stunTicks > 0) {
            delay(entry.stunTicks)
        }
    }

    private fun rollLoot(table: List<PickpocketLoot>): PickpocketLoot {
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
