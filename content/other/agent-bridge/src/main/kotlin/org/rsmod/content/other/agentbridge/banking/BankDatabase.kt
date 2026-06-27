package org.rsmod.content.other.agentbridge.banking

import jakarta.inject.Singleton
import org.rsmod.map.CoordGrid

/**
 * Database of bank locations in the game world. Used by bots to find and navigate to the nearest
 * bank.
 */
@Singleton
object BankDatabase {

    /** Represents a bank location. */
    data class BankLocation(
        val name: String,
        val x: Int,
        val z: Int,
        val plane: Int = 0,
        val type: BankType = BankType.BOOTH,
        val region: String = "",
    ) {
        val coords: CoordGrid
            get() = CoordGrid(x, z, plane)
    }

    /** Type of bank access point. */
    enum class BankType {
        BOOTH, // Standard bank booth
        CHEST, // Bank chest
        NPC, // Banker NPC
        DEPOSIT_BOX, // Deposit box only
    }

    /** All known bank locations in the F2P world. */
    private val banks =
        listOf(
            // Lumbridge
            BankLocation("Lumbridge Castle Bank", 3209, 3220, 2, BankType.NPC, "Lumbridge"),

            // Varrock
            BankLocation("Varrock West Bank", 3185, 3443, 0, BankType.BOOTH, "Varrock"),
            BankLocation("Varrock East Bank", 3253, 3422, 0, BankType.BOOTH, "Varrock"),
            BankLocation("Varrock South Bank", 3255, 3480, 0, BankType.BOOTH, "Varrock"),
            BankLocation("Varrock Grand Exchange", 3164, 3487, 0, BankType.NPC, "Varrock"),

            // Falador
            BankLocation("Falador West Bank", 2946, 3368, 0, BankType.BOOTH, "Falador"),
            BankLocation("Falador East Bank", 3013, 3355, 0, BankType.BOOTH, "Falador"),

            // Draynor
            BankLocation("Draynor Bank", 3093, 3244, 0, BankType.NPC, "Draynor"),

            // Edgeville
            BankLocation("Edgeville Bank", 3094, 3491, 0, BankType.BOOTH, "Edgeville"),

            // Al Kharid
            BankLocation("Al Kharid Bank", 3269, 3167, 0, BankType.NPC, "Al Kharid"),

            // Port Sarim
            BankLocation(
                "Port Sarim Deposit Box",
                3045,
                3235,
                0,
                BankType.DEPOSIT_BOX,
                "Port Sarim",
            ),

            // Wilderness
            BankLocation("Wilderness Volcano Bank", 3028, 3597, 0, BankType.CHEST, "Wilderness"),
        )

    /**
     * Find the nearest bank to the given coordinates.
     *
     * @param x Current X coordinate
     * @param z Current Z coordinate
     * @param plane Current plane/level
     * @param allowDepositBox Whether to include deposit-only boxes
     * @return The nearest bank location, or null if none found
     */
    fun findNearest(
        x: Int,
        z: Int,
        plane: Int = 0,
        allowDepositBox: Boolean = false,
    ): BankLocation? {
        return banks
            .filter { it.plane == plane }
            .filter { allowDepositBox || it.type != BankType.DEPOSIT_BOX }
            .minByOrNull { bank ->
                val dx = kotlin.math.abs(bank.x - x)
                val dz = kotlin.math.abs(bank.z - z)
                maxOf(dx, dz)
            }
    }

    /**
     * Find all banks within a certain distance.
     *
     * @param x Current X coordinate
     * @param z Current Z coordinate
     * @param plane Current plane/level
     * @param maxDistance Maximum Chebyshev distance
     * @return List of banks within range, sorted by distance
     */
    fun findNearby(x: Int, z: Int, plane: Int = 0, maxDistance: Int = 50): List<BankLocation> {
        return banks
            .filter { it.plane == plane }
            .filter { bank ->
                val dx = kotlin.math.abs(bank.x - x)
                val dz = kotlin.math.abs(bank.z - z)
                maxOf(dx, dz) <= maxDistance
            }
            .sortedBy { bank ->
                val dx = kotlin.math.abs(bank.x - x)
                val dz = kotlin.math.abs(bank.z - z)
                maxOf(dx, dz)
            }
    }

    /**
     * Get bank by name (partial match).
     *
     * @param name Name or partial name of the bank
     * @return The matching bank, or null if not found
     */
    fun findByName(name: String): BankLocation? {
        return banks.find { it.name.contains(name, ignoreCase = true) }
    }

    /**
     * Get all banks in a specific region.
     *
     * @param region Region name (e.g., "Lumbridge", "Varrock")
     * @return List of banks in that region
     */
    fun findByRegion(region: String): List<BankLocation> {
        return banks.filter { it.region.equals(region, ignoreCase = true) }
    }

    /** Get all known bank locations. */
    fun getAll(): List<BankLocation> = banks.toList()

    /**
     * Check if coordinates are at or near a bank.
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @param plane Plane/level
     * @param tolerance Distance tolerance (default 2 tiles)
     * @return True if near a bank
     */
    fun isNearBank(x: Int, z: Int, plane: Int = 0, tolerance: Int = 2): Boolean {
        return banks.any { bank ->
            bank.plane == plane &&
                kotlin.math.abs(bank.x - x) <= tolerance &&
                kotlin.math.abs(bank.z - z) <= tolerance
        }
    }
}
