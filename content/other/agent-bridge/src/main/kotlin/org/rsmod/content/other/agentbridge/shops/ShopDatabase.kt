package org.rsmod.content.other.agentbridge.shops

import jakarta.inject.Singleton
import org.rsmod.map.CoordGrid

/** Database of shops in the game world. Used by bots to find shopkeepers and their inventories. */
@Singleton
object ShopDatabase {

    /** Represents a shop location. */
    data class ShopLocation(
        val name: String,
        val shopkeeperName: String,
        val x: Int,
        val z: Int,
        val plane: Int = 0,
        val type: ShopType = ShopType.GENERAL,
        val region: String = "",
    ) {
        val coords: CoordGrid
            get() = CoordGrid(x, z, plane)
    }

    /** Type of shop. */
    enum class ShopType {
        GENERAL, // General store (buys anything)
        WEAPONS, // Weapon shop
        ARMOR, // Armor shop
        FOOD, // Food shop
        RUNES, // Rune shop
        FISHING, // Fishing supplies
        CRAFTING, // Crafting supplies
        MINING, // Mining supplies
        FLETCHING, // Fletching supplies
        HERBLORE, // Herblore supplies
        FARMING, // Farming supplies
        SPECIALTY, // Specialty items only
    }

    /** Shop inventory template. */
    data class ShopInventory(
        val shopName: String,
        val defaultItems: List<ShopItem>,
        val restockTicks: Int = 100, // ~60 seconds
    )

    /** Single item in shop default stock. */
    data class ShopItem(
        val itemId: Int,
        val name: String,
        val defaultStock: Int,
        val basePrice: Int,
    )

    /** All known shop locations. */
    private val shops =
        listOf(
            // Lumbridge
            ShopLocation(
                "Lumbridge General Store",
                "Shopkeeper",
                3210,
                3244,
                0,
                ShopType.GENERAL,
                "Lumbridge",
            ),
            ShopLocation(
                "Lumbridge Fishing Shop",
                "Hank",
                3190,
                3250,
                0,
                ShopType.FISHING,
                "Lumbridge",
            ),

            // Varrock
            ShopLocation(
                "Varrock General Store",
                "Shopkeeper",
                3217,
                3417,
                0,
                ShopType.GENERAL,
                "Varrock",
            ),
            ShopLocation(
                "Varrock Sword Shop",
                "Shopkeeper",
                3204,
                3477,
                0,
                ShopType.WEAPONS,
                "Varrock",
            ),
            ShopLocation(
                "Varrock Armor Shop",
                "Shopkeeper",
                3204,
                3484,
                0,
                ShopType.ARMOR,
                "Varrock",
            ),
            ShopLocation(
                "Varrock Staff Shop",
                "Zaff",
                3203,
                3435,
                0,
                ShopType.SPECIALTY,
                "Varrock",
            ),
            ShopLocation("Varrock Rune Shop", "Aubury", 3253, 3401, 0, ShopType.RUNES, "Varrock"),
            ShopLocation(
                "Varrock Fur Shop",
                "Baraek",
                3289,
                3393,
                0,
                ShopType.SPECIALTY,
                "Varrock",
            ),

            // Falador
            ShopLocation(
                "Falador General Store",
                "Shopkeeper",
                2958,
                3385,
                0,
                ShopType.GENERAL,
                "Falador",
            ),
            ShopLocation("Falador Shield Shop", "Cassie", 2976, 3383, 0, ShopType.ARMOR, "Falador"),
            ShopLocation(
                "Falador Chainmail Shop",
                "Wayne",
                2969,
                3382,
                0,
                ShopType.ARMOR,
                "Falador",
            ),
            ShopLocation("Falador Mace Shop", "Flynn", 2953, 3388, 0, ShopType.WEAPONS, "Falador"),

            // Al Kharid
            ShopLocation(
                "Al Kharid General Store",
                "Shopkeeper",
                3317,
                3182,
                0,
                ShopType.GENERAL,
                "Al Kharid",
            ),
            ShopLocation(
                "Al Kharid Scimitar Shop",
                "Daga",
                3287,
                3190,
                0,
                ShopType.WEAPONS,
                "Al Kharid",
            ),
            ShopLocation(
                "Al Kharid Gem Shop",
                "Gem Trader",
                3288,
                3211,
                0,
                ShopType.SPECIALTY,
                "Al Kharid",
            ),
            ShopLocation(
                "Al Kharid Crafting Shop",
                "Dommik",
                3319,
                3193,
                0,
                ShopType.CRAFTING,
                "Al Kharid",
            ),
            ShopLocation(
                "Ali's Discount Wares",
                "Ali Morrisane",
                3304,
                3211,
                0,
                ShopType.GENERAL,
                "Al Kharid",
            ),

            // Edgeville
            ShopLocation(
                "Edgeville General Store",
                "Shopkeeper",
                3079,
                3510,
                0,
                ShopType.GENERAL,
                "Edgeville",
            ),

            // Draynor
            ShopLocation(
                "Draynor Village Market",
                "Fortunato",
                3084,
                3251,
                0,
                ShopType.SPECIALTY,
                "Draynor",
            ),

            // Rimmington
            ShopLocation(
                "Rimmington General Store",
                "Shopkeeper",
                2947,
                3216,
                0,
                ShopType.GENERAL,
                "Rimmington",
            ),
            ShopLocation(
                "Rimmington Crafting Shop",
                "Rommik",
                2947,
                3206,
                0,
                ShopType.CRAFTING,
                "Rimmington",
            ),

            // Karamja (Musa Point)
            ShopLocation(
                "Karamja General Store",
                "Shopkeeper",
                2891,
                3190,
                0,
                ShopType.GENERAL,
                "Karamja",
            ),
            ShopLocation("Karamja Fish Shop", "Kloves", 2890, 3187, 0, ShopType.FISHING, "Karamja"),

            // Port Sarim
            ShopLocation(
                "Port Sarim General Store",
                "Gerrant",
                3017,
                3223,
                0,
                ShopType.GENERAL,
                "Port Sarim",
            ),
            ShopLocation(
                "Port Sarim Fishing Shop",
                "Gerrant",
                3015,
                3223,
                0,
                ShopType.FISHING,
                "Port Sarim",
            ),
            ShopLocation(
                "Port Sarim Magic Shop",
                "Betty",
                3013,
                3258,
                0,
                ShopType.SPECIALTY,
                "Port Sarim",
            ),
            ShopLocation(
                "Port Sarim Food Shop",
                "Wydin",
                3013,
                3206,
                0,
                ShopType.FOOD,
                "Port Sarim",
            ),

            // Wilderness
            ShopLocation(
                "Wilderness Bandit Camp",
                "Bandit Shopkeeper",
                3027,
                3698,
                0,
                ShopType.GENERAL,
                "Wilderness",
            ),
        )

    /** Default shop inventories. */
    private val inventories =
        mapOf(
            "Lumbridge General Store" to
                ShopInventory(
                    "Lumbridge General Store",
                    listOf(
                        ShopItem(1931, "Pot", 5, 1),
                        ShopItem(1935, "Jug", 2, 1),
                        ShopItem(1735, "Shears", 2, 1),
                        ShopItem(1925, "Bucket", 3, 2),
                        ShopItem(590, "Tinderbox", 2, 1),
                        ShopItem(1755, "Chisel", 2, 1),
                        ShopItem(2347, "Hammer", 5, 1),
                        ShopItem(1933, "Flour", 3, 14),
                    ),
                ),
            "Varrock General Store" to
                ShopInventory(
                    "Varrock General Store",
                    listOf(
                        ShopItem(1931, "Pot", 5, 1),
                        ShopItem(1935, "Jug", 2, 1),
                        ShopItem(1735, "Shears", 2, 1),
                        ShopItem(1925, "Bucket", 3, 2),
                        ShopItem(590, "Tinderbox", 2, 1),
                        ShopItem(1755, "Chisel", 2, 1),
                        ShopItem(2347, "Hammer", 5, 1),
                        ShopItem(882, "Bronze arrow", 30, 7),
                    ),
                ),
        )

    /**
     * Find the nearest shop to the given coordinates.
     *
     * @param x Current X coordinate
     * @param z Current Z coordinate
     * @param plane Current plane/level
     * @param type Optional filter by shop type
     * @return The nearest shop location, or null if none found
     */
    fun findNearest(x: Int, z: Int, plane: Int = 0, type: ShopType? = null): ShopLocation? {
        return shops
            .filter { it.plane == plane }
            .filter { type == null || it.type == type }
            .minByOrNull { shop ->
                val dx = kotlin.math.abs(shop.x - x)
                val dz = kotlin.math.abs(shop.z - z)
                maxOf(dx, dz)
            }
    }

    /**
     * Find shop by name or shopkeeper name.
     *
     * @param name Shop or shopkeeper name (partial match)
     * @return Matching shop, or null if not found
     */
    fun findByName(name: String): ShopLocation? {
        return shops.find {
            it.name.contains(name, ignoreCase = true) ||
                it.shopkeeperName.contains(name, ignoreCase = true)
        }
    }

    /**
     * Get all shops in a region.
     *
     * @param region Region name
     * @return List of shops in that region
     */
    fun findByRegion(region: String): List<ShopLocation> {
        return shops.filter { it.region.equals(region, ignoreCase = true) }
    }

    /**
     * Get shops by type.
     *
     * @param type Shop type
     * @return List of matching shops
     */
    fun findByType(type: ShopType): List<ShopLocation> {
        return shops.filter { it.type == type }
    }

    /** Get all known shops. */
    fun getAll(): List<ShopLocation> = shops.toList()

    /**
     * Get default inventory for a shop.
     *
     * @param shopName Shop name
     * @return Shop inventory, or null if not defined
     */
    fun getInventory(shopName: String): ShopInventory? {
        return inventories[shopName]
    }

    /**
     * Check if coordinates are near a shop.
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @param plane Plane/level
     * @param tolerance Distance tolerance (default 5 tiles)
     * @return True if near a shop
     */
    fun isNearShop(x: Int, z: Int, plane: Int = 0, tolerance: Int = 5): Boolean {
        return shops.any { shop ->
            shop.plane == plane &&
                kotlin.math.abs(shop.x - x) <= tolerance &&
                kotlin.math.abs(shop.z - z) <= tolerance
        }
    }
}
