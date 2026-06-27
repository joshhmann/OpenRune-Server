package org.rsmod.content.other.progressivebots.economy

data class ToolUpgrade(
    val itemId: String,
    val requiredLevel: Int
)

data class TargetUpgrade(
    val locId: String,
    val requiredLevel: Int
)

object ProgressionRegistry {
    // Woodcutting tools
    val AXES = listOf(
        ToolUpgrade("bronze_axe", 1),
        ToolUpgrade("iron_axe", 1),
        ToolUpgrade("steel_axe", 6),
        ToolUpgrade("black_axe", 11),
        ToolUpgrade("mithril_axe", 21),
        ToolUpgrade("adamant_axe", 31),
        ToolUpgrade("rune_axe", 41)
    )
    
    // Mining tools
    val PICKAXES = listOf(
        ToolUpgrade("bronze_pickaxe", 1),
        ToolUpgrade("iron_pickaxe", 1),
        ToolUpgrade("steel_pickaxe", 6),
        ToolUpgrade("mithril_pickaxe", 21),
        ToolUpgrade("adamant_pickaxe", 31),
        ToolUpgrade("rune_pickaxe", 41)
    )

    fun getBestAxe(level: Int): String {
        return AXES.lastOrNull { it.requiredLevel <= level }?.itemId ?: "bronze_axe"
    }

    fun getBestPickaxe(level: Int): String {
        return PICKAXES.lastOrNull { it.requiredLevel <= level }?.itemId ?: "bronze_pickaxe"
    }

    val WOODCUTTING_TARGETS = listOf(
        TargetUpgrade("tree", 1),
        TargetUpgrade("oak_tree", 15),
        TargetUpgrade("willow_tree", 30),
        TargetUpgrade("yew_tree", 60),
        TargetUpgrade("magic_tree", 75)
    )

    val MINING_TARGETS = listOf(
        TargetUpgrade("copper_rocks", 1),
        TargetUpgrade("tin_rocks", 1),
        TargetUpgrade("iron_rocks", 15),
        TargetUpgrade("coal_rocks", 30),
        TargetUpgrade("mithril_rocks", 55),
        TargetUpgrade("adamantite_rocks", 70),
        TargetUpgrade("runite_rocks", 85)
    )

    fun getBestWoodcuttingTarget(level: Int): String {
        return WOODCUTTING_TARGETS.lastOrNull { it.requiredLevel <= level }?.locId ?: "tree"
    }

    fun getBestMiningTarget(level: Int): String {
        return MINING_TARGETS.lastOrNull { it.requiredLevel <= level }?.locId ?: "copper_rocks"
    }

    val WEAPONS = listOf(
        ToolUpgrade("bronze_sword", 1),
        ToolUpgrade("iron_sword", 1),
        ToolUpgrade("steel_sword", 5),
        ToolUpgrade("mithril_sword", 20),
        ToolUpgrade("adamant_sword", 30),
        ToolUpgrade("rune_sword", 40)
    )

    val NPC_TARGETS = listOf(
        TargetUpgrade("man", 1),
        TargetUpgrade("goblin", 5),
        TargetUpgrade("guard", 20),
        TargetUpgrade("hill_giant", 40),
        TargetUpgrade("moss_giant", 60)
    )

    fun getBestWeapon(level: Int): String {
        return WEAPONS.lastOrNull { it.requiredLevel <= level }?.itemId ?: "bronze_sword"
    }

    fun getBestNPCTarget(level: Int): String {
        return NPC_TARGETS.lastOrNull { it.requiredLevel <= level }?.locId ?: "man"
    }
}
