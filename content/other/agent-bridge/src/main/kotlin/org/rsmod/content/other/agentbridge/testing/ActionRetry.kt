package org.rsmod.content.other.agentbridge.testing

import jakarta.inject.Inject
import org.rsmod.game.entity.Player

class ActionRetry @Inject constructor() {
    suspend fun <T : ActionResult> withDoorRetry(
        player: Player,
        action: suspend () -> T,
        shouldRetry: (T) -> Boolean,
        maxRetries: Int = 2,
    ): T {
        return action()
    }

    suspend fun tryOpenBlockingDoor(player: Player, maxDistance: Int = 15): Boolean {
        return false
    }
}

interface ActionResult {
    val success: Boolean
    val message: String
}
