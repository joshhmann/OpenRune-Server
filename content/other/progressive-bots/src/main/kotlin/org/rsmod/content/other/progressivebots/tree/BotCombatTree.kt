package org.rsmod.content.other.progressivebots.tree

import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp

object TargetEvaluator {
    fun findClosestPlayerTarget(bot: Player, state: BotState, maxDistance: Int = 15): Player? {
        val playerList = state.goalStack.playerListContext ?: return null
        var closestTarget: Player? = null
        var minDistance = maxDistance

        val botCoords = bot.coords
        for (other in playerList) {
            if (other == null || other == bot) continue
            // Ensure same plane
            val otherCoords = other.coords
            if (otherCoords.level != botCoords.level) continue

            // Don't target bots for now, just humans? Or yes, bots target bots to simulate wildy.
            // For now, any other player is a valid target.
            val dist = botCoords.chebyshevDistance(otherCoords)
            if (dist < minDistance) {
                minDistance = dist
                closestTarget = other
            }
        }
        return closestTarget
    }
}

class EngagePvPNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val target = TargetEvaluator.findClosestPlayerTarget(player, state)
        if (target != null) {
            // Note: Since we don't have DI for PlayerInteractions here,
            // we will simulate the interaction via op2 queueing directly if possible,
            // or issue a route request.
            // For now, we simulate success if a target is found.
            return NodeStatus.SUCCESS
        }
        return NodeStatus.FAILURE
    }
}
