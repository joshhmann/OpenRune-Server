package org.rsmod.content.other.progressivebots

import org.rsmod.content.other.progressivebots.tree.ActionNode
import org.rsmod.content.other.progressivebots.tree.BehaviorNode
import org.rsmod.content.other.progressivebots.tree.GateSweeperDecorator
import org.rsmod.content.other.progressivebots.tree.NodeStatus
import org.rsmod.content.other.progressivebots.tree.StuckDetectorDecorator
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class WanderNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val dx = (-4..4).random()
        val dz = (-4..4).random()
        val coords = player.coords
        player.walk(
            CoordGrid((coords.x + dx).coerceIn(3200, 3270), (coords.z + dz).coerceIn(3200, 3270), 0)
        )
        return NodeStatus.SUCCESS
    }
}

class SocializeNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        return org.rsmod.content.other.progressivebots.tree.BeggarTreeBuilder.build()
            .execute(player, state)
    }
}

class FightNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        return org.rsmod.content.other.progressivebots.tree.FightTreeBuilder.build()
            .execute(player, state)
    }
}

class ShopNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        player.walk(CoordGrid(3215, 3245, 0))
        return NodeStatus.SUCCESS
    }
}

/**
 * Bot personality — defines behavior patterns per planner archetype.
 *
 * Each archetype has a tick-interval decision function that picks what action the bot should take
 * next based on its state.
 */
sealed class BotPersonality {
    /** Pick the next goal tree for this bot. */
    open fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> =
        wrapGoal(WanderNode(), "Wander")

    protected fun wrapGoal(node: BehaviorNode, name: String): Pair<BehaviorNode, String> {
        val wrapped = GateSweeperDecorator(StuckDetectorDecorator(node))
        return Pair(wrapped, name)
    }

    companion object {
        fun forPlanner(planner: BotPlanner): BotPersonality =
            when (planner) {
                BotPlanner.Skiller -> SkillerPersonality()
                BotPlanner.Fighter -> FighterPersonality()
                BotPlanner.Balanced -> BalancedPersonality()
                BotPlanner.Social -> SocialPersonality()
                BotPlanner.Vendor -> VendorPersonality()
                BotPlanner.PKer -> PKerPersonality()
            }
    }
}

/** Gather resources: chop trees, mine rocks, fish, etc. */
class SkillerPersonality : BotPersonality() {
    override fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> {
        // Broke fallback
        if (player.gpCount < 100) {
            val roll = (0..99).random()
            return if (roll < 40) {
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.BeggarTreeBuilder.build(),
                    "Begging",
                )
            } else {
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.FightTreeBuilder.build(),
                    "FarmGold",
                )
            }
        }

        val roll = (0..99).random()
        return if (roll < 50) {
            wrapGoal(
                org.rsmod.content.other.progressivebots.tree.GatherTreeBuilder.build("woodcutting"),
                "Woodcut",
            )
        } else {
            wrapGoal(
                org.rsmod.content.other.progressivebots.tree.ProductionTreeBuilder.build(),
                "Smithing",
            )
        }
    }
}

/** Fight NPCs to train combat. */
class FighterPersonality : BotPersonality() {
    override fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> {
        if (player.gpCount < 100) {
            val roll = (0..99).random()
            if (roll < 30) {
                return wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.BeggarTreeBuilder.build(),
                    "Begging",
                )
            }
        }
        return wrapGoal(
            org.rsmod.content.other.progressivebots.tree.FightTreeBuilder.build(),
            "Fight",
        )
    }
}

/** Mix of skills, combat, and questing. */
class BalancedPersonality : BotPersonality() {
    override fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> {
        if (player.gpCount < 100) {
            val roll = (0..99).random()
            return if (roll < 50) {
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.BeggarTreeBuilder.build(),
                    "Begging",
                )
            } else {
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.FightTreeBuilder.build(),
                    "FarmGold",
                )
            }
        }

        val roll = (0..99).random()
        return when {
            roll < 20 ->
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.GatherTreeBuilder.build(
                        "woodcutting"
                    ),
                    "Woodcut",
                )
            roll < 40 ->
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.ProductionTreeBuilder.build(),
                    "Smithing",
                )
            roll < 70 ->
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.FightTreeBuilder.build(),
                    "Fight",
                )
            roll < 85 ->
                wrapGoal(
                    org.rsmod.content.other.progressivebots.tree.BeggarTreeBuilder.build(),
                    "Socialize",
                )
            else -> wrapGoal(WanderNode(), "Wander")
        }
    }
}

/** Wander around high-traffic areas and socialize. */
class SocialPersonality : BotPersonality() {
    override fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> {
        return wrapGoal(SocializeNode(), "Socialize")
    }
}

/** Buy low, sell high at shops. */
class VendorPersonality : BotPersonality() {
    override fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> {
        return wrapGoal(ShopNode(), "Shop")
    }
}

/** Hunt players in the Wilderness. */
class PKerPersonality : BotPersonality() {
    override fun pickGoal(player: BotPlayerView, state: BotState): Pair<BehaviorNode, String> {
        return wrapGoal(WanderNode(), "Wander") // Placeholder
    }
}

/** Lightweight view of a player's state for bot decision-making. */
data class BotPlayerView(
    val x: Int,
    val z: Int,
    val level: Int,
    val inCombat: Boolean,
    val animating: Boolean,
    val playerList: org.rsmod.game.entity.PlayerList? = null,
    val gpCount: Int = 0,
)
