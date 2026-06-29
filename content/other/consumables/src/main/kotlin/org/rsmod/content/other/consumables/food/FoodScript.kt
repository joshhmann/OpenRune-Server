package org.rsmod.content.other.consumables.food

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.script.onOpHeld2
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Food eating script for all F2P food items.
 *
 * - Heals hitpoints (capped at base level)
 * - 3-tick delay (1 tick for combo food)
 * - Partial food replacement (pizzas)
 */
class FoodScript
@Inject
constructor(
    private val mapClock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (itemName in FoodRegistry.ALL_FOOD_ITEMS) {
            onOpHeld2(itemName) { eatFood(itemName) }
        }
    }

    private suspend fun ProtectedAccess.eatFood(itemName: String) {
        // Validate food and delay
        val healAmt = FoodRegistry.healAmount(itemName) ?: return
        if (actionDelay > mapClock) return

        // Play eat animation
        anim("seq.human_eat")

        // Consume or replace
        val replacement = FoodRegistry.replacement(itemName)
        if (replacement != null) {
            invReplace(inv, itemName, 1, replacement)
        } else {
            invDel(inv, itemName, 1)
        }

        // Heal
        if (itemName == "obj.anglerfish") {
            val baseHp = player.statBase("stat.hitpoints")
            val bonus =
                when (baseHp) {
                    in 1..24 -> 2
                    in 25..49 -> 4
                    in 50..74 -> 6
                    in 75..92 -> 8
                    in 93..99 -> 13
                    else -> 2
                }
            statHeal("stat.hitpoints", (baseHp / 10) + bonus, 0)
        } else {
            statHeal("stat.hitpoints", healAmt, 0)
        }

        // Set delay
        val delayTicks = if (FoodRegistry.isComboFood(itemName)) 1 else 3
        actionDelay = mapClock + delayTicks
        delay(delayTicks)
    }
}
