package org.rsmod.content.other.agentbridge

/**
 * Represents an action command received from an LLM agent via WebSocket.
 *
 * Inbound JSON format:
 * ```
 * { "player": "TestBot", "type": "walk", "x": 3222, "z": 3219 }
 * { "player": "TestBot", "type": "teleport", "x": 3222, "z": 3219, "plane": 0 }
 * { "player": "TestBot", "type": "interact_loc", "id": 10820, "x": 3223, "z": 3219, "option": 1 }
 * { "player": "TestBot", "type": "interact_npc", "index": 7, "option": 1 }
 * { "player": "TestBot", "type": "spawn_item", "item_id": 1511, "count": 1 }
 * { "player": "TestBot", "type": "clear_inventory" }
 * { "player": "TestBot", "type": "wait_ticks", "ticks": 5 }
 * { "player": "TestBot", "type": "wait_for_animation", "animation_id": 867, "timeout": 5000 }
 * { "player": "TestBot", "type": "wait_for_xp", "skill": "woodcutting", "min_amount": 25, "timeout": 10000 }
 * { "player": "TestBot", "type": "wait_for_item", "item_id": 1511, "timeout": 5000 }
 * ```
 *
 * `player` is optional — if omitted, the command targets all logged-in players (broadcast).
 */
sealed class BotAction {
    /** Walk to (x, z) using pathfinding. */
    data class Walk(val x: Int, val z: Int) : BotAction()

    /** Teleport instantly to (x, z, plane). */
    data class Teleport(val x: Int, val z: Int, val plane: Int = 0) : BotAction()

    /** Interact with a game object (loc) at (x, z) using the given option (1-5). */
    data class InteractLoc(val id: Int, val x: Int, val z: Int, val option: Int = 1) : BotAction()

    /** Interact with an NPC at the given server index using the given option (1-5). */
    data class InteractNpc(val index: Int, val option: Int = 1) : BotAction()

    /** Use one inventory item on another inventory item by slot index. */
    data class InteractHeld(val selectedSlot: Int, val targetSlot: Int) : BotAction()

    /** Spawn an item directly into the player's inventory. */
    data class SpawnItem(val itemId: Int, val count: Int = 1) : BotAction()

    /** Clear all items from inventory. */
    data object ClearInventory : BotAction()

    /** Delete a specific item from inventory. */
    data class DeleteItem(val itemId: Int, val count: Int = 1) : BotAction()

    /** Wait for N game ticks before next action. */
    data class WaitTicks(val ticks: Int) : BotAction()

    /** Wait for a specific animation to start. */
    data class WaitForAnimation(val animationId: Int, val timeoutMs: Int = 5000) : BotAction()

    /** Wait for XP gain in a specific skill. */
    data class WaitForXp(val skill: String, val minAmount: Int, val timeoutMs: Int = 10000) :
        BotAction()

    /** Wait for an item to appear in inventory. */
    data class WaitForItem(val itemId: Int, val timeoutMs: Int = 5000) : BotAction()

    /** Ensure player has item (spawn if missing). */
    data class EnsureItem(val itemId: Int, val count: Int = 1) : BotAction()

    /** Get current state/position without modifying anything. */
    data object GetState : BotAction()

    /** Wait for player to be ready (valid position, in-game). */
    data class WaitForReady(val timeoutMs: Int = 15000) : BotAction()

    /** Wait for player position to be within tolerance of target. */
    data class WaitForPosition(
        val x: Int,
        val z: Int,
        val tolerance: Int = 3,
        val timeoutMs: Int = 30000,
    ) : BotAction()

    /** Wait for a condition to be met (checked server-side). */
    data class WaitForCondition(val conditionType: String, val timeoutMs: Int = 30000) :
        BotAction()

    /** Find path to destination coordinates. */
    data class FindPath(val x: Int, val z: Int, val plane: Int = 0, val maxWaypoints: Int = 500) :
        BotAction()

    /** Check if a tile is walkable. */
    data class CheckWalkable(val x: Int, val z: Int, val plane: Int = 0) : BotAction()

    /** Open a door or gate at specific coordinates. */
    data class OpenDoor(val x: Int, val z: Int, val plane: Int = 0, val timeoutMs: Int = 8000) :
        BotAction()

    /** Block a door in the pathfinder (for locked doors that can't be opened). */
    data class BlockDoor(val x: Int, val z: Int, val plane: Int = 0) : BotAction()

    /** Walk to destination with automatic door handling. */
    data class WalkWithDoors(val x: Int, val z: Int, val plane: Int = 0, val tolerance: Int = 3) :
        BotAction()

    // ===== PORCELAIN ACTIONS =====

    /** Chop a tree and wait for logs/depletion. */
    data class ChopTree(
        val targetName: String? = null,
        val maxLogs: Int = 1,
        val timeoutMs: Int = 60000,
    ) : BotAction()

    /** Burn logs and wait for XP. */
    data class BurnLogs(
        val logType: String? = null,
        val count: Int = 1,
        val timeoutMs: Int = 30000,
    ) : BotAction()

    /** Pick up a ground item with retry. */
    data class PickupItem(
        val itemName: String,
        val maxRetries: Int = 3,
        val timeoutMs: Int = 15000,
    ) : BotAction()

    /** Talk to an NPC. */
    data class TalkTo(val npcName: String, val timeoutMs: Int = 15000) : BotAction()

    /** Use item on location. */
    data class UseItemOnLoc(val itemName: String, val locName: String, val timeoutMs: Int = 15000) :
        BotAction()

    /** Use item on NPC. */
    data class UseItemOnNpc(val itemName: String, val npcName: String, val timeoutMs: Int = 15000) :
        BotAction()

    /** Dismiss blocking UI (level up, etc). */
    data class DismissBlockingUI(val timeoutMs: Int = 5000) : BotAction()

    /** Execute porcelain action sequence. */
    data class ExecutePorcelain(
        val porcelainType: String,
        val params: Map<String, String> = emptyMap(),
        val timeoutMs: Int = 60000,
    ) : BotAction()

    // ===== BANKING ACTIONS =====

    /** Open the nearest bank. */
    data class OpenBank(val timeoutMs: Int = 15000) : BotAction()

    /** Deposit items to bank. */
    data class BankDeposit(val slot: Int, val amount: Int = 1, val note: Boolean = false) :
        BotAction()

    /** Withdraw items from bank. */
    data class BankWithdraw(val slot: Int, val amount: Int = 1, val note: Boolean = false) :
        BotAction()

    /** Close the bank interface. */
    data object CloseBank : BotAction()

    /** Find bank item by name pattern. */
    data class FindBankItem(val pattern: String, val timeoutMs: Int = 5000) : BotAction()

    /** Get all bank items (for state refresh). */
    data object GetBankItems : BotAction()

    /** Deposit all items matching pattern. */
    data class BankDepositAll(val pattern: String? = null, val keepAmount: Int = 0) : BotAction()

    /** Withdraw exact amount of item by name. */
    data class BankWithdrawByName(
        val itemName: String,
        val amount: Int,
        val note: Boolean = false,
    ) : BotAction()

    // ===== SHOP ACTIONS =====

    /** Open a shop by shopkeeper name or walk to nearest shop. */
    data class OpenShop(val shopkeeperName: String? = null, val timeoutMs: Int = 15000) :
        BotAction()

    /** Buy item from shop. Amount is auto-decomposed (10/5/1). */
    data class BuyFromShop(val slot: Int, val amount: Int = 1, val timeoutMs: Int = 5000) :
        BotAction()

    /** Buy item from shop by name. */
    data class BuyByName(val itemName: String, val amount: Int = 1, val timeoutMs: Int = 5000) :
        BotAction()

    /** Sell item to shop. */
    data class SellToShop(val inventorySlot: Int, val amount: Int = 1, val timeoutMs: Int = 5000) :
        BotAction()

    /** Close the shop interface. */
    data object CloseShop : BotAction()

    /** Find shop item by name pattern. */
    data class FindShopItem(val pattern: String, val timeoutMs: Int = 3000) : BotAction()

    /** Get shop state/inventory. */
    data object GetShopState : BotAction()

    /** Haggle with shopkeeper (if supported). */
    data class HaggleShop(val itemSlot: Int, val targetPrice: Int) : BotAction()

    // ===== GROUND ITEM ACTIONS =====

    /** Scan for ground items in radius. */
    data class ScanGroundItems(val radius: Int = 10, val pattern: String? = null) : BotAction()

    /** Find ground item by name pattern. */
    data class FindGroundItem(val pattern: String, val radius: Int = 10) : BotAction()

    /** Pick up a specific ground item. */
    data class PickupGroundItem(
        val x: Int,
        val z: Int,
        val itemId: Int,
        val timeoutMs: Int = 10000,
    ) : BotAction()

    /** Pick up nearest item matching pattern. */
    data class PickupNearest(
        val pattern: String,
        val maxDistance: Int = 10,
        val timeoutMs: Int = 15000,
    ) : BotAction()

    /** Wait for ground item to appear. */
    data class WaitForGroundItem(val pattern: String, val timeoutMs: Int = 30000) : BotAction()

    /** Extended location scan (beyond normal state). */
    data class ScanNearbyLocs(val radius: Int = 20, val pattern: String? = null) : BotAction()

    /** Take all ground items within radius (loot area). */
    data class LootArea(
        val radius: Int = 5,
        val patterns: List<String> = emptyList(),
        val timeoutMs: Int = 60000,
    ) : BotAction()

    // ===== PRAYER ACTIONS =====

    /** Toggle a prayer by name or index. */
    data class TogglePrayer(val prayerName: String? = null, val prayerIndex: Int = -1) :
        BotAction()

    /** Activate a specific prayer. */
    data class ActivatePrayer(
        val prayerName: String,
        val allowToggle: Boolean = true, // If true, won't error if already active
    ) : BotAction()

    /** Deactivate a specific prayer. */
    data class DeactivatePrayer(val prayerName: String) : BotAction()

    /** Deactivate all prayers. */
    data object DeactivateAllPrayers : BotAction()

    /** Get current prayer state. */
    data object GetPrayerState : BotAction()

    /** Wait for prayer points to be at minimum level. */
    data class WaitForPrayerPoints(val minPoints: Int, val timeoutMs: Int = 30000) : BotAction()

    /** Activate best combat prayer based on level. */
    data class ActivateBestCombatPrayer(val prayerType: CombatPrayerType = CombatPrayerType.MELEE) :
        BotAction()

    // ===== COMBAT ACTIONS =====

    /** Attack an NPC by server list index. */
    data class AttackNpc(val index: Int, val timeoutMs: Int = 10000) : BotAction()

    /** Fight current target until hp drops to threshold, then flee. */
    data class FightUntilHp(val threshold: Int, val timeoutMs: Int = 60000) : BotAction()

    /** Eat food item (optional name pattern). */
    data class EatFood(val foodItem: String? = null) : BotAction()

    /** Set combat style / stance. */
    data class SetCombatStyle(val style: String) : BotAction()

    /** Combat prayer types for auto-selection. */
    enum class CombatPrayerType {
        MELEE, // Strength/Attack prayers
        DEFENSE, // Defense prayers
        PROTECTION, // Protection prayers (magic/ranged/melee)
    }

    // ===== BOT MANAGEMENT ACTIONS =====

    /** Spawn a new headless bot player into the world. */
    data class SpawnBot(val name: String, val x: Int = 3222, val z: Int = 3222) : BotAction()

    /** Despawn a bot player from the world. */
    data class DespawnBot(val name: String) : BotAction()

    /** List all active bot players. */
    data object ListBots : BotAction()
}
