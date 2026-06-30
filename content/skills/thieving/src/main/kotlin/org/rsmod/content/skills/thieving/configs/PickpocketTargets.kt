package org.rsmod.content.skills.thieving.configs

/**
 * Pickpocket target data definitions ported from rsmod-233 source.
 *
 * Each entry defines an NPC that can be pickpocketed: the level requirement,
 * XP reward, success formula parameters, stun/damage on failure, and weighted
 * loot table.
 *
 * Source: `osrs-ps-dev-source/rsmod/content/skills/thieving/scripts/Thieving.kt`
 * Data sourced from Alter pickpockets.json + OSRS wiki.
 */
public data class PickpocketLoot(
    val obj: String,
    val min: Int = 1,
    val max: Int = min,
    val weight: Double = 1.0,
)

public data class PickpocketEntry(
    val levelReq: Int,
    val xp: Double,
    val baseSuccess: Double,
    val bonusPerLevel: Double,
    val stunTicks: Int,
    val stunDamageMin: Int,
    val stunDamageMax: Int,
    val loot: List<PickpocketLoot>,
)

/**
 * All pickpocket targets indexed by the NPC's RSCM name.
 *
 * This map is keyed by the NPC cache identifiers used in `onOpNpc2` handlers
 * (e.g., "npc.man", "npc.farmer1"). Multiple NPC variants (man1/man2/man3)
 * may share the same [PickpocketEntry] via separate map entries.
 */
@Suppress("MemberVisibilityCanBePrivate")
public object PickpocketTargets {

    // -----------------------------------------------------------------------
    //  Man / Woman (level 1)
    // -----------------------------------------------------------------------
    val MAN_WOMAN =
        PickpocketEntry(
            levelReq = 1,
            xp = 8.0,
            baseSuccess = 0.55,
            bonusPerLevel = 0.015,
            stunTicks = 7,
            stunDamageMin = 1,
            stunDamageMax = 1,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 3, 12, 75.0),
                    PickpocketLoot("obj.bolts", 1, 2, 25.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Farmer (level 10)
    // -----------------------------------------------------------------------
    val FARMER =
        PickpocketEntry(
            levelReq = 10,
            xp = 14.5,
            baseSuccess = 0.52,
            bonusPerLevel = 0.012,
            stunTicks = 7,
            stunDamageMin = 1,
            stunDamageMax = 2,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 9, 30, 75.0),
                    PickpocketLoot("obj.potato_seed", 1, 1, 25.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  H.A.M. Member (level 15)
    // -----------------------------------------------------------------------
    val HAM_MEMBER =
        PickpocketEntry(
            levelReq = 15,
            xp = 18.5,
            baseSuccess = 0.48,
            bonusPerLevel = 0.01,
            stunTicks = 6,
            stunDamageMin = 1,
            stunDamageMax = 3,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 20, 40, 60.0),
                    PickpocketLoot("obj.digsitebuttons", 1, 1, 20.0),
                    PickpocketLoot("obj.bronze_dagger", 1, 1, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  H.A.M. Guard (level 20)
    // -----------------------------------------------------------------------
    val HAM_GUARD =
        PickpocketEntry(
            levelReq = 20,
            xp = 22.5,
            baseSuccess = 0.46,
            bonusPerLevel = 0.009,
            stunTicks = 6,
            stunDamageMin = 1,
            stunDamageMax = 3,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 25, 45, 60.0),
                    PickpocketLoot("obj.iron_knife", 1, 1, 20.0),
                    PickpocketLoot("obj.leather_gloves", 1, 1, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Al-Kharid Warrior (level 25)
    // -----------------------------------------------------------------------
    val AL_KHARID_WARRIOR =
        PickpocketEntry(
            levelReq = 25,
            xp = 26.0,
            baseSuccess = 0.45,
            bonusPerLevel = 0.009,
            stunTicks = 7,
            stunDamageMin = 1,
            stunDamageMax = 2,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 18, 35, 80.0),
                    PickpocketLoot("obj.bronze_dagger", 1, 1, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Rogue (level 32)
    // -----------------------------------------------------------------------
    val ROGUE =
        PickpocketEntry(
            levelReq = 32,
            xp = 35.5,
            baseSuccess = 0.42,
            bonusPerLevel = 0.009,
            stunTicks = 7,
            stunDamageMin = 1,
            stunDamageMax = 2,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 25, 50, 75.0),
                    PickpocketLoot("obj.lockpick", 1, 1, 25.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Cave Goblin (level 36)
    // -----------------------------------------------------------------------
    val CAVE_GOBLIN =
        PickpocketEntry(
            levelReq = 36,
            xp = 40.0,
            baseSuccess = 0.43,
            bonusPerLevel = 0.008,
            stunTicks = 7,
            stunDamageMin = 1,
            stunDamageMax = 1,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 12, 48, 75.0),
                    PickpocketLoot("obj.iron_ore", 1, 2, 25.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Master Farmer (level 38)
    // -----------------------------------------------------------------------
    val MASTER_FARMER =
        PickpocketEntry(
            levelReq = 38,
            xp = 43.0,
            baseSuccess = 0.35,
            bonusPerLevel = 0.008,
            stunTicks = 8,
            stunDamageMin = 1,
            stunDamageMax = 3,
            loot =
                listOf(
                    PickpocketLoot("obj.potato_seed", 1, 1, 50.0),
                    PickpocketLoot("obj.strawberry_seed", 1, 1, 25.0),
                    PickpocketLoot("obj.ranarr_seed", 1, 1, 25.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Guard (level 40)
    // -----------------------------------------------------------------------
    val GUARD =
        PickpocketEntry(
            levelReq = 40,
            xp = 46.8,
            baseSuccess = 0.38,
            bonusPerLevel = 0.008,
            stunTicks = 8,
            stunDamageMin = 1,
            stunDamageMax = 2,
            loot = listOf(PickpocketLoot("obj.coins", 30, 60, 100.0)),
        )

    // -----------------------------------------------------------------------
    //  Knight of Ardougne (level 55)
    // -----------------------------------------------------------------------
    val KNIGHT_OF_ARDOUGNE =
        PickpocketEntry(
            levelReq = 55,
            xp = 84.3,
            baseSuccess = 0.34,
            bonusPerLevel = 0.007,
            stunTicks = 9,
            stunDamageMin = 2,
            stunDamageMax = 4,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 50, 90, 80.0),
                    PickpocketLoot("obj.lawrune", 1, 2, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Menaphite Thug (level 65)
    // -----------------------------------------------------------------------
    val MENAPHITE_THUG =
        PickpocketEntry(
            levelReq = 65,
            xp = 137.5,
            baseSuccess = 0.32,
            bonusPerLevel = 0.007,
            stunTicks = 8,
            stunDamageMin = 2,
            stunDamageMax = 5,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 40, 80, 80.0),
                    PickpocketLoot("obj.earthrune", 4, 8, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Paladin (level 70)
    // -----------------------------------------------------------------------
    val PALADIN =
        PickpocketEntry(
            levelReq = 70,
            xp = 151.75,
            baseSuccess = 0.30,
            bonusPerLevel = 0.006,
            stunTicks = 9,
            stunDamageMin = 2,
            stunDamageMax = 3,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 80, 160, 80.0),
                    PickpocketLoot("obj.chaosrune", 1, 2, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  Hero (level 80)
    // -----------------------------------------------------------------------
    val HERO =
        PickpocketEntry(
            levelReq = 80,
            xp = 275.0,
            baseSuccess = 0.26,
            bonusPerLevel = 0.0055,
            stunTicks = 10,
            stunDamageMin = 3,
            stunDamageMax = 4,
            loot =
                listOf(
                    PickpocketLoot("obj.coins", 200, 300, 60.0),
                    PickpocketLoot("obj.bloodrune", 2, 3, 20.0),
                    PickpocketLoot("obj.ruby", 1, 1, 20.0),
                ),
        )

    // -----------------------------------------------------------------------
    //  NPC → entry lookup map (keyed by RSCM NPC name)
    // -----------------------------------------------------------------------
    val npcToEntry: Map<String, PickpocketEntry> =
        mapOf(
            "npc.man" to MAN_WOMAN,
            "npc.man2" to MAN_WOMAN,
            "npc.man3" to MAN_WOMAN,
            "npc.man_indoor" to MAN_WOMAN,
            "npc.woman" to MAN_WOMAN,
            "npc.woman2" to MAN_WOMAN,
            "npc.woman3" to MAN_WOMAN,
            // Farmers
            "npc.farmer1" to FARMER,
            "npc.farmer1_f" to FARMER,
            "npc.farmer2" to FARMER,
            "npc.farmer2_f" to FARMER,
            "npc.farmer3" to FARMER,
            "npc.farmer3_f" to FARMER,
            "npc.farmer4" to FARMER,
            // H.A.M.
            "npc.ham_member" to HAM_MEMBER,
            "npc.ham_guard" to HAM_GUARD,
            // Al-Kharid Warrior
            "npc.al_kharid_warrior" to AL_KHARID_WARRIOR,
            // Rogues
            "npc.rogue" to ROGUE,
            // Cave goblins (Dorgeshuun, NOT surface goblins)
            "npc.cave_goblin" to CAVE_GOBLIN,
            // Master Farmer
            "npc.master_farmer" to MASTER_FARMER,
            // City guard
            "npc.city_guard" to GUARD,
            "npc.guard" to GUARD,
            // Ardougne
            "npc.knight_of_ardougne" to KNIGHT_OF_ARDOUGNE,
            // Paladin
            "npc.paladin" to PALADIN,
            // Hero
            "npc.hero" to HERO,
        )
}
