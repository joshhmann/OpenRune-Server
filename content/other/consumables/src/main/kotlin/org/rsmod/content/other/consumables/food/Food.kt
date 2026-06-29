package org.rsmod.content.other.consumables.food

/**
 * A consumable food entry with its properties.
 *
 * @property itemName RSCM item name (e.g., "obj.trout")
 * @property healAmount Amount of hitpoints restored
 * @property isComboFood Whether this is combo food (1-tick eat delay in combat)
 * @property replacement Optional RSCM name of the replacement item after eating (e.g., pizza -> half)
 */
data class FoodEntry(
    val itemName: String,
    val healAmount: Int,
    val isComboFood: Boolean = false,
    val replacement: String? = null,
) {
    /** Eating delay in ticks (3 for normal food, 1 for combo food). */
    val eatDelay: Int = if (isComboFood) 1 else 3
}

/** Food registry containing all F2P and common food definitions. */
object FoodRegistry {

    /** All registered food entries, keyed by RSCM item name. */
    private val FOOD_BY_NAME: Map<String, FoodEntry> by lazy {
        ALL_FOOD.associateBy { it.itemName }
    }

    /**
     * Look up a food entry by its RSCM item name.
     *
     * @param itemName RSCM name (e.g., "obj.trout")
     * @return FoodEntry or null if not food
     */
    fun get(itemName: String): FoodEntry? = FOOD_BY_NAME[itemName]

    /**
     * Check if an item name is registered food.
     */
    fun isFood(itemName: String): Boolean = itemName in FOOD_BY_NAME

    /**
     * Get the heal amount for a food item.
     */
    fun getHealAmount(itemName: String): Int? = FOOD_BY_NAME[itemName]?.healAmount

    /**
     * Check if an item is combo food.
     */
    fun isComboFood(itemName: String): Boolean =
        FOOD_BY_NAME[itemName]?.isComboFood ?: false

    /**
     * Get the replacement item name after eating (e.g., pizza -> half pizza).
     */
    fun getReplacement(itemName: String): String? =
        FOOD_BY_NAME[itemName]?.replacement

    /**
     * Complete list of all food entries.
     *
     * Fish - from lowest to highest healing.
     * Pizzas have partial replacements.
     * Karambwan is combo food (1-tick eat).
     * Anglerfish has dynamic healing (calculated separately).
     */
    val ALL_FOOD: List<FoodEntry> =
        listOf(
            // Fish
            FoodEntry("obj.shrimps", 3),
            FoodEntry("obj.sardine", 4),
            FoodEntry("obj.cooked_chicken", 4),
            FoodEntry("obj.cooked_meat", 4),
            FoodEntry("obj.herring", 5),
            FoodEntry("obj.mackerel", 6),
            FoodEntry("obj.trout", 7),
            FoodEntry("obj.cod", 7),
            FoodEntry("obj.pike", 8),
            FoodEntry("obj.salmon", 9),
            FoodEntry("obj.tuna", 10),
            FoodEntry("obj.lobster", 12),
            FoodEntry("obj.bass", 13),
            FoodEntry("obj.swordfish", 14),
            FoodEntry("obj.monkfish", 16),
            FoodEntry("obj.shark", 20),

            // Bread
            FoodEntry("obj.bread", 5),

            // Pizzas (full -> half)
            FoodEntry("obj.plain_pizza", 7, replacement = "obj.half_plain_pizza"),
            FoodEntry("obj.meat_pizza", 8, replacement = "obj.half_meat_pizza"),
            FoodEntry("obj.anchovy_pizza", 9, replacement = "obj.half_anchovy_pizza"),

            // Pizza halves (no replacement = fully consumed)
            FoodEntry("obj.half_plain_pizza", 7),
            FoodEntry("obj.half_meat_pizza", 8),
            FoodEntry("obj.half_anchovy_pizza", 9),

            // Combo food (1-tick eat, can combo with other food)
            FoodEntry("obj.cooked_karambwan", 18, isComboFood = true),

            // Overheal (special case - healing calculated dynamically)
            FoodEntry("obj.anglerfish", 0), // Heal calculated in script
        )

    /** All food RSCM names for registration. */
    val ALL_FOOD_NAMES: Set<String> = ALL_FOOD.map { it.itemName }.toSet()
}
