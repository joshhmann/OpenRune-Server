package org.rsmod.content.other.progressivebots.tree

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player

/**
 * Decorator that measures a bot's progress over a window of ticks. If the bot hasn't moved closer
 * to its destination by a minimum amount, it aborts the current behavior to prevent oscillation or
 * getting stuck.
 */
class StuckDetectorDecorator(
    private val child: BehaviorNode,
    private val windowTicks: Int = 40,
    private val minProgressTiles: Int = 5,
    private val freezeRadius: Int = 3,
) : BehaviorNode {

    private var ticks = 0
    private var snapshotDist = -1
    private var snapshotX = -1
    private var snapshotZ = -1

    override fun execute(player: Player, state: BotState): NodeStatus {
        val destX = state.goalStack.destinationX
        val destZ = state.goalStack.destinationZ

        if (destX == null || destZ == null) {
            // No destination set yet, just execute child
            return child.execute(player, state)
        }

        val currentX = player.coords.x
        val currentZ = player.coords.z

        val dist = max(abs(currentX - destX), abs(currentZ - destZ))

        if (snapshotDist < 0) {
            snapshotDist = dist
            snapshotX = currentX
            snapshotZ = currentZ
        }

        ticks++
        if (ticks >= windowTicks) {
            ticks = 0
            val progress = snapshotDist - dist
            val moved = max(abs(currentX - snapshotX), abs(currentZ - snapshotZ))

            snapshotDist = dist
            snapshotX = currentX
            snapshotZ = currentZ

            // If the bot hasn't left a small radius, it's frozen or oscillating
            if (moved <= freezeRadius || progress < minProgressTiles) {
                // Abort goal to force utility reassessment or escape walk
                return NodeStatus.FAILURE
            }
        }

        return child.execute(player, state)
    }
}
