package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import kotlin.random.Random
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Gem cutting implementation for Crafting skill.
 *
 * Mechanics: Use chisel on uncut gem to cut it. Semi-precious gems (opal, jade, red topaz)
 * have a chance to be crushed instead of cut.
 *
 * XP rates (F2P + P2P):
 *   Opal:        1 Crafting, 15 XP   (semi-precious, can crush)
 *   Jade:        13 Crafting, 20 XP  (semi-precious, can crush)
 *   Red topaz:   16 Crafting, 25 XP  (semi-precious, can crush)
 *   Sapphire:    20 Crafting, 50 XP
 *   Emerald:     27 Crafting, 67.5 XP
 *   Ruby:        34 Crafting, 85 XP
 *   Diamond:     43 Crafting, 107.5 XP
 *   Dragonstone: 55 Crafting, 137.5 XP
 */
class GemCuttingEvents
@Inject
constructor(private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeldU("obj.chisel", "obj.uncut_opal") { openGemCutting(GemType.OPAL) }
        onOpHeldU("obj.chisel", "obj.uncut_jade") { openGemCutting(GemType.JADE) }
        onOpHeldU("obj.chisel", "obj.uncut_red_topaz") { openGemCutting(GemType.RED_TOPAZ) }
        onOpHeldU("obj.chisel", "obj.uncut_sapphire") { openGemCutting(GemType.SAPPHIRE) }
        onOpHeldU("obj.chisel", "obj.uncut_emerald") { openGemCutting(GemType.EMERALD) }
        onOpHeldU("obj.chisel", "obj.uncut_ruby") { openGemCutting(GemType.RUBY) }
        onOpHeldU("obj.chisel", "obj.uncut_diamond") { openGemCutting(GemType.DIAMOND) }
        onOpHeldU("obj.chisel", "obj.uncut_dragonstone") { openGemCutting(GemType.DRAGONSTONE) }
    }

    private suspend fun ProtectedAccess.openGemCutting(gem: GemType) {
        if (player.craftingLvl < gem.levelReq) {
            mes("You need a Crafting level of ${gem.levelReq} to cut this gem.")
            return
        }

        val uncutInternal = gem.uncutInternal
        if (!inv.contains(uncutInternal)) {
            mes("You need an uncut gem to do this.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "cut",
                entries = listOf(
                    SkillMultiEntry(gem.cutInternal, listOf(Material(uncutInternal)))
                )
            )
        ) { selection ->
            val startCoords = coords
            repeat(selection.amount) {
                if (coords != startCoords) return@repeat

                val removed = invDel(inv, uncutInternal, 1)
                if (!removed.success) return@repeat

                anim("seq.human_gem_cut")
                delay(2)

                if (gem.canCrush && Random.nextInt(100) < CRUSH_CHANCE) {
                    mes("You accidentally crush the ${gem.displayName.lowercase()}.")
                } else {
                    val xp = gem.xp * xpMods.get(player, "stat.crafting")
                    statAdvance("stat.crafting", xp)
                    invAdd(inv, gem.cutInternal, 1)
                    mes("You cut the ${gem.displayName.lowercase()}.")
                }
            }
        }
    }

    private enum class GemType(
        val uncutInternal: String,
        val cutInternal: String,
        val displayName: String,
        val levelReq: Int,
        val xp: Double,
        val canCrush: Boolean = false,
    ) {
        OPAL("obj.uncut_opal", "obj.opal", "Opal", 1, 15.0, canCrush = true),
        JADE("obj.uncut_jade", "obj.jade", "Jade", 13, 20.0, canCrush = true),
        RED_TOPAZ("obj.uncut_red_topaz", "obj.red_topaz", "Red topaz", 16, 25.0, canCrush = true),
        SAPPHIRE("obj.uncut_sapphire", "obj.sapphire", "Sapphire", 20, 50.0),
        EMERALD("obj.uncut_emerald", "obj.emerald", "Emerald", 27, 67.5),
        RUBY("obj.uncut_ruby", "obj.ruby", "Ruby", 34, 85.0),
        DIAMOND("obj.uncut_diamond", "obj.diamond", "Diamond", 43, 107.5),
        DRAGONSTONE("obj.uncut_dragonstone", "obj.dragonstone", "Dragonstone", 55, 137.5),
    }

    companion object {
        private const val CRUSH_CHANCE = 10
    }
}
