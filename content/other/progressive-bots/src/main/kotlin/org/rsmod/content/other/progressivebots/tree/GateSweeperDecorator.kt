package org.rsmod.content.other.progressivebots.tree

import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player

/**
 * Universal background behavior node to automatically sweep and open gates/doors 
 * within a 5-tile radius if they are closed.
 * This runs alongside the main goal to prevent pathing getting permanently blocked.
 */
class GateSweeperDecorator(
    private val child: BehaviorNode,
    private val radius: Int = 5
) : BehaviorNode {
    
    // Throttle sweeps to every 5 executions
    private var sweepCounter = 0

    override fun execute(player: Player, state: BotState): NodeStatus {
        sweepCounter++
        if (sweepCounter >= 5) {
            sweepCounter = 0
            sweepForGates(player)
        }
        return child.execute(player, state)
    }

    private fun sweepForGates(player: Player) {
        // Logic to scan for closed doors/gates in radius and queue an interaction.
        // Needs integration with LocList / collision flags in the engine to find 
        // Loc objects with "door" or "gate" in their name that have a closed state.
        // For now, this is a stub that will be fleshed out with Engine Loc API.
    }
}
