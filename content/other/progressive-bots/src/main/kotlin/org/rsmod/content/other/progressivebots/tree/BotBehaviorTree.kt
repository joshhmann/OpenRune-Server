package org.rsmod.content.other.progressivebots.tree

import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player

enum class NodeStatus {
    SUCCESS,
    FAILURE,
    RUNNING
}

interface BehaviorNode {
    fun execute(player: Player, state: BotState): NodeStatus
}

abstract class ActionNode : BehaviorNode

class SequenceNode(private val nodes: List<BehaviorNode>) : BehaviorNode {
    private var currentIndex = 0

    override fun execute(player: Player, state: BotState): NodeStatus {
        while (currentIndex < nodes.size) {
            val status = nodes[currentIndex].execute(player, state)
            when (status) {
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.FAILURE -> {
                    currentIndex = 0
                    return NodeStatus.FAILURE
                }
                NodeStatus.SUCCESS -> {
                    currentIndex++
                }
            }
        }
        currentIndex = 0
        return NodeStatus.SUCCESS
    }
}

class SelectorNode(private val nodes: List<BehaviorNode>) : BehaviorNode {
    private var currentIndex = 0

    override fun execute(player: Player, state: BotState): NodeStatus {
        while (currentIndex < nodes.size) {
            val status = nodes[currentIndex].execute(player, state)
            when (status) {
                NodeStatus.RUNNING -> return NodeStatus.RUNNING
                NodeStatus.SUCCESS -> {
                    currentIndex = 0
                    return NodeStatus.SUCCESS
                }
                NodeStatus.FAILURE -> {
                    currentIndex++
                }
            }
        }
        currentIndex = 0
        return NodeStatus.FAILURE
    }
}

class InvertNode(private val child: BehaviorNode) : BehaviorNode {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val status = child.execute(player, state)
        return when (status) {
            NodeStatus.SUCCESS -> NodeStatus.FAILURE
            NodeStatus.FAILURE -> NodeStatus.SUCCESS
            NodeStatus.RUNNING -> NodeStatus.RUNNING
        }
    }
}
