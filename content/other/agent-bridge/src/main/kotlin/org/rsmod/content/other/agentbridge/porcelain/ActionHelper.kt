package org.rsmod.content.other.agentbridge.porcelain

import dev.openrune.ServerCacheManager
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.route.RouteFactory
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

/**
 * Shared utility class for porcelain actions in AgentBridge.
 *
 * Provides common patterns for:
 * - Target resolution (finding locs, NPCs, ground items by name pattern)
 * - Inventory utilities (checking items, finding slots, counting)
 * - Distance and collision checks
 * - Name matching helpers
 */
@Singleton
class ActionHelper
@Inject
constructor(
    private val hunt: Hunt,
    private val locRegistry: LocRegistry,
    private val collisionFlags: CollisionFlagMap,
    private val routeFactory: RouteFactory,
) {
    // ===== TARGET RESOLUTION =====

    /**
     * Find location by name pattern (case-insensitive, partial match).
     *
     * @param player The player to search around
     * @param pattern The name pattern to match
     * @param radius Search radius in tiles (default 16)
     * @return The closest matching location, or null if none found
     */
    fun findLoc(player: Player, pattern: String, radius: Int = DEFAULT_SCAN_RADIUS): BoundLocInfo? {
        return hunt
            .findLocs(player.coords, radius, HuntVis.Off)
            .mapNotNull { locInfo ->
                val type = ServerCacheManager.getObject(locInfo.id) ?: return@mapNotNull null
                if (type.name.isBlank() || type.name == "null") return@mapNotNull null
                if (!matches(type.name, pattern)) return@mapNotNull null
                BoundLocInfo(locInfo, type)
            }
            .minByOrNull { boundLoc -> chebyshevDistance(player.coords, boundLoc.coords) }
    }

    /**
     * Find location by regex pattern.
     *
     * @param player The player to search around
     * @param pattern The regex pattern to match
     * @param radius Search radius in tiles (default 16)
     * @return The closest matching location, or null if none found
     */
    fun findLoc(player: Player, pattern: Regex, radius: Int = DEFAULT_SCAN_RADIUS): BoundLocInfo? {
        return hunt
            .findLocs(player.coords, radius, HuntVis.Off)
            .mapNotNull { locInfo ->
                val type = ServerCacheManager.getObject(locInfo.id) ?: return@mapNotNull null
                if (type.name.isBlank() || type.name == "null") return@mapNotNull null
                if (!pattern.containsMatchIn(type.name)) return@mapNotNull null
                BoundLocInfo(locInfo, type)
            }
            .minByOrNull { boundLoc -> chebyshevDistance(player.coords, boundLoc.coords) }
    }

    /**
     * Find NPC by name pattern (case-insensitive, partial match).
     *
     * @param player The player to search around
     * @param pattern The name pattern to match
     * @param radius Search radius in tiles (default 16)
     * @return The closest matching NPC, or null if none found
     */
    fun findNpc(player: Player, pattern: String, radius: Int = DEFAULT_SCAN_RADIUS): Npc? {
        return hunt
            .findNpcs(player.coords, radius, HuntVis.Off)
            .filter { npc ->
                npc.type.name.isNotBlank() &&
                    npc.type.name != "null" &&
                    matches(npc.type.name, pattern)
            }
            .minByOrNull { npc -> chebyshevDistance(player.coords, npc.coords) }
    }

    /**
     * Find NPC by regex pattern.
     *
     * @param player The player to search around
     * @param pattern The regex pattern to match
     * @param radius Search radius in tiles (default 16)
     * @return The closest matching NPC, or null if none found
     */
    fun findNpc(player: Player, pattern: Regex, radius: Int = DEFAULT_SCAN_RADIUS): Npc? {
        return hunt
            .findNpcs(player.coords, radius, HuntVis.Off)
            .filter { npc ->
                npc.type.name.isNotBlank() &&
                    npc.type.name != "null" &&
                    pattern.containsMatchIn(npc.type.name)
            }
            .minByOrNull { npc -> chebyshevDistance(player.coords, npc.coords) }
    }

    /**
     * Find ground item by name pattern (case-insensitive, partial match).
     *
     * @param player The player to search around
     * @param pattern The name pattern to match
     * @param radius Search radius in tiles (default 16)
     * @return The closest matching ground item, or null if none found
     */
    fun findGroundItem(player: Player, pattern: String, radius: Int = DEFAULT_SCAN_RADIUS): Obj? {
        return hunt
            .findObjs(player.coords, radius, HuntVis.Off)
            .filter { obj ->
                val type = ServerCacheManager.getItem(obj.entity.id)
                type != null &&
                    type.name.isNotBlank() &&
                    type.name != "null" &&
                    matches(type.name, pattern)
            }
            .minByOrNull { obj -> chebyshevDistance(player.coords, obj.coords) }
    }

    /**
     * Find ground item by exact name match.
     *
     * @param player The player to search around
     * @param name The exact item name to find
     * @param radius Search radius in tiles (default 16)
     * @return The closest matching ground item, or null if none found
     */
    fun findGroundItemExact(player: Player, name: String, radius: Int = DEFAULT_SCAN_RADIUS): Obj? {
        return hunt
            .findObjs(player.coords, radius, HuntVis.Off)
            .filter { obj ->
                val type = ServerCacheManager.getItem(obj.entity.id)
                type != null && type.name.equals(name, ignoreCase = true)
            }
            .minByOrNull { obj -> chebyshevDistance(player.coords, obj.coords) }
    }

    // ===== INVENTORY UTILITIES =====

    /**
     * Check if player has an item matching the name pattern.
     *
     * @param player The player to check
     * @param pattern The name pattern to match
     * @return True if at least one item matches
     */
    fun hasItem(player: Player, pattern: String): Boolean {
        return findItemSlot(player, pattern) != null
    }

    /**
     * Check if player has an item with exact name.
     *
     * @param player The player to check
     * @param name The exact item name
     * @return True if at least one item matches
     */
    fun hasItemExact(player: Player, name: String): Boolean {
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            val type = ServerCacheManager.getItem(obj.id) ?: continue
            if (type.name.equals(name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Find the first inventory slot containing an item matching the pattern.
     *
     * @param player The player to check
     * @param pattern The name pattern to match
     * @return The slot index, or null if not found
     */
    fun findItemSlot(player: Player, pattern: String): Int? {
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            val type = ServerCacheManager.getItem(obj.id) ?: continue
            if (matches(type.name, pattern)) {
                return slot
            }
        }
        return null
    }

    /**
     * Find all inventory slots containing items matching the pattern.
     *
     * @param player The player to check
     * @param pattern The name pattern to match
     * @return List of slot indices that match
     */
    fun findAllItemSlots(player: Player, pattern: String): List<Int> {
        return player.inv.indices.filter { slot ->
            val obj = player.inv[slot]
            if (obj == null) return@filter false
            val type = ServerCacheManager.getItem(obj.id)
            type != null && matches(type.name, pattern)
        }
    }

    /**
     * Count total quantity of items matching the pattern.
     *
     * @param player The player to check
     * @param pattern The name pattern to match
     * @return Total count of matching items
     */
    fun countItems(player: Player, pattern: String): Int {
        var count = 0
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            val type = ServerCacheManager.getItem(obj.id) ?: continue
            if (matches(type.name, pattern)) {
                count += obj.count
            }
        }
        return count
    }

    /**
     * Get the count of a specific item by exact name.
     *
     * @param player The player to check
     * @param name The exact item name
     * @return Total count of the item
     */
    fun countItemsExact(player: Player, name: String): Int {
        var count = 0
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            val type = ServerCacheManager.getItem(obj.id) ?: continue
            if (type.name.equals(name, ignoreCase = true)) {
                count += obj.count
            }
        }
        return count
    }

    /**
     * Get the best item from a list of candidates based on level requirement.
     *
     * @param skillLevel The player's current level in the relevant skill
     * @param candidates List of candidate items
     * @param levelExtractor Function to extract the required level from a candidate
     * @return The best candidate the player can use, or null if none
     */
    fun <T> getBestByLevel(skillLevel: Int, candidates: List<T>, levelExtractor: (T) -> Int): T? {
        return candidates
            .filter { levelExtractor(it) <= skillLevel }
            .maxByOrNull { levelExtractor(it) }
    }

    // ===== DISTANCE/COLLISION =====

    /**
     * Check if a tile is reachable (not blocked by collision).
     *
     * @param player The player (for current plane)
     * @param x Target X coordinate
     * @param z Target Z coordinate
     * @return True if the tile can be walked on
     */
    fun isReachable(player: Player, x: Int, z: Int): Boolean {
        return isReachable(player.coords.level, x, z)
    }

    /**
     * Check if a tile is reachable (not blocked by collision).
     *
     * @param level The plane/level
     * @param x Target X coordinate
     * @param z Target Z coordinate
     * @return True if the tile can be walked on
     */
    fun isReachable(level: Int, x: Int, z: Int): Boolean {
        val flags = collisionFlags[level, x, z]
        return (flags and COLLISION_BLOCKED) == 0
    }

    /**
     * Check if there's line of walk between player and target.
     *
     * @param player The player to check from
     * @param toX Target X coordinate
     * @param toZ Target Z coordinate
     * @return True if there's a clear line of walk
     */
    fun hasLineOfWalk(player: Player, toX: Int, toZ: Int): Boolean {
        val route =
            routeFactory.create(
                source = player.avatar,
                destination = CoordGrid(toX, toZ, player.coords.level),
                collision = CollisionStrategy.Normal,
            )
        return route.isNotEmpty() && route.last().x == toX && route.last().z == toZ
    }

    /**
     * Get Chebyshev distance (walking distance) between player and target.
     *
     * @param player The player
     * @param x Target X coordinate
     * @param z Target Z coordinate
     * @return Distance in tiles
     */
    fun getWalkDistance(player: Player, x: Int, z: Int): Int {
        return chebyshevDistance(player.coords.x, player.coords.z, x, z)
    }

    /**
     * Get Chebyshev distance between two coordinates.
     *
     * @param x1 First X coordinate
     * @param z1 First Z coordinate
     * @param x2 Second X coordinate
     * @param z2 Second Z coordinate
     * @return Distance in tiles
     */
    fun getWalkDistance(x1: Int, z1: Int, x2: Int, z2: Int): Int {
        return chebyshevDistance(x1, z1, x2, z2)
    }

    // ===== NAME MATCHING =====

    /**
     * Case-insensitive partial name match.
     *
     * @param name The name to check
     * @param pattern The pattern to match against
     * @return True if name contains pattern (case-insensitive)
     */
    fun matches(name: String, pattern: String): Boolean {
        return name.contains(pattern, ignoreCase = true)
    }

    /**
     * Match against multiple patterns (any match returns true).
     *
     * @param name The name to check
     * @param patterns The patterns to match against
     * @return True if name contains any pattern (case-insensitive)
     */
    fun matchesAny(name: String, patterns: List<String>): Boolean {
        return patterns.any { pattern -> matches(name, pattern) }
    }

    /**
     * Match against multiple patterns (all must match).
     *
     * @param name The name to check
     * @param patterns The patterns to match against
     * @return True if name contains all patterns (case-insensitive)
     */
    fun matchesAll(name: String, patterns: List<String>): Boolean {
        return patterns.all { pattern -> matches(name, pattern) }
    }

    /**
     * Extract the numeric level requirement from an item name. Useful for tools like "Bronze axe
     * (level 1)", "Rune axe (level 41)", etc.
     *
     * @param name The item name
     * @return The extracted level, or null if not found
     */
    fun extractLevelRequirement(name: String): Int? {
        val regex = "\\(level\\s+(\\d+)\\)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    // ===== HELPER METHODS =====

    private fun chebyshevDistance(a: CoordGrid, b: CoordGrid): Int {
        return chebyshevDistance(a.x, a.z, b.x, b.z)
    }

    private fun chebyshevDistance(x1: Int, z1: Int, x2: Int, z2: Int): Int {
        val dx = max(x1, x2) - minOf(x1, x2)
        val dz = max(z1, z2) - minOf(z1, z2)
        return max(dx, dz)
    }

    companion object {
        /** Default scan radius for nearby entity searches. */
        private const val DEFAULT_SCAN_RADIUS = 16

        /** Collision flag indicating a blocked tile. */
        private const val COLLISION_BLOCKED = 0x1
    }
}
