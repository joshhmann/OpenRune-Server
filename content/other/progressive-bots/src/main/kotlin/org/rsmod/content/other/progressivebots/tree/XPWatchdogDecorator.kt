package org.rsmod.content.other.progressivebots.tree

import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player

/**
 * Decorator that tracks XP drops for a specific stat.
 * If a bot goes [maxTicksWithoutXp] ticks without gaining XP, it aborts the current behavior.
 * This acts as a fallback to prevent silent pathing or interaction failures.
 */
class XPWatchdogDecorator(
    private val child: BehaviorNode,
    private val statToWatch: String,
    private val maxTicksWithoutXp: Int = 150
) : BehaviorNode {
    private var ticks = 0
    private var lastXp = -1

    override fun execute(player: Player, state: BotState): NodeStatus {
        val currentXp = player.statMap.getXP(statToWatch)
        
        if (lastXp < 0) {
            lastXp = currentXp
        }

        if (currentXp > lastXp) {
            // XP gained, reset watchdog
            lastXp = currentXp
            ticks = 0
        } else {
            ticks++
            if (ticks >= maxTicksWithoutXp) {
                // Stall detected! Abort goal to force utility reassessment.
                return NodeStatus.FAILURE
            }
        }

        return child.execute(player, state)
    }
}
