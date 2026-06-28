package org.rsmod.content.other.progressivebots.tree

import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player

class GoalStack {
    private var activeTree: BehaviorNode? = null
    var playerListContext: org.rsmod.game.entity.PlayerList? = null
    var destinationX: Int? = null
    var destinationZ: Int? = null

    fun setTree(tree: BehaviorNode) {
        activeTree = tree
    }

    fun clear() {
        activeTree = null
    }

    fun hasActiveGoal(): Boolean = activeTree != null

    fun tick(player: Player, state: BotState) {
        val tree = activeTree ?: return

        val status = tree.execute(player, state)
        if (status == NodeStatus.SUCCESS || status == NodeStatus.FAILURE) {
            // Goal completed or failed, clear the stack
            activeTree = null
        }
    }
}
