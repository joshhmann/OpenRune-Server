package org.rsmod.content.other.agentbridge.grounditems

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.hunt.Hunt
import org.rsmod.content.other.agentbridge.AgentBridgeServer
import org.rsmod.content.other.agentbridge.BotAction
import org.rsmod.content.other.agentbridge.GroundItemScan
import org.rsmod.content.other.agentbridge.GroundItemSnapshot
import org.rsmod.content.other.agentbridge.pathfinding.PathfindingService
import org.rsmod.content.other.agentbridge.porcelain.BotPorcelain
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj

/**
 * High-level ground item porcelain actions for bot automation. Provides on-demand scanning and
 * pickup with automatic navigation.
 */
@Singleton
class GroundItemPorcelain
@Inject
constructor(
    private val server: AgentBridgeServer,
    private val pathfinding: PathfindingService,
    private val hunt: Hunt,
) {
    private val logger = InlineLogger()

    companion object {
        private const val DEFAULT_SCAN_RADIUS = 10
        private const val MAX_SCAN_RADIUS = 30
        private const val DEFAULT_TIMEOUT_MS = 15000
    }

    /** Cache for recent scans to avoid repeated scanning. */
    private val scanCache = mutableMapOf<String, GroundItemScan>()
    private val CACHE_DURATION_TICKS = 5 // Cache for 5 ticks (~3 seconds)

    /**
     * Scans for ground items in radius.
     *
     * @param player The player to perform the action
     * @param radius Scan radius (max 30)
     * @param pattern Optional name pattern filter
     * @return Result with found items
     */
    fun scanGroundItems(
        player: Player,
        radius: Int = DEFAULT_SCAN_RADIUS,
        pattern: String? = null,
    ): BotPorcelain.PorcelainResult {
        val effectiveRadius = radius.coerceIn(1, MAX_SCAN_RADIUS)
        val playerKey = player.avatar.name.lowercase()
        val currentTick = server.getCurrentTick()

        // Check cache
        val cached = scanCache[playerKey]
        if (cached != null && (currentTick - cached.timestamp) < CACHE_DURATION_TICKS) {
            // Use cached results, filtered by pattern if needed
            val filtered =
                if (pattern != null) {
                    cached.items.filter { it.name.contains(pattern, ignoreCase = true) }
                } else cached.items

            return BotPorcelain.PorcelainResult(
                success = true,
                message = "Found ${filtered.size} ground items (cached)",
                actionResults = listOf("Scan radius: $effectiveRadius"),
            )
        }

        // Perform scan
        val items = performScan(player, effectiveRadius, pattern)

        // Cache results
        scanCache[playerKey] =
            GroundItemScan(
                items = items,
                scanX = player.coords.x,
                scanZ = player.coords.z,
                radius = effectiveRadius,
                timestamp = currentTick,
            )

        return BotPorcelain.PorcelainResult(
            success = items.isNotEmpty(),
            message = "Found ${items.size} ground items within $effectiveRadius tiles",
            actionResults = listOf("Scan radius: $effectiveRadius"),
        )
    }

    /**
     * Finds a ground item by name pattern.
     *
     * @param player The player
     * @param pattern Name pattern to search for
     * @param radius Search radius
     * @return Result with nearest matching item
     */
    fun findGroundItem(
        player: Player,
        pattern: String,
        radius: Int = DEFAULT_SCAN_RADIUS,
    ): BotPorcelain.PorcelainResult {
        val items = performScan(player, radius, pattern)

        if (items.isEmpty()) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "No ground items matching '$pattern' found within $radius tiles",
            )
        }

        val nearest = items.minByOrNull { it.distance } ?: items.first()

        return BotPorcelain.PorcelainResult(
            success = true,
            message =
                "Found '${nearest.name}' (${nearest.count}x) at (${nearest.x}, ${nearest.z}), distance: ${nearest.distance}",
            actionResults = listOf("Item ID: ${nearest.id}", "Count: ${nearest.count}"),
        )
    }

    /**
     * Picks up a specific ground item by coordinates.
     *
     * @param player The player
     * @param x Item X coordinate
     * @param z Item Z coordinate
     * @param itemId Item type ID
     * @return Result of the operation
     */
    fun pickupGroundItem(
        player: Player,
        x: Int,
        z: Int,
        itemId: Int,
    ): BotPorcelain.PorcelainResult {
        val results = mutableListOf<String>()

        // Verify item exists at location
        val item = findItemAt(player, x, z, itemId)
        if (item == null) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "Item not found at ($x, $z)",
                actionResults = results,
            )
        }

        val typeName = ServerCacheManager.getItem(item.entity.id)?.name ?: "Unknown"
        results.add("Found item: $typeName at ($x, $z)")

        // Walk to item if not at location
        if (player.coords.x != x || player.coords.z != z) {
            queueAction(player, BotAction.WalkWithDoors(x, z, player.coords.level, tolerance = 0))
            results.add("Walking to item")
        }

        // Pick up item
        queueAction(player, BotAction.PickupGroundItem(x, z, itemId))
        results.add("Picking up $typeName")

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Picking up $typeName",
            actionResults = results,
        )
    }

    /**
     * Picks up the nearest item matching a pattern.
     *
     * @param player The player
     * @param pattern Item name pattern
     * @param maxDistance Maximum distance to search
     * @return Result of the operation
     */
    fun pickupNearest(
        player: Player,
        pattern: String,
        maxDistance: Int = DEFAULT_SCAN_RADIUS,
    ): BotPorcelain.PorcelainResult {
        val items = performScan(player, maxDistance, pattern)

        if (items.isEmpty()) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "No items matching '$pattern' found within $maxDistance tiles",
            )
        }

        val nearest = items.minByOrNull { it.distance } ?: items.first()

        return pickupGroundItem(player, nearest.x, nearest.z, nearest.id)
    }

    /**
     * Loots all items in an area matching patterns.
     *
     * @param player The player
     * @param radius Loot radius
     * @param patterns Item name patterns to loot (empty = all)
     * @return Result of the operation
     */
    fun lootArea(
        player: Player,
        radius: Int = 5,
        patterns: List<String> = emptyList(),
    ): BotPorcelain.PorcelainResult {
        val items = performScan(player, radius, null)

        val toLoot =
            if (patterns.isEmpty()) {
                items
            } else {
                items.filter { item ->
                    patterns.any { pattern -> item.name.contains(pattern, ignoreCase = true) }
                }
            }

        if (toLoot.isEmpty()) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "No items to loot in $radius tile radius",
            )
        }

        // Queue pickup actions for each item (closest first)
        val sorted = toLoot.sortedBy { it.distance }
        for (item in sorted) {
            queueAction(player, BotAction.PickupGroundItem(item.x, item.z, item.id))
        }

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Looting ${sorted.size} items within $radius tiles",
            actionResults = sorted.map { "${it.name} at (${it.x}, ${it.z})" },
        )
    }

    /**
     * Waits for a ground item to appear.
     *
     * @param player The player
     * @param pattern Item name pattern to wait for
     * @param timeoutMs Timeout in milliseconds
     * @return Result of the operation
     */
    fun waitForGroundItem(
        player: Player,
        pattern: String,
        timeoutMs: Int = 30000,
    ): BotPorcelain.PorcelainResult {
        queueAction(player, BotAction.WaitForGroundItem(pattern, timeoutMs))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Waiting for ground item matching '$pattern' (timeout: ${timeoutMs}ms)",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------------------------

    /** Performs the actual ground item scan using Hunt API. */
    private fun performScan(
        player: Player,
        radius: Int,
        pattern: String?,
    ): List<GroundItemSnapshot> {
        return hunt
            .findObjs(player.coords, radius, HuntVis.Off)
            .filter { obj -> obj.isVisibleTo(player) }
            .mapNotNull { obj ->
                val type = ServerCacheManager.getItem(obj.entity.id) ?: return@mapNotNull null
                if (type.name.isBlank() || type.name == "null") return@mapNotNull null

                // Apply pattern filter if provided
                if (pattern != null && !type.name.contains(pattern, ignoreCase = true)) {
                    return@mapNotNull null
                }

                val dx = kotlin.math.abs(obj.coords.x - player.coords.x)
                val dz = kotlin.math.abs(obj.coords.z - player.coords.z)
                val dist = maxOf(dx, dz)

                GroundItemSnapshot(
                    id = obj.entity.id,
                    name = type.name,
                    count = obj.count,
                    x = obj.coords.x,
                    z = obj.coords.z,
                    distance = dist,
                    visible = true,
                )
            }
            .sortedBy { it.distance }
            .toList()
    }

    /** Finds a specific item at coordinates. */
    private fun findItemAt(player: Player, x: Int, z: Int, itemId: Int): Obj? {
        return hunt.findObjs(player.coords, 1, HuntVis.Off).find { obj ->
            obj.coords.x == x &&
                obj.coords.z == z &&
                obj.entity.id == itemId &&
                obj.isVisibleTo(player)
        }
    }

    /** Queues an action via reflection. */
    private fun queueAction(player: Player, action: BotAction) {
        try {
            val playerKey = player.avatar.name.lowercase()
            val pendingActionsField = server.javaClass.getDeclaredField("pendingActions")
            pendingActionsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val pendingActions =
                pendingActionsField.get(server)
                    as
                    java.util.concurrent.ConcurrentHashMap<
                        String,
                        java.util.concurrent.ConcurrentLinkedQueue<BotAction>,
                    >
            val queue =
                pendingActions.getOrPut(playerKey) { java.util.concurrent.ConcurrentLinkedQueue() }
            queue.offer(action)
        } catch (e: Exception) {
            logger.error { "[GroundItemPorcelain] Failed to queue action: ${e.message}" }
        }
    }
}
