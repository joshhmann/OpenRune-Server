package org.rsmod.content.other.agentbridge.shops

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.other.agentbridge.AgentBridgeServer
import org.rsmod.content.other.agentbridge.BotAction
import org.rsmod.content.other.agentbridge.pathfinding.PathfindingService
import org.rsmod.content.other.agentbridge.porcelain.BotPorcelain
import org.rsmod.game.entity.Player

/**
 * High-level shop porcelain actions for bot automation. Provides convenient methods for buying and
 * selling with automatic navigation.
 */
@Singleton
class ShopPorcelain
@Inject
constructor(private val server: AgentBridgeServer, private val pathfinding: PathfindingService) {
    private val logger = InlineLogger()

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15000
        private const val SCAN_RADIUS = 16
    }

    /**
     * Opens a shop. Finds the shopkeeper, walks to them, and opens trade.
     *
     * @param player The player to perform the action
     * @param shopkeeperName Optional specific shopkeeper name
     * @return Result of the operation
     */
    fun openShop(player: Player, shopkeeperName: String? = null): BotPorcelain.PorcelainResult {
        val results = mutableListOf<String>()

        // Step 1: Check if shop is already open
        if (isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(
                success = true,
                message = "Shop is already open",
                actionResults = results,
            )
        }

        // Step 2: Find target shop
        val shop =
            if (shopkeeperName != null) {
                ShopDatabase.findByName(shopkeeperName)
                    ?: return BotPorcelain.PorcelainResult(
                        success = false,
                        message = "Shop '$shopkeeperName' not found",
                        actionResults = results,
                    )
            } else {
                ShopDatabase.findNearest(player.coords.x, player.coords.z, player.coords.level)
                    ?: return BotPorcelain.PorcelainResult(
                        success = false,
                        message = "No shop found nearby",
                        actionResults = results,
                    )
            }

        results.add("Found shop: ${shop.name} (${shop.shopkeeperName}) at (${shop.x}, ${shop.z})")

        // Step 3: Walk to shop if not near
        if (!ShopDatabase.isNearShop(player.coords.x, player.coords.z, player.coords.level)) {
            val walkResult = walkToShop(player, shop)
            if (!walkResult.success) {
                return BotPorcelain.PorcelainResult(
                    success = false,
                    message = "Failed to walk to shop: ${walkResult.message}",
                    actionResults = results,
                )
            }
            results.add("Walked to shop")
        }

        // Step 4: Talk to shopkeeper
        val interactResult = talkToShopkeeper(player, shop)
        if (!interactResult.success) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "Failed to open shop: ${interactResult.message}",
                actionResults = results,
            )
        }

        results.add("Opened shop interface")

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Successfully opened ${shop.name}",
            actionResults = results,
        )
    }

    /**
     * Closes the shop interface.
     *
     * @param player The player to perform the action
     * @return Result of the operation
     */
    fun closeShop(player: Player): BotPorcelain.PorcelainResult {
        if (!isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(success = true, message = "Shop is already closed")
        }

        queueAction(player, BotAction.CloseShop)

        return BotPorcelain.PorcelainResult(success = true, message = "Shop closed")
    }

    /**
     * Buys an item from the shop. Amount is auto-decomposed (10/5/1).
     *
     * @param player The player to perform the action
     * @param slot Shop slot to buy from
     * @param amount Amount to buy
     * @return Result of the operation
     */
    fun buyFromShop(player: Player, slot: Int, amount: Int = 1): BotPorcelain.PorcelainResult {
        if (!isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Shop is not open")
        }

        // Auto-decompose amount into 10/5/1 buy operations
        var remaining = amount
        val buyOps = mutableListOf<Int>()

        while (remaining > 0) {
            when {
                remaining >= 10 -> {
                    buyOps.add(10)
                    remaining -= 10
                }
                remaining >= 5 -> {
                    buyOps.add(5)
                    remaining -= 5
                }
                else -> {
                    buyOps.add(1)
                    remaining -= 1
                }
            }
        }

        // Queue buy operations
        for (buyAmount in buyOps) {
            queueAction(player, BotAction.BuyFromShop(slot, buyAmount))
        }

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Buying $amount items from shop slot $slot (${buyOps.size} operations)",
        )
    }

    /**
     * Buys item from shop by name.
     *
     * @param player The player to perform the action
     * @param itemName Name of item to buy
     * @param amount Amount to buy
     * @return Result of the operation
     */
    fun buyByName(player: Player, itemName: String, amount: Int = 1): BotPorcelain.PorcelainResult {
        if (!isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Shop is not open")
        }

        queueAction(player, BotAction.BuyByName(itemName, amount))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Buying $amount x '$itemName' from shop",
        )
    }

    /**
     * Sells an item to the shop.
     *
     * @param player The player to perform the action
     * @param inventorySlot Inventory slot to sell from
     * @param amount Amount to sell
     * @return Result of the operation
     */
    fun sellToShop(
        player: Player,
        inventorySlot: Int,
        amount: Int = 1,
    ): BotPorcelain.PorcelainResult {
        if (!isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Shop is not open")
        }

        queueAction(player, BotAction.SellToShop(inventorySlot, amount))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Selling $amount items from inventory slot $inventorySlot",
        )
    }

    /**
     * Finds a shop item by name pattern.
     *
     * @param player The player
     * @param pattern Name pattern to search for
     * @return Result with matching items
     */
    fun findShopItem(player: Player, pattern: String): BotPorcelain.PorcelainResult {
        if (!isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Shop is not open")
        }

        queueAction(player, BotAction.FindShopItem(pattern))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Searching shop for items matching '$pattern'",
        )
    }

    /**
     * Sells all items matching a pattern.
     *
     * @param player The player
     * @param pattern Item name pattern to match
     * @return Result of the operation
     */
    fun sellAll(player: Player, pattern: String): BotPorcelain.PorcelainResult {
        if (!isShopOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Shop is not open")
        }

        // Find all matching slots
        val matchingSlots = mutableListOf<Int>()
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            val type = ServerCacheManager.getItem(obj.id)
            if (type != null && type.name.contains(pattern, ignoreCase = true)) {
                matchingSlots.add(slot)
            }
        }

        if (matchingSlots.isEmpty()) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "No items matching '$pattern' found in inventory",
            )
        }

        // Sell each matching slot
        for (slot in matchingSlots) {
            val obj = player.inv[slot] ?: continue
            queueAction(player, BotAction.SellToShop(slot, obj.count))
        }

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Selling items from ${matchingSlots.size} slots matching '$pattern'",
        )
    }

    // -----------------------------------------------------------------------------------------
    // Private Helpers
    // -----------------------------------------------------------------------------------------

    /** Checks if a shop interface is currently open. */
    private fun isShopOpen(player: Player): Boolean {
        // Shop interface IDs vary, commonly 300+ range
        return player.ui.modals.any { it.value in 300..350 }
    }

    /** Walks to a shop location. */
    private fun walkToShop(player: Player, shop: ShopDatabase.ShopLocation): WalkResult {
        queueAction(player, BotAction.WalkWithDoors(shop.x, shop.z, shop.plane, tolerance = 3))
        return WalkResult(true, "Walking to shop")
    }

    /** Talks to shopkeeper to open shop. */
    private fun talkToShopkeeper(player: Player, shop: ShopDatabase.ShopLocation): InteractResult {
        queueAction(player, BotAction.TalkTo(shop.shopkeeperName))
        return InteractResult(true, "Talking to ${shop.shopkeeperName}")
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
            logger.error { "[ShopPorcelain] Failed to queue action: ${e.message}" }
        }
    }

    private data class WalkResult(val success: Boolean, val message: String)

    private data class InteractResult(val success: Boolean, val message: String)
}
