package org.rsmod.content.other.agentbridge.banking

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.other.agentbridge.AgentBridgeServer
import org.rsmod.content.other.agentbridge.BotAction
import org.rsmod.content.other.agentbridge.pathfinding.PathfindingService
import org.rsmod.content.other.agentbridge.porcelain.BotPorcelain
import org.rsmod.game.entity.Player

/**
 * High-level banking porcelain actions for bot automation. Provides convenient methods for bank
 * operations with automatic navigation.
 */
@Singleton
class BankPorcelain
@Inject
constructor(private val server: AgentBridgeServer, private val pathfinding: PathfindingService) {
    private val logger = InlineLogger()

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15000
        private const val SCAN_RADIUS = 16
    }

    /**
     * Opens the nearest bank. Walks to the bank if needed and interacts with it.
     *
     * @param player The player to perform the action
     * @param bankName Optional specific bank name to use
     * @return Result of the operation
     */
    fun openBank(player: Player, bankName: String? = null): BotPorcelain.PorcelainResult {
        val results = mutableListOf<String>()

        // Step 1: Check if bank is already open
        if (isBankOpen(player)) {
            return BotPorcelain.PorcelainResult(
                success = true,
                message = "Bank is already open",
                actionResults = results,
            )
        }

        // Step 2: Find target bank
        val bank =
            if (bankName != null) {
                BankDatabase.findByName(bankName)
                    ?: return BotPorcelain.PorcelainResult(
                        success = false,
                        message = "Bank '$bankName' not found",
                        actionResults = results,
                    )
            } else {
                BankDatabase.findNearest(player.coords.x, player.coords.z, player.coords.level)
                    ?: return BotPorcelain.PorcelainResult(
                        success = false,
                        message = "No bank found nearby",
                        actionResults = results,
                    )
            }

        results.add("Found bank: ${bank.name} at (${bank.x}, ${bank.z})")

        // Step 3: Walk to bank if not near
        if (!BankDatabase.isNearBank(player.coords.x, player.coords.z, player.coords.level)) {
            val walkResult = walkToBank(player, bank)
            if (!walkResult.success) {
                return BotPorcelain.PorcelainResult(
                    success = false,
                    message = "Failed to walk to bank: ${walkResult.message}",
                    actionResults = results,
                )
            }
            results.add("Walked to bank")
        }

        // Step 4: Interact with bank
        val interactResult = interactWithBank(player, bank)
        if (!interactResult.success) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "Failed to open bank: ${interactResult.message}",
                actionResults = results,
            )
        }

        results.add("Opened bank interface")

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Successfully opened ${bank.name}",
            actionResults = results,
        )
    }

    /**
     * Closes the bank interface.
     *
     * @param player The player to perform the action
     * @return Result of the operation
     */
    fun closeBank(player: Player): BotPorcelain.PorcelainResult {
        if (!isBankOpen(player)) {
            return BotPorcelain.PorcelainResult(success = true, message = "Bank is already closed")
        }

        // Queue close bank action
        queueAction(player, BotAction.CloseBank)

        return BotPorcelain.PorcelainResult(success = true, message = "Bank closed")
    }

    /**
     * Deposits items to the bank.
     *
     * @param player The player to perform the action
     * @param slot Inventory slot to deposit
     * @param amount Amount to deposit (or Int.MAX_VALUE for all)
     * @param note Whether to note the items
     * @return Result of the operation
     */
    fun deposit(
        player: Player,
        slot: Int,
        amount: Int = 1,
        note: Boolean = false,
    ): BotPorcelain.PorcelainResult {
        if (!isBankOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Bank is not open")
        }

        // Validate slot
        if (slot < 0 || slot >= player.inv.size) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "Invalid inventory slot: $slot",
            )
        }

        val item = player.inv[slot]
        if (item == null) {
            return BotPorcelain.PorcelainResult(success = false, message = "No item in slot $slot")
        }

        queueAction(player, BotAction.BankDeposit(slot, amount, note))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Deposited ${amount}x item from slot $slot",
        )
    }

    /**
     * Deposits all items matching a pattern.
     *
     * @param player The player to perform the action
     * @param pattern Item name pattern to match (null = all items)
     * @param keepAmount Amount to keep in inventory
     * @return Result of the operation
     */
    fun depositAll(
        player: Player,
        pattern: String? = null,
        keepAmount: Int = 0,
    ): BotPorcelain.PorcelainResult {
        if (!isBankOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Bank is not open")
        }

        queueAction(player, BotAction.BankDepositAll(pattern, keepAmount))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Depositing${pattern?.let { " items matching '$it'" } ?: " all items"}",
        )
    }

    /**
     * Withdraws items from the bank.
     *
     * @param player The player to perform the action
     * @param slot Bank slot to withdraw from
     * @param amount Amount to withdraw
     * @param note Whether to withdraw as notes
     * @return Result of the operation
     */
    fun withdraw(
        player: Player,
        slot: Int,
        amount: Int = 1,
        note: Boolean = false,
    ): BotPorcelain.PorcelainResult {
        if (!isBankOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Bank is not open")
        }

        queueAction(player, BotAction.BankWithdraw(slot, amount, note))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Withdrawing ${amount}x from bank slot $slot",
        )
    }

    /**
     * Withdraws item by name.
     *
     * @param player The player to perform the action
     * @param itemName Name of the item to withdraw
     * @param amount Amount to withdraw
     * @param note Whether to withdraw as notes
     * @return Result of the operation
     */
    fun withdrawByName(
        player: Player,
        itemName: String,
        amount: Int = 1,
        note: Boolean = false,
    ): BotPorcelain.PorcelainResult {
        if (!isBankOpen(player)) {
            return BotPorcelain.PorcelainResult(success = false, message = "Bank is not open")
        }

        queueAction(player, BotAction.BankWithdrawByName(itemName, amount, note))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Withdrawing ${amount}x '$itemName' from bank",
        )
    }

    /**
     * Finds a bank item by name pattern.
     *
     * @param player The player
     * @param pattern Name pattern to search for
     * @return List of matching bank items
     */
    fun findBankItem(player: Player, pattern: String): BotPorcelain.PorcelainResult {
        queueAction(player, BotAction.FindBankItem(pattern))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Searching bank for items matching '$pattern'",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------------------------

    /** Checks if the bank interface is currently open. */
    private fun isBankOpen(player: Player): Boolean {
        // Bank interface is typically modal with specific ID
        // Interface IDs vary by revision, commonly 12 or in the 200+ range
        return player.ui.modals.any { it.value == 12 || it.value == 15 }
    }

    /** Walks to a bank location. */
    private fun walkToBank(player: Player, bank: BankDatabase.BankLocation): WalkResult {
        // Use WalkWithDoors for door handling
        queueAction(player, BotAction.WalkWithDoors(bank.x, bank.z, bank.plane, tolerance = 2))
        return WalkResult(true, "Walking to bank")
    }

    /** Interacts with the bank to open interface. */
    private fun interactWithBank(player: Player, bank: BankDatabase.BankLocation): InteractResult {
        // Different interaction based on bank type
        val action =
            when (bank.type) {
                BankDatabase.BankType.BOOTH -> {
                    // Find and interact with bank booth
                    BotAction.InteractLoc(2213, bank.x, bank.z, 1) // Standard bank booth ID
                }
                BankDatabase.BankType.NPC -> {
                    // Find nearest banker NPC and talk
                    BotAction.TalkTo("Banker")
                }
                BankDatabase.BankType.CHEST -> {
                    BotAction.InteractLoc(4483, bank.x, bank.z, 1) // Bank chest
                }
                BankDatabase.BankType.DEPOSIT_BOX -> {
                    BotAction.InteractLoc(9398, bank.x, bank.z, 1) // Deposit box
                }
            }
        queueAction(player, action)
        return InteractResult(true, "Interacting with ${bank.type.name.lowercase()}")
    }

    /** Queues an action via reflection (same pattern as BotPorcelain). */
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
            logger.error { "[BankPorcelain] Failed to queue action: ${e.message}" }
        }
    }

    private data class WalkResult(val success: Boolean, val message: String)

    private data class InteractResult(val success: Boolean, val message: String)
}
