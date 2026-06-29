package org.rsmod.content.other.agentbridge.testing

/**
 * Named wait conditions for common test scenarios.
 *
 * Each condition maps to a check that can be evaluated server-side on the game thread,
 * allowing the test script to "wait until X happens" without polling from external code.
 *
 * These are referenced by strings so they can be serialized over AgentBridge WebSocket.
 */
object WaitConditions {
    /** Condition names that the test system recognizes. */
    const val READY = "ready"
    const val POSITION = "position"
    const val INVENTORY_FULL = "inventory_full"
    const val INVENTORY_EMPTY = "inventory_empty"
    const val DIALOG_OPEN = "dialog_open"
    const val DIALOG_CLOSED = "dialog_closed"
    const val BANK_OPEN = "bank_open"
    const val SHOP_OPEN = "shop_open"
    const val IN_COMBAT = "in_combat"
    const val NOT_IN_COMBAT = "not_in_combat"
    const val HP_BELOW = "hp_below"
    const val HP_ABOVE = "hp_above"
    const val HP_FULL = "hp_full"
    const val ANIMATION_IDLE = "animation_idle"
    const val ANIMATION_PLAYING = "animation_playing"
    const val ITEM_IN_INVENTORY = "item_in_inventory"
    const val ITEM_NOT_IN_INVENTORY = "item_not_in_inventory"
    const val XP_GAINED = "xp_gained"
    const val PLAYER_MOVED = "player_moved"
    const val QUEST_COMPLETED = "quest_completed"
    const val QUEST_STARTED = "quest_started"
    const val VAR_PLAYER_SET = "var_player_set"
    const val VAR_PLAYER_UNSET = "var_player_unset"
    const val GROUND_ITEM_VISIBLE = "ground_item_visible"
    const val GROUND_ITEM_PICKED = "ground_item_picked"

    /**
     * Get a human-readable description of a wait condition.
     */
    fun describe(condition: String, params: Map<String, Any> = emptyMap()): String =
        when (condition) {
            READY -> "Waiting for player to be ready (in-game with valid position)"
            POSITION -> "Waiting for position ${params["x"]},${params["z"]} (tol=${params["tolerance"]})"
            INVENTORY_FULL -> "Waiting for inventory to be full"
            INVENTORY_EMPTY -> "Waiting for inventory to be empty"
            DIALOG_OPEN -> "Waiting for dialogue/chat window to open"
            DIALOG_CLOSED -> "Waiting for dialogue/chat window to close"
            BANK_OPEN -> "Waiting for bank interface to open"
            SHOP_OPEN -> "Waiting for shop interface to open"
            IN_COMBAT -> "Waiting to enter combat"
            NOT_IN_COMBAT -> "Waiting to exit combat"
            HP_BELOW -> "Waiting for HP to drop below ${params["threshold"]}"
            HP_FULL -> "Waiting for full HP"
            ANIMATION_IDLE -> "Waiting for player animation to stop"
            ITEM_IN_INVENTORY -> "Waiting for item ${params["itemName"] ?: params["itemId"]} in inventory"
            XP_GAINED -> "Waiting for XP gain in ${params["skill"]}"
            QUEST_COMPLETED -> "Waiting for quest completion"
            else -> "Waiting for condition '$condition'"
        }

    /**
     * Get a suggested timeout in ticks based on the condition type.
     */
    fun suggestedTimeoutTicks(condition: String): Int =
        when (condition) {
            READY -> 25 // ~15 seconds
            POSITION -> 50 // ~30 seconds
            DIALOG_OPEN -> 17 // ~10 seconds
            DIALOG_CLOSED -> 17 // ~10 seconds
            BANK_OPEN -> 25 // ~15 seconds
            SHOP_OPEN -> 25 // ~15 seconds
            INVENTORY_FULL -> 100 // ~60 seconds
            ITEM_IN_INVENTORY -> 50 // ~30 seconds
            XP_GAINED -> 50 // ~30 seconds
            ANIMATION_IDLE -> 25 // ~15 seconds
            QUEST_COMPLETED -> 300 // ~3 minutes (quests can be long)
            HP_BELOW -> 100 // ~60 seconds
            else -> 50 // Default ~30 seconds
        }
}
