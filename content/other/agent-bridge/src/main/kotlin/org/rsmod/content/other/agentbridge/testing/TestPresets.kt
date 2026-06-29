package org.rsmod.content.other.agentbridge.testing

/**
 * Named coordinate and entity presets for QA test locations in Lumbridge and surrounding areas.
 *
 * These presets allow test scripts to reference locations by name instead of hardcoding coordinates.
 * Each preset maps a human-readable key to its coordinate/entity data.
 */
object TestPresets {
    // ===== LUMBRIDGE NPCS =====
    data class NpcPreset(val name: String, val x: Int, val z: Int, val description: String)

    val LUMBRIDGE_NPCS: Map<String, NpcPreset> =
        mapOf(
            "cook" to NpcPreset("Cook", 3209, 3216, "Castle kitchen — Cook's Assistant quest"),
            "father_aereck" to NpcPreset("Father Aereck", 3241, 3207, "Church — Restless Ghost quest"),
            "farmer_fred" to NpcPreset("Farmer Fred", 3188, 3270, "North of castle — Sheep Shearer"),
            "shopkeeper" to NpcPreset("Shopkeeper", 3214, 3246, "General store"),
            "bob" to NpcPreset("Bob", 3231, 3203, "Axe shop west of castle"),
            "hans" to NpcPreset("Hans", 3222, 3219, "Castle courtyard — XP counter"),
            "duke_horacio" to NpcPreset("Duke Horacio", 3214, 3224, "Castle top floor — Rune Mysteries"),
            "guide" to NpcPreset("Guide", 3221, 3222, "Starting area tutorial"),
            "bellamy" to NpcPreset("Bellamy", 3244, 3206, "Church — young lad"),
            "goblin" to NpcPreset("Goblin", 3246, 3245, "Goblin area east of castle"),
            "chicken" to NpcPreset("Chicken", 3238, 3298, "Chicken farm"),
            "man" to NpcPreset("Man", 3225, 3225, "Wandering townsperson"),
            "woman" to NpcPreset("Woman", 3210, 3240, "Wandering townsperson"),
        )

    // ===== LUMBRIDGE LOCATIONS =====
    data class LocPreset(
        val name: String,
        val x: Int,
        val z: Int,
        val description: String,
        val locId: Int = -1,
    )

    val LUMBRIDGE_LOCS: Map<String, LocPreset> =
        mapOf(
            "castle_door_main" to LocPreset("Castle door (main)", 3212, 3220, "Front entrance to Lumbridge Castle"),
            "castle_door_north" to LocPreset("Castle door (north)", 3220, 3227, "North entrance to Lumbridge Castle"),
            "castle_door_south" to LocPreset("Castle door (south)", 3220, 3215, "South entrance to Lumbridge Castle"),
            "cook_range" to LocPreset("Cook's range", 3209, 3215, "Kitchen range for Cook's Assistant"),
            "cook_larder" to LocPreset("Cook's larder", 3207, 3217, "Kitchen larder for Cook's Assistant"),
            "church_door" to LocPreset("Church door", 3243, 3205, "Lumbridge Church entrance"),
            "general_store_counter" to LocPreset("General store counter", 3215, 3244, "General store service counter"),
            "fishing_shop_door" to LocPreset("Fishing shop door", 3240, 3229, "Fishing shop entrance"),
            "axe_shop_door" to LocPreset("Axe shop door", 3232, 3202, "Bob's axe shop entrance"),
            "windmill_door" to LocPreset("Windmill door", 3162, 3310, "Lumbridge windmill entrance"),
            "wizard_tower_door" to LocPreset("Wizard tower door", 3116, 3160, "Wizards' Tower entrance"),
            "cow_gate" to LocPreset("Cow gate", 3254, 3273, "Cow field gate"),
            "tree_normal" to LocPreset("Normal tree", 3215, 3222, "Typical tree for woodcutting"),
            "empty_goblin_armor" to LocPreset("Empty goblin armour", 3245, 3246, "Goblin armour spawn"),
            "lumbridge_fountain" to LocPreset("Fountain", 3221, 3224, "Castle courtyard fountain"),
            "lumbridge_bank_booth" to LocPreset("Bank booth", 3214, 3220, "Castry yard bank booth (in castle)"),
        )

    // ===== LUMBRIDGE WALK ROUTES =====
    data class RoutePreset(val name: String, val waypoints: List<Pair<Int, Int>>, val description: String)

    val LUMBRIDGE_ROUTES: Map<String, RoutePreset> =
        mapOf(
            "castle_circuit" to
                RoutePreset(
                    name = "Castle Circuit",
                    waypoints =
                        listOf(
                            3222 to 3222, // Start: courtyard
                            3209 to 3216, // Kitchen (Cook)
                            3241 to 3207, // Church (Father Aereck)
                            3231 to 3203, // Axe shop (Bob)
                            3222 to 3222, // Back to courtyard
                        ),
                    description = "Walk castle courtyard → kitchen → church → axe shop → courtyard",
                ),
            "town_circuit" to
                RoutePreset(
                    name = "Town Circuit",
                    waypoints =
                        listOf(
                            3222 to 3222, // Start: courtyard
                            3214 to 3246, // General store
                            3246 to 3245, // Goblin area
                            3238 to 3298, // Chicken farm
                            3255 to 3272, // Cow field
                            3222 to 3222, // Back to courtyard
                        ),
                    description = "Full Lumbridge town tour",
                ),
            "npc_walk" to
                RoutePreset(
                    name = "NPC Walk",
                    waypoints =
                        listOf(
                            3222 to 3219, // Hans
                            3209 to 3216, // Cook
                            3214 to 3224, // Duke Horacio
                            3241 to 3207, // Father Aereck
                            3214 to 3246, // Shopkeeper
                            3231 to 3203, // Bob
                        ),
                    description = "Walk to all important NPCs",
                ),
        )

    // ===== QUEST-RELATED ITEMS =====
    data class ItemPreset(val name: String, val itemId: Int, val description: String)

    val QUEST_ITEMS: Map<String, ItemPreset> =
        mapOf(
            "pot_of_flour" to ItemPreset("Pot of flour", 1933, "Cook's Assistant — flour"),
            "bucket_of_milk" to ItemPreset("Bucket of milk", 1927, "Cook's Assistant — milk"),
            "egg" to ItemPreset("Egg", 1944, "Cook's Assistant — egg"),
            "air_talisman" to ItemPreset("Air talisman", 1438, "Rune Mysteries — required item"),
            "shears" to ItemPreset("Shears", 1735, "Sheep Shearer — required item"),
            "bones" to ItemPreset("Bones", 526, "Restless Ghost — required item"),
            "tinderbox" to ItemPreset("Tinderbox", 590, "Firemaking"),
            "copper_ore" to ItemPreset("Copper ore", 436, "Mining"),
            "tin_ore" to ItemPreset("Tin ore", 438, "Mining"),
            "iron_ore" to ItemPreset("Iron ore", 440, "Mining"),
            "logs" to ItemPreset("Logs", 1511, "Woodcutting/Firemaking"),
        )
}
