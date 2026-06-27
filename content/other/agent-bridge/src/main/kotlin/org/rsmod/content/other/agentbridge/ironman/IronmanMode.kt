package org.rsmod.content.other.agentbridge.ironman

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.other.agentbridge.BotAction

/**
 * Ironman mode configuration and enforcement for AgentBridge.
 *
 * When ironman mode is enabled, bots must:
 * - Walk everywhere (no teleporting)
 * - Earn all items legitimately (no spawning)
 * - Use only porcelain actions that simulate real player behavior
 *
 * When disabled (test mode), all cheat commands are available.
 */
@Singleton
class IronmanMode @Inject constructor() {
    private val logger = InlineLogger()

    /** Current ironman mode state. */
    @Volatile
    var isEnabled: Boolean = false
        private set

    /** Toggle ironman mode on/off. */
    fun toggle(enabled: Boolean) {
        isEnabled = enabled
        logger.info { "[AgentBridge] Ironman mode ${if (enabled) "ENABLED" else "DISABLED"}" }
    }

    /**
     * Check if an action is allowed in the current mode.
     *
     * @param action The bot action to check
     * @return True if allowed, false if blocked
     */
    fun isActionAllowed(action: BotAction): Boolean {
        if (!isEnabled) return true // Test mode allows everything

        return when (action) {
            // Movement - only walking allowed
            is BotAction.Walk -> true
            is BotAction.WalkWithDoors -> true
            is BotAction.Teleport -> false // BLOCKED in ironman

            // Inventory manipulation - no spawning
            is BotAction.SpawnItem -> false // BLOCKED in ironman
            is BotAction.ClearInventory -> false // BLOCKED in ironman
            is BotAction.DeleteItem -> true // Allow dropping items
            is BotAction.EnsureItem -> false // BLOCKED in ironman

            // Interactions - all allowed
            is BotAction.InteractLoc -> true
            is BotAction.InteractNpc -> true
            is BotAction.InteractHeld -> true

            // Waiting - all allowed
            is BotAction.WaitTicks -> true
            is BotAction.WaitForAnimation -> true
            is BotAction.WaitForXp -> true
            is BotAction.WaitForItem -> true
            is BotAction.WaitForReady -> true
            is BotAction.WaitForPosition -> true
            is BotAction.WaitForCondition -> true
            is BotAction.WaitForGroundItem -> true
            is BotAction.WaitForPrayerPoints -> true

            // State queries - all allowed
            is BotAction.GetState -> true
            is BotAction.GetPrayerState -> true
            is BotAction.GetShopState -> true
            is BotAction.GetBankItems -> true

            // Pathfinding - all allowed
            is BotAction.FindPath -> true
            is BotAction.CheckWalkable -> true

            // Door handling - all allowed
            is BotAction.OpenDoor -> true
            is BotAction.BlockDoor -> true

            // Porcelain actions - all allowed (they use legit methods)
            is BotAction.ChopTree -> true
            is BotAction.BurnLogs -> true
            is BotAction.PickupItem -> true
            is BotAction.TalkTo -> true
            is BotAction.UseItemOnLoc -> true
            is BotAction.UseItemOnNpc -> true
            is BotAction.DismissBlockingUI -> true
            is BotAction.ExecutePorcelain -> true

            // Banking - all allowed (legit gameplay)
            is BotAction.OpenBank -> true
            is BotAction.BankDeposit -> true
            is BotAction.BankWithdraw -> true
            is BotAction.CloseBank -> true
            is BotAction.FindBankItem -> true
            is BotAction.BankDepositAll -> true
            is BotAction.BankWithdrawByName -> true

            // Shops - all allowed (legit gameplay)
            is BotAction.OpenShop -> true
            is BotAction.BuyFromShop -> true
            is BotAction.BuyByName -> true
            is BotAction.SellToShop -> true
            is BotAction.CloseShop -> true
            is BotAction.FindShopItem -> true
            is BotAction.HaggleShop -> true

            // Ground items - all allowed (legit gameplay)
            is BotAction.ScanGroundItems -> true
            is BotAction.FindGroundItem -> true
            is BotAction.PickupGroundItem -> true
            is BotAction.PickupNearest -> true
            is BotAction.LootArea -> true
            is BotAction.ScanNearbyLocs -> true

            // Prayer - all allowed (legit gameplay)
            is BotAction.TogglePrayer -> true
            is BotAction.ActivatePrayer -> true
            is BotAction.DeactivatePrayer -> true
            is BotAction.DeactivateAllPrayers -> true
            is BotAction.ActivateBestCombatPrayer -> true

            // Combat - all allowed (legit gameplay)
            is BotAction.AttackNpc -> true
            is BotAction.FightUntilHp -> true
            is BotAction.EatFood -> true
            is BotAction.SetCombatStyle -> true
            else -> true
        }
    }

    /**
     * Get the error message for a blocked action.
     *
     * @param action The blocked action
     * @return Human-readable error message
     */
    fun getBlockedMessage(action: BotAction): String {
        return when (action) {
            is BotAction.Teleport ->
                "Teleport is disabled in ironman mode. Use walkWithDoors() instead."
            is BotAction.SpawnItem ->
                "Spawning items is disabled in ironman mode. Earn items through gameplay."
            is BotAction.ClearInventory ->
                "Clearing inventory is disabled in ironman mode. Drop items manually."
            is BotAction.EnsureItem ->
                "Ensuring items is disabled in ironman mode. Obtain items legitimately."
            else -> "This action is disabled in ironman mode."
        }
    }

    /** Get the current mode description. */
    fun getModeDescription(): String {
        return if (isEnabled) {
            """
            IRONMAN MODE ENABLED
            - Walking only (no teleporting)
            - Earn all items legitimately
            - Use porcelain actions for real gameplay
            """
                .trimIndent()
        } else {
            """
            TEST MODE ENABLED
            - All commands available
            - Teleport, spawn items, etc.
            - For rapid development and testing
            """
                .trimIndent()
        }
    }

    companion object {
        /** Actions that are considered "cheats" and blocked in ironman mode. */
        val CHEAT_ACTIONS =
            setOf(
                BotAction.Teleport::class,
                BotAction.SpawnItem::class,
                BotAction.ClearInventory::class,
                BotAction.EnsureItem::class,
            )
    }
}
