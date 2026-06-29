package org.rsmod.content.other.consumables.food

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld2
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Food eating script that handles consuming food items.
 *
 * Features:
 * - Heals hitpoints (capped at base level)
 * - 3-tick delay between eating (1 tick for combo foods)
 * - Partial foods (pizzas) replaced correctly
 * - Eat animation on consumption
 */
class FoodScript : PluginScript() {

    override fun ScriptContext.startup() {
        // Register eat handler (op2 = right-click "Eat") for each food item
        for (food in FoodRegistry.ALL_FOOD) {
            runCatching {
                onOpHeld2(food.itemName) { eatFood(food, it.slot) }
            }
        }
    }

    /**
     * Handle eating food from the specified inventory slot.
     */
    private suspend fun ProtectedAccess.eatFood(food: FoodEntry, slot: Int) {
        // Verify the item is still in the expected slot
        val item = inv[slot] ?: return
        if (!item.isType(food.itemName)) {
            return
        }

        // Check action delay (can't eat if still on cooldown)
        if (actionDelay > mapClock) {
            return
        }

        // Calculate heal amount
        val healAmount = calculateHealAmount(food)

        // Play eat animation
        anim("seq.human_eat")

        // Consume the food from inventory
        val replacementName = food.replacement
        if (replacementName != null) {
            // Partial food (pizza) - replace with half
            invReplace(inv = inv, replace = food.itemName, count = 1, replacement = replacementName)
        } else {
            // Normal food - delete from inventory
            invDel(inv = inv, type = food.itemName, count = 1)
        }

        // Heal hitpoints (capped at base level)
        if (healAmount > 0) {
            statHeal("stat.hitpoints", healAmount, 0)
        }

        // Set eating delay
        actionDelay = mapClock + food.eatDelay

        // Wait for the action to complete
        delay(food.eatDelay)
    }

    /**
     * Calculate the heal amount for a food item.
     * Most foods have fixed heal amounts; some (like anglerfish) are dynamic.
     */
    private fun ProtectedAccess.calculateHealAmount(food: FoodEntry): Int {
        if (food.itemName == "obj.anglerfish") {
            return calculateAnglerfishHeal()
        }
        return food.healAmount
    }

    /**
     * Calculate anglerfish heal amount. Heals floor(baseHp/10) + tier bonus:
     * - HP 1-24: +2 (total: floor(hp/10) + 2)
     * - HP 25-49: +4
     * - HP 50-74: +6
     * - HP 75-92: +8
     * - HP 93-99: +13
     */
    private fun ProtectedAccess.calculateAnglerfishHeal(): Int {
        val baseHp = statBase("stat.hitpoints")
        val tierBonus =
            when (baseHp) {
                in 1..24 -> 2
                in 25..49 -> 4
                in 50..74 -> 6
                in 75..92 -> 8
                in 93..99 -> 13
                else -> 2
            }
        return (baseHp / 10) + tierBonus
    }
}
