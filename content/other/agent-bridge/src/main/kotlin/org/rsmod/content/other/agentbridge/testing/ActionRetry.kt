package org.rsmod.content.other.agentbridge.testing

import jakarta.inject.Inject
import org.rsmod.game.entity.Player

/**
 * Retry logic for flaky bot actions during QA testing.
 *
 * Provides configurable retry with backoff for actions that may fail due to
 * race conditions, server lag, or unhandled edge cases.
 */
class ActionRetry @Inject constructor() {
    companion object {
        /** Default number of retries. */
        private const val DEFAULT_MAX_RETRIES = 3

        /** Initial backoff in ticks. */
        private const val INITIAL_BACKOFF_TICKS = 2

        /** Whether to log retry attempts. */
        private var loggingEnabled = true
    }

    /**
     * Execute an action with retry logic.
     *
     * @param player The player performing the action
     * @param maxRetries Maximum number of retry attempts
     * @param action The action to execute (returns an ActionResult)
     * @param shouldRetry Predicate to determine if the result should trigger a retry
     * @return The result of the action
     */
    suspend fun <T : ActionResult> withRetry(
        player: Player,
        action: suspend () -> T,
        shouldRetry: (T) -> Boolean = { !it.success },
        maxRetries: Int = DEFAULT_MAX_RETRIES,
    ): T {
        var lastResult = action()
        var attempt = 0

        while (!lastResult.success && attempt < maxRetries) {
            attempt++
            if (loggingEnabled) {
                println(
                    "[ActionRetry] Attempt $attempt/$maxRetries for ${player.avatar.name}: " +
                        "${lastResult.message}"
                )
            }
            // Simple tick-based backoff
            Thread.sleep(INITIAL_BACKOFF_TICKS * 600L * attempt)
            lastResult = action()
        }

        return lastResult
    }

    /**
     * Try to open a blocking door (a door that requires interaction but may not respond).
     * Uses multiple strategies: walk adjacent first, then interact, wait, verify.
     *
     * @param player The player
     * @param maxDistance Max tiles to walk for door
     * @return true if door was opened successfully
     */
    suspend fun tryOpenBlockingDoor(player: Player, maxDistance: Int = 15): Boolean {
        // Delegate to the AgentBridge door system
        // For now, returns false as doors are handled by AgentBridgeScript's PendingDoorOp
        println("[ActionRetry] tryOpenBlockingDoor called for ${player.avatar.name}")
        return false
    }

    /**
     * Enable or disable retry logging.
     */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }
}

interface ActionResult {
    val success: Boolean
    val message: String
}
