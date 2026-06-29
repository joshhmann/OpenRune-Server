package org.rsmod.content.other.consumables.food

/**
 * Food registry containing all F2P food definitions.
 *
 * Maps RSCM item names to their heal amounts and properties.
 * Naming follows the 239 cache convention (e.g., "obj.trout" for cooked trout).
 */
object FoodRegistry {

    private data class FoodEntry(
        val healAmount: Int,
        val isComboFood: Boolean = false,
        val replacement: String? = null,
    )

    private val FOOD_MAP: Map<String, FoodEntry> =
        mapOf(
            // ── Fish ─────────────────────────────────────────
            "obj.shrimps" to FoodEntry(3),
            "obj.sardine" to FoodEntry(4),
            "obj.cooked_chicken" to FoodEntry(4),
            "obj.cooked_meat" to FoodEntry(4),
            "obj.herring" to FoodEntry(5),
            "obj.mackerel" to FoodEntry(6),
            "obj.trout" to FoodEntry(7),
            "obj.cod" to FoodEntry(7),
            "obj.pike" to FoodEntry(8),
            "obj.salmon" to FoodEntry(9),
            "obj.tuna" to FoodEntry(10),
            "obj.lobster" to FoodEntry(12),
            "obj.bass" to FoodEntry(13),
            "obj.swordfish" to FoodEntry(14),
            "obj.monkfish" to FoodEntry(16),
            "obj.shark" to FoodEntry(20),

            // ── Bread ────────────────────────────────────────
            "obj.bread" to FoodEntry(5),

            // ── Pizzas ───────────────────────────────────────
            "obj.plain_pizza" to FoodEntry(7, replacement = "obj.half_plain_pizza"),
            "obj.meat_pizza" to FoodEntry(8, replacement = "obj.half_meat_pizza"),
            "obj.anchovy_pizza" to FoodEntry(9, replacement = "obj.half_anchovy_pizza"),

            // ── Pizza halves ─────────────────────────────────
            "obj.half_plain_pizza" to FoodEntry(7),
            "obj.half_meat_pizza" to FoodEntry(8),
            "obj.half_anchovy_pizza" to FoodEntry(9),

            // ── Combo food ───────────────────────────────────
            "obj.cooked_karambwan" to FoodEntry(18, isComboFood = true),

            // ── Overheal (special case) ──────────────────────
            "obj.anglerfish" to FoodEntry(0), // dynamic heal, calculated separately
        )

    /** All food RSCM item names. */
    val ALL_FOOD_ITEMS: Set<String> = FOOD_MAP.keys

    fun healAmount(itemName: String): Int? = FOOD_MAP[itemName]?.healAmount

    fun isComboFood(itemName: String): Boolean = FOOD_MAP[itemName]?.isComboFood ?: false

    fun replacement(itemName: String): String? = FOOD_MAP[itemName]?.replacement

    fun isFood(itemName: String): Boolean = FOOD_MAP.containsKey(itemName)
}
