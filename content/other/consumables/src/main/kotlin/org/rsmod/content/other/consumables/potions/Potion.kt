package org.rsmod.content.other.consumables.potions

/**
 * Potion definition with its effects and dose item names.
 */
data class PotionDef(
    val name: String,
    val effect: PotionEffect,
    val doses: List<String>, // [4-dose, 3-dose, 2-dose, 1-dose]
)

/**
 * Potion stat effect definition.
 */
data class PotionEffect(
    val stat: String, // e.g., "stat.attack"
    val constant: Int,
    val percent: Int,
    val isRestore: Boolean = false,
    val isEnergyRestore: Boolean = false,
    val energyRestorePercent: Int = 0,
)

/**
 * Registry of all F2P potions using RSCM item names.
 */
object PotionRegistry {

    val ALL_POTIONS: List<PotionDef> =
        listOf(
            PotionDef(
                name = "Energy potion",
                effect = PotionEffect("stat.hitpoints", 0, 0, isEnergyRestore = true, energyRestorePercent = 10),
                doses = listOf("obj.energy_potion4", "obj.energy_potion3", "obj.energy_potion2", "obj.energy_potion1"),
            ),
            PotionDef(
                name = "Attack potion",
                effect = PotionEffect("stat.attack", constant = 3, percent = 10),
                doses = listOf("obj.attack_potion4", "obj.attack_potion3", "obj.attack_potion2", "obj.attack_potion1"),
            ),
            PotionDef(
                name = "Strength potion",
                effect = PotionEffect("stat.strength", constant = 3, percent = 10),
                doses = listOf("obj.strength_potion4", "obj.strength_potion3", "obj.strength_potion2", "obj.strength_potion1"),
            ),
            PotionDef(
                name = "Defence potion",
                effect = PotionEffect("stat.defence", constant = 3, percent = 10),
                doses = listOf("obj.defence_potion4", "obj.defence_potion3", "obj.defence_potion2", "obj.defence_potion1"),
            ),
            PotionDef(
                name = "Prayer potion",
                effect = PotionEffect("stat.prayer", constant = 7, percent = 25, isRestore = true),
                doses = listOf("obj.prayer_potion4", "obj.prayer_potion3", "obj.prayer_potion2", "obj.prayer_potion1"),
            ),
            PotionDef(
                name = "Super attack",
                effect = PotionEffect("stat.attack", constant = 5, percent = 15),
                doses = listOf("obj.super_attack4", "obj.super_attack3", "obj.super_attack2", "obj.super_attack1"),
            ),
            PotionDef(
                name = "Super strength",
                effect = PotionEffect("stat.strength", constant = 5, percent = 15),
                doses = listOf("obj.super_strength4", "obj.super_strength3", "obj.super_strength2", "obj.super_strength1"),
            ),
            PotionDef(
                name = "Super defence",
                effect = PotionEffect("stat.defence", constant = 5, percent = 15),
                doses = listOf("obj.super_defence4", "obj.super_defence3", "obj.super_defence2", "obj.super_defence1"),
            ),
        )

    /** All potion dose RSCM item names. */
    val ALL_POTION_ITEMS: Set<String> = ALL_POTIONS.flatMap { it.doses }.toSet()

    /** Empty vial RSCM name. */
    const val VIAL_EMPTY: String = "obj.vial"

    fun findPotion(itemName: String): PotionDef? =
        ALL_POTIONS.find { it.doses.contains(itemName) }

    fun getDose(itemName: String): Int? {
        val potion = findPotion(itemName) ?: return null
        return potion.doses.indexOf(itemName).let { if (it >= 0) it + 1 else null }
    }

    fun getReplacement(itemName: String): String? {
        val potion = findPotion(itemName) ?: return null
        val dose = getDose(itemName) ?: return null
        return if (dose > 1) potion.doses[dose - 2] else VIAL_EMPTY
    }
}
