package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Spinning wheel implementation for Crafting skill.
 *
 * Mechanics: Use wool on spinning wheel → Ball of wool
 * Level: 1 Crafting, 2.5 XP
 */
class SpinningEvents
@Inject
constructor(private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU("loc.spinningwheel", "obj.wool") { spinWool() }
    }

    private suspend fun ProtectedAccess.spinWool() {
        if (player.craftingLvl < 1) {
            mes("You need a Crafting level of 1 to spin wool.")
            return
        }

        if (!inv.contains("obj.wool")) {
            mes("You need wool to use the spinning wheel.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "spin",
                entries = listOf(
                    SkillMultiEntry("obj.ball_of_wool", listOf(Material("obj.wool")))
                )
            )
        ) { selection ->
            val startCoords = coords
            repeat(selection.amount) {
                if (coords != startCoords) return@repeat
                if (!inv.contains("obj.wool")) return@repeat

                val removed = invDel(inv, "obj.wool", 1)
                if (!removed.success) return@repeat

                anim("seq.human_spinningwheel")
                delay(3)

                val xp = SPIN_XP * xpMods.get(player, "stat.crafting")
                statAdvance("stat.crafting", xp)
                invAdd(inv, "obj.ball_of_wool", 1)
                mes("You spin the wool into a ball.")
            }
        }
    }

    companion object {
        private const val SPIN_XP = 2.5
    }
}
