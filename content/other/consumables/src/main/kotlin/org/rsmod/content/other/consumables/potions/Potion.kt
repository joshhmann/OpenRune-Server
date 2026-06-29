package org.rsmod.content.other.consumables.potions

/**
 * Represents a potion effect that can be applied when drinking.
 *
 * @property stat The stat to boost/restore (RSCM name like "stat.attack")
 * @property constant The flat boost amount
 * @property percent The percentage of base level to add (0-100)
 * @property isRestore Whether this restores toward base (true) or boosts above base (false)
 * @property curesPoison Whether this potion cures/downgrades poison or venom-to-poison
 * @property curesVenom Whether this potion fully cures venom (antivenom variants)
 * @property poisonImmunityTicks Ticks of poison immunity granted (0 = none)
 * @property venomImmunityTicks Ticks of venom immunity granted (0 = none)
 * @property isEnergyRestore Whether this potion restores run energy
 * @property energyRestorePercent Percentage of run energy to restore (0-100)
 */
data class PotionEffect(
    val stat: String,
    val constant: Int,
    val percent: Int,
    val isRestore: Boolean = false,
    val curesPoison: Boolean = false,
    val curesVenom: Boolean = false,
    val poisonImmunityTicks: Int = 0,
    val venomImmunityTicks: Int = 0,
    val isEnergyRestore: Boolean = false,
    val energyRestorePercent: Int = 0,
)

/**
 * Potion type definition with all dose variants.
 *
 * @property name Potion name (e.g., "Attack potion")
 * @property effect The potion effect when consumed
 * @property doseNames Map of dose count (1-4) to RSCM item name
 */
data class PotionType(
    val name: String,
    val effect: PotionEffect,
    val doseNames: Map<Int, String>,
) {
    /** Get the next dose item name (e.g., 4-dose -> 3-dose, 1-dose -> vial). */
    fun getNextDoseName(currentDose: Int): String? {
        return when (currentDose) {
            4 -> doseNames[3]
            3 -> doseNames[2]
            2 -> doseNames[1]
            1 -> PotionRegistry.EMPTY_VIAL
            else -> null
        }
    }

    /** Get dose number from item RSCM name. */
    fun getDoseFromName(itemName: String): Int? {
        return doseNames.entries.find { it.value == itemName }?.key
    }

    /** Check if item RSCM name is a dose of this potion. */
    fun isDose(itemName: String): Boolean {
        return doseNames.containsValue(itemName)
    }
}

/** Registry of all F2P potions. */
object PotionRegistry {

    /** Empty vial RSCM name. */
    const val EMPTY_VIAL = "obj.vial"

    /** All registered potions. */
    val ALL_POTIONS: List<PotionType> =
        listOf(
            // Energy potion: restores 10% run energy per dose
            PotionType(
                name = "Energy potion",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints", // Dummy stat, energy handled separately
                        constant = 0,
                        percent = 0,
                        isEnergyRestore = true,
                        energyRestorePercent = 10,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.energy_potion4",
                        3 to "obj.energy_potion3",
                        2 to "obj.energy_potion2",
                        1 to "obj.energy_potion1",
                    ),
            ),

            // Attack potion: +3 + 10% of base
            PotionType(
                name = "Attack potion",
                effect =
                    PotionEffect(
                        stat = "stat.attack",
                        constant = 3,
                        percent = 10,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.attack_potion4",
                        3 to "obj.attack_potion3",
                        2 to "obj.attack_potion2",
                        1 to "obj.attack_potion1",
                    ),
            ),

            // Strength potion: +3 + 10% of base
            PotionType(
                name = "Strength potion",
                effect =
                    PotionEffect(
                        stat = "stat.strength",
                        constant = 3,
                        percent = 10,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.strength_potion4",
                        3 to "obj.strength_potion3",
                        2 to "obj.strength_potion2",
                        1 to "obj.strength_potion1",
                    ),
            ),

            // Defence potion: +3 + 10% of base
            PotionType(
                name = "Defence potion",
                effect =
                    PotionEffect(
                        stat = "stat.defence",
                        constant = 3,
                        percent = 10,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.defence_potion4",
                        3 to "obj.defence_potion3",
                        2 to "obj.defence_potion2",
                        1 to "obj.defence_potion1",
                    ),
            ),

            // Prayer potion: restore 7 + 25% of base (toward base, not boost)
            PotionType(
                name = "Prayer potion",
                effect =
                    PotionEffect(
                        stat = "stat.prayer",
                        constant = 7,
                        percent = 25,
                        isRestore = true,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.prayer_potion4",
                        3 to "obj.prayer_potion3",
                        2 to "obj.prayer_potion2",
                        1 to "obj.prayer_potion1",
                    ),
            ),

            // Super attack: +5 + 15% of base
            PotionType(
                name = "Super attack",
                effect =
                    PotionEffect(
                        stat = "stat.attack",
                        constant = 5,
                        percent = 15,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.super_attack4",
                        3 to "obj.super_attack3",
                        2 to "obj.super_attack2",
                        1 to "obj.super_attack1",
                    ),
            ),

            // Super strength: +5 + 15% of base
            PotionType(
                name = "Super strength",
                effect =
                    PotionEffect(
                        stat = "stat.strength",
                        constant = 5,
                        percent = 15,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.super_strength4",
                        3 to "obj.super_strength3",
                        2 to "obj.super_strength2",
                        1 to "obj.super_strength1",
                    ),
            ),

            // Super defence: +5 + 15% of base
            PotionType(
                name = "Super defence",
                effect =
                    PotionEffect(
                        stat = "stat.defence",
                        constant = 5,
                        percent = 15,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.super_defence4",
                        3 to "obj.super_defence3",
                        2 to "obj.super_defence2",
                        1 to "obj.super_defence1",
                    ),
            ),

            // Antipoison: cures poison + 150-tick immunity (~90 seconds)
            PotionType(
                name = "Antipoison",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints", // Dummy stat, just for cure
                        constant = 0,
                        percent = 0,
                        curesPoison = true,
                        poisonImmunityTicks = 150,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.4doseantipoison",
                        3 to "obj.3doseantipoison",
                        2 to "obj.2doseantipoison",
                        1 to "obj.1doseantipoison",
                    ),
            ),

            // Superantipoison: cures poison + 300-tick immunity (~3 min)
            PotionType(
                name = "Superantipoison",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints",
                        constant = 0,
                        percent = 0,
                        curesPoison = true,
                        poisonImmunityTicks = 300,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.4dose2antipoison",
                        3 to "obj.3dose2antipoison",
                        2 to "obj.2dose2antipoison",
                        1 to "obj.1dose2antipoison",
                    ),
            ),

            // Antidote+: cures poison + 600-tick immunity (~6 min)
            PotionType(
                name = "Antidote+",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints",
                        constant = 0,
                        percent = 0,
                        curesPoison = true,
                        poisonImmunityTicks = 600,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.4doseantidote+",
                        3 to "obj.3doseantidote+",
                        2 to "obj.2doseantidote+",
                        1 to "obj.1doseantidote+",
                    ),
            ),

            // Antidote++: cures poison + 1200-tick immunity (~12 min)
            PotionType(
                name = "Antidote++",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints",
                        constant = 0,
                        percent = 0,
                        curesPoison = true,
                        poisonImmunityTicks = 1200,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.4doseantidote++",
                        3 to "obj.3doseantidote++",
                        2 to "obj.2doseantidote++",
                        1 to "obj.1doseantidote++",
                    ),
            ),

            // Antivenom: fully cures venom + 300-tick venom immunity
            PotionType(
                name = "Antivenom",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints",
                        constant = 0,
                        percent = 0,
                        curesVenom = true,
                        venomImmunityTicks = 300,
                        poisonImmunityTicks = 0,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.4doseantivenom",
                        3 to "obj.3doseantivenom",
                        2 to "obj.2doseantivenom",
                        1 to "obj.1doseantivenom",
                    ),
            ),

            // Antivenom+: fully cures venom + 1600-tick venom AND poison immunity (~16 min)
            PotionType(
                name = "Antivenom+",
                effect =
                    PotionEffect(
                        stat = "stat.hitpoints",
                        constant = 0,
                        percent = 0,
                        curesVenom = true,
                        venomImmunityTicks = 1600,
                        poisonImmunityTicks = 1600,
                    ),
                doseNames =
                    mapOf(
                        4 to "obj.4doseantivenom+",
                        3 to "obj.3doseantivenom+",
                        2 to "obj.2doseantivenom+",
                        1 to "obj.1doseantivenom+",
                    ),
            ),
        )

    /** All potion item RSCM names (all doses). */
    val ALL_POTION_NAMES: Set<String> = ALL_POTIONS.flatMap { it.doseNames.values }.toSet()

    /**
     * Get potion type from item RSCM name.
     */
    fun getPotionType(itemName: String): PotionType? {
        return ALL_POTIONS.find { it.isDose(itemName) }
    }

    /**
     * Get dose number from item RSCM name.
     */
    fun getDose(itemName: String): Int? {
        return getPotionType(itemName)?.getDoseFromName(itemName)
    }

    /**
     * Check if item RSCM name is a potion.
     */
    fun isPotion(itemName: String): Boolean {
        return itemName in ALL_POTION_NAMES
    }

    /**
     * Get the replacement item name after drinking.
     *
     * @param itemName The current potion item RSCM name
     * @return Replacement item RSCM name, or null
     */
    fun getReplacement(itemName: String): String? {
        val potion = getPotionType(itemName) ?: return null
        val dose = potion.getDoseFromName(itemName) ?: return null
        return potion.getNextDoseName(dose)
    }
}
