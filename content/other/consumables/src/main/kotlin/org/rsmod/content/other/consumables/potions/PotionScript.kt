package org.rsmod.content.other.consumables.potions

import kotlin.math.min
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld2
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Potion drinking script that handles consuming all potions.
 *
 * Features:
 * - Stat boosts (attack, strength, defence)
 * - Prayer restoration (restore toward base, not boost)
 * - Antipoison / antivenom (stub: poison mechanic not yet implemented in 239)
 * - Dose tracking (4→3→2→1→vial)
 * - 3-tick delay between potions
 * - Run energy restoration (energy potions)
 */
class PotionScript : PluginScript() {

    override fun ScriptContext.startup() {
        // Register drink handler (op2 = right-click "Drink") for each potion dose
        for (potionName in PotionRegistry.ALL_POTION_NAMES) {
            runCatching {
                onOpHeld2(potionName) { drinkPotion(potionName, it.slot) }
            }
        }
    }

    /**
     * Handle drinking a potion from the specified inventory slot.
     */
    private suspend fun ProtectedAccess.drinkPotion(potionName: String, slot: Int) {
        // Verify the item is still in the expected slot
        val item = inv[slot] ?: return
        if (!item.isType(potionName)) {
            return
        }

        // Check action delay (can't drink if still on cooldown)
        if (actionDelay > mapClock) {
            return
        }

        val potionType = PotionRegistry.getPotionType(potionName) ?: return
        val effect = potionType.effect

        // Play drink animation
        anim("seq.human_eat")

        // Replace potion with next dose (or empty vial)
        val replacementName = PotionRegistry.getReplacement(potionName)
        if (replacementName != null) {
            invReplace(inv = inv, replace = potionName, count = 1, replacement = replacementName)
        }

        // Apply potion effect
        if (effect.isEnergyRestore) {
            // Energy potion - restore run energy
            restoreRunEnergy(effect.energyRestorePercent)
        } else if (effect.curesVenom) {
            // TODO: Wire to poison/venom system when implemented
            // with(poison) { cureVenom(effect.venomImmunityTicks, effect.poisonImmunityTicks) }
            mes("You feel a soothing sensation as the venom is cured.")
        } else if (effect.curesPoison) {
            // TODO: Wire to poison system when implemented
            // with(poison) { curePoison(effect.poisonImmunityTicks) }
            mes("You feel a little better.")
        } else if (effect.isRestore) {
            // Prayer potion - restore toward base (statHeal)
            statHeal(effect.stat, effect.constant, effect.percent)
        } else {
            // Stat boost potion - boost above base (statBoost)
            statBoost(effect.stat, effect.constant, effect.percent)
        }

        // Set drinking delay (3 ticks)
        actionDelay = mapClock + 3

        // Wait for the action to complete
        delay(3)
    }

    /**
     * Restore run energy by a percentage of maximum.
     * Energy potions restore 10% per dose in F2P.
     */
    private fun ProtectedAccess.restoreRunEnergy(percent: Int) {
        val restoreAmount = (constants.run_max_energy * percent) / 100
        val newEnergy = min(constants.run_max_energy, player.runEnergy + restoreAmount)
        player.runEnergy = newEnergy
        UpdateRun.energy(player, newEnergy)
    }
}
