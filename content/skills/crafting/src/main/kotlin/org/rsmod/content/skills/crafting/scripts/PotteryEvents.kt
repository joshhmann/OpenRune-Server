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
 * Pottery implementation for Crafting skill.
 *
 * Mechanics:
 * 1. Use soft clay on pottery wheel → Unfired pot/bowl
 * 2. Use unfired item on pottery oven → Finished fired item
 *
 * XP: Pot (wheel: 6.3 + fire: 6.3 = 12.6), Bowl (wheel: 15 + fire: 15 = 30)
 */
class PotteryEvents
@Inject
constructor(private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLocU("loc.potterywheel", "obj.softclay") { usePotteryWheel() }

        onOpLocU("loc.potteryoven", "obj.pot_unfired") { firePottery(PotteryItem.POT) }
        onOpLocU("loc.potteryoven", "obj.bowl_unfired") { firePottery(PotteryItem.BOWL) }
    }

    private suspend fun ProtectedAccess.usePotteryWheel() {
        if (!inv.contains("obj.softclay")) {
            mes("You need soft clay to use the pottery wheel.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = listOf(
                    SkillMultiEntry(
                        "obj.pot_unfired",
                        listOf(Material("obj.softclay"))
                    ),
                    SkillMultiEntry(
                        "obj.bowl_unfired",
                        listOf(Material("obj.softclay"))
                    ),
                )
            )
        ) { selection ->
            val isPot = selection.entry.internal == "obj.pot_unfired"
            val item = if (isPot) PotteryItem.POT else PotteryItem.BOWL

            if (player.craftingLvl < item.wheelLevelReq) {
                mes("You need a Crafting level of ${item.wheelLevelReq} to make this.")
                return@openSkillMulti
            }

            val startCoords = coords
            repeat(selection.amount) {
                if (coords != startCoords) return@repeat

                val removed = invDel(inv, "obj.softclay", 1)
                if (!removed.success) return@repeat

                anim("seq.human_potterywheel")
                delay(3)

                val xp = item.wheelXp * xpMods.get(player, "stat.crafting")
                statAdvance("stat.crafting", xp)
                invAdd(inv, selection.entry.internal, 1)
                mes("You shape the soft clay into a ${item.displayName.lowercase()}.")
            }
        }
    }

    private suspend fun ProtectedAccess.firePottery(item: PotteryItem) {
        if (player.craftingLvl < item.fireLevelReq) {
            mes("You need a Crafting level of ${item.fireLevelReq} to fire this.")
            return
        }

        if (!inv.contains(item.unfiredInternal)) {
            mes("You don't have any unfired ${item.displayName.lowercase()}s to fire.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "fire",
                entries = listOf(
                    SkillMultiEntry(item.firedInternal, listOf(Material(item.unfiredInternal)))
                )
            )
        ) { selection ->
            val startCoords = coords
            repeat(selection.amount) {
                if (coords != startCoords) return@repeat

                val removed = invDel(inv, item.unfiredInternal, 1)
                if (!removed.success) return@repeat

                anim("seq.potteryoven_quick")
                delay(3)

                val xp = item.fireXp * xpMods.get(player, "stat.crafting")
                statAdvance("stat.crafting", xp)
                invAdd(inv, item.firedInternal, 1)
                mes("You fire the ${item.displayName.lowercase()} in the oven.")
            }
        }
    }

    private enum class PotteryItem(
        val unfiredInternal: String,
        val firedInternal: String,
        val displayName: String,
        val wheelLevelReq: Int,
        val wheelXp: Double,
        val fireLevelReq: Int,
        val fireXp: Double,
    ) {
        POT(
            unfiredInternal = "obj.pot_unfired",
            firedInternal = "obj.pot_empty",
            displayName = "Pot",
            wheelLevelReq = 1,
            wheelXp = 6.3,
            fireLevelReq = 1,
            fireXp = 6.3,
        ),
        BOWL(
            unfiredInternal = "obj.bowl_unfired",
            firedInternal = "obj.bowl_empty",
            displayName = "Bowl",
            wheelLevelReq = 8,
            wheelXp = 15.0,
            fireLevelReq = 8,
            fireXp = 15.0,
        ),
    }
}
