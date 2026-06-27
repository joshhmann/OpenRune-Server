package org.rsmod.content.other.progressivebots.economy

import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.content.other.progressivebots.tree.ActionNode
import org.rsmod.content.other.progressivebots.tree.NodeStatus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

object GrandExchangeLogic {
    // Basic coords for the Grand Exchange in Varrock
    val GE_COORDS = CoordGrid(3164, 3481, 0)
}

class GrandExchangeNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val dist = player.coords.chebyshevDistance(GrandExchangeLogic.GE_COORDS)
        
        if (dist > 10) {
            // Path towards the GE
            player.walk(GrandExchangeLogic.GE_COORDS)
            return NodeStatus.RUNNING
        }
        
        // At the GE, interact with the clerk and place offers
        // In a full implementation, we'd queue an InteractionOp with the GE Clerk NPC
        return NodeStatus.SUCCESS
    }
}
