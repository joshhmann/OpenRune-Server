package org.rsmod.content.other.consumables.potions

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.config.Constants
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.script.onOpHeld2
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Potion drinking script for all F2P potions.
 *
 * - Stat boosts (attack, strength, defence)
 * - Prayer restoration (restore toward base)
 * - Dose tracking (4→3→2→1→vial)
 * - 3-tick delay between potions
 */
class PotionScript
@Inject
constructor(
    private val mapClock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (itemName in PotionRegistry.ALL_POTION_ITEMS) {
            onOpHeld2(itemName) { drinkPotion(itemName) }
        }
    }

    private suspend fun ProtectedAccess.drinkPotion(itemName: String) {
        val potion = PotionRegistry.findPotion(itemName) ?: return
        val effect = potion.effect

        // Check delay
        if (actionDelay > mapClock) return

        // Play drink animation
        anim("seq.human_eat")

        // Replace with next dose or empty vial
        val replacement = PotionRegistry.getReplacement(itemName)
        if (replacement != null) {
            invReplace(inv, itemName, 1, replacement)
        }

        // Apply effect
        when {
            effect.isEnergyRestore -> {
                val restore = (Constants.run_max_energy * effect.energyRestorePercent) / 100
                val newEnergy = min(Constants.run_max_energy, player.runEnergy + restore)
                player.runEnergy = newEnergy
                UpdateRun.energy(player, newEnergy)
            }
            effect.isRestore -> statHeal(effect.stat, effect.constant, effect.percent)
            else -> statBoost(effect.stat, effect.constant, effect.percent)
        }

        // Set drinking delay
        actionDelay = mapClock + 3
        delay(3)
    }
}
