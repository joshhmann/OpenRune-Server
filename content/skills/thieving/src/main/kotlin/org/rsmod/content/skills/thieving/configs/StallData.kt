package org.rsmod.content.skills.thieving.configs

/**
 * Stall thieving data definitions ported from rsmod-233 source.
 *
 * Each entry defines a stall type that can be stolen from: the level
 * requirement, XP reward, the empty-stall variant (for respawn), respawn
 * tick duration, and weighted loot table.
 *
 * Source: `osrs-ps-dev-source/rsmod/content/skills/thieving/scripts/Thieving.kt`
 * Data sourced from Alter stalls.json + OSRS wiki.
 */
public data class StallLoot(
    val obj: String,
    val min: Int = 1,
    val max: Int = min,
    val weight: Double = 1.0,
)

public data class StallEntry(
    val levelReq: Int,
    val xp: Double,
    val emptyLoc: String,
    val respawnTicks: Int,
    val loot: List<StallLoot>,
)

@Suppress("MemberVisibilityCanBePrivate")
public object StallData {

    val VEGETABLE =
        StallEntry(
            levelReq = 2,
            xp = 10.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 14,
            loot =
                listOf(
                    StallLoot("obj.potato", weight = 30.0),
                    StallLoot("obj.onion", weight = 25.0),
                    StallLoot("obj.cabbage", weight = 25.0),
                    StallLoot("obj.tomato", weight = 15.0),
                    StallLoot("obj.garlic", weight = 5.0),
                ),
        )

    val BAKERS =
        StallEntry(
            levelReq = 5,
            xp = 16.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 14,
            loot =
                listOf(
                    StallLoot("obj.cake", weight = 35.0),
                    StallLoot("obj.bread", weight = 35.0),
                    StallLoot("obj.chocolate_slice", weight = 30.0),
                ),
        )

    val TEA =
        StallEntry(
            levelReq = 5,
            xp = 16.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 14,
            loot = listOf(StallLoot("obj.cup_of_tea")),
        )

    val SILK =
        StallEntry(
            levelReq = 20,
            xp = 24.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 16,
            loot = listOf(StallLoot("obj.silk")),
        )

    val ARDOUGNE_SILK =
        StallEntry(
            levelReq = 22,
            xp = 27.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 16,
            loot = listOf(StallLoot("obj.silk")),
        )

    val SEED =
        StallEntry(
            levelReq = 27,
            xp = 10.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 16,
            loot =
                listOf(
                    StallLoot("obj.potato_seed", weight = 20.0),
                    StallLoot("obj.onion_seed", weight = 18.0),
                    StallLoot("obj.cabbage_seed", weight = 15.0),
                    StallLoot("obj.tomato_seed", weight = 12.0),
                    StallLoot("obj.sweetcorn_seed", weight = 10.0),
                    StallLoot("obj.strawberry_seed", weight = 8.0),
                    StallLoot("obj.watermelon_seed", weight = 6.0),
                    StallLoot("obj.snape_grass_seed", weight = 5.0),
                    StallLoot("obj.guam_seed", weight = 4.0),
                    StallLoot("obj.marrentill_seed", weight = 3.0),
                    StallLoot("obj.harralander_seed", weight = 3.0),
                    StallLoot("obj.jute_seed", weight = 3.0),
                    StallLoot("obj.apple_tree_seed", weight = 1.5),
                    StallLoot("obj.banana_tree_seed", weight = 1.5),
                ),
        )

    val FUR =
        StallEntry(
            levelReq = 35,
            xp = 36.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 18,
            loot =
                listOf(
                    StallLoot("obj.fur", weight = 40.0),
                    StallLoot("obj.fur", weight = 35.0),
                    StallLoot("obj.grey_wolf_fur", weight = 25.0),
                ),
        )

    val FISH =
        StallEntry(
            levelReq = 42,
            xp = 42.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 18,
            loot =
                listOf(
                    StallLoot("obj.raw_trout", weight = 30.0),
                    StallLoot("obj.raw_salmon", weight = 30.0),
                    StallLoot("obj.raw_tuna", weight = 25.0),
                    StallLoot("obj.raw_lobster", weight = 15.0),
                ),
        )

    val SILVER =
        StallEntry(
            levelReq = 50,
            xp = 54.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 20,
            loot =
                listOf(
                    StallLoot("obj.silver_ore", weight = 75.0),
                    StallLoot("obj.silver_bar", weight = 25.0),
                ),
        )

    val SPICE =
        StallEntry(
            levelReq = 65,
            xp = 81.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 22,
            loot = listOf(StallLoot("obj.spicespot", min = 1, max = 2)),
        )

    val GEM =
        StallEntry(
            levelReq = 75,
            xp = 160.0,
            emptyLoc = "loc.rag_market_stall",
            respawnTicks = 24,
            loot =
                listOf(
                    StallLoot("obj.uncut_sapphire", weight = 40.0),
                    StallLoot("obj.uncut_emerald", weight = 30.0),
                    StallLoot("obj.uncut_ruby", weight = 20.0),
                    StallLoot("obj.uncut_diamond", weight = 9.0),
                    StallLoot("obj.uncut_dragonstone", weight = 1.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Stall loc → entry lookup map (keyed by RSCM loc name)
    // -----------------------------------------------------------------------
    val locToEntry: Map<String, StallEntry> =
        mapOf(
            // Vegetable stall variants
            "loc.etc_veg_market" to VEGETABLE,
            "loc.aldarin_market_stall_veg" to VEGETABLE,
            // Baker's stall variants
            "loc.cakethiefstall" to BAKERS,
            "loc.cam_torum_market_bakery" to BAKERS,
            "loc.fortis_market_stall_bakers" to BAKERS,
            // Tea stall variants
            "loc.tea_stall" to TEA,
            "loc.icthalarins_tea_stall" to TEA,
            "loc.contact_tea_stall" to TEA,
            // Silk stall variants
            "loc.silkthiefstall" to SILK,
            "loc.icthalarins_silkmarket" to SILK,
            "loc.contact_silkmarket" to SILK,
            "loc.prif_marketstall_silk" to SILK,
            "loc.silkthiefstall_noop" to SILK,
            "loc.fortis_market_stall_silk" to SILK,
            // Seed stall variants
            "loc.seed_stall" to SEED,
            // Fur stall variants
            "loc.furthiefstall" to FUR,
            "loc.viking_fur_market" to FUR,
            "loc.icthalarins_furmarket" to FUR,
            "loc.contact_furmarket" to FUR,
            "loc.vikingexile_fur_market" to FUR,
            "loc.fortis_market_stall_fur" to FUR,
            // Fish stall variants
            "loc.etc_fish_market" to FISH,
            "loc.misc_fish_market" to FISH,
            "loc.fish_stall_warrens" to FISH,
            "loc.vikingexile_fish_market" to FISH,
            // Silver stall variants
            "loc.silverthiefstall" to SILVER,
            "loc.dwarf_market_silver" to SILVER,
            "loc.prif_marketstall_silver" to SILVER,
            "loc.silverthiefstall_noop" to SILVER,
            // Spice stall variants
            "loc.spicethiefstall" to SPICE,
            "loc.icthalarins_spicemarket" to SPICE,
            "loc.prif_marketstall_spice" to SPICE,
            "loc.fortis_market_stall_spice" to SPICE,
            // Gem stall variants
            "loc.gemthiefstall" to GEM,
            "loc.dwarf_market_gems" to GEM,
            "loc.icthalarins_gemmarket" to GEM,
            "loc.contact_gemmarket" to GEM,
            "loc.prif_marketstall_gem" to GEM,
            "loc.gemthiefstall_noop" to GEM,
            "loc.fortis_market_stall_gems" to GEM,
            "loc.aldarin_market_stall_gems" to GEM,
        )
}
