@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.progressivebots.tree

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PublicMessage
import org.rsmod.map.CoordGrid
import org.rsmod.game.movement.RouteRequestCoord
import kotlin.random.Random

class IsBrokeNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val coinsId = ServerCacheManager.getItem("obj.coins".asRSCM(RSCMType.OBJ))?.id ?: 995
        val gpCount = player.inv.firstOrNull { it?.id == coinsId }?.count ?: 0
        
        // Broke if less than 100 GP
        return if (gpCount < 100) NodeStatus.SUCCESS else NodeStatus.FAILURE
    }
}

class WalkToHubNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val hubs = listOf(
            CoordGrid(3182, 3438, 0), // Varrock West Bank
            CoordGrid(3165, 3485, 0), // Grand Exchange
            CoordGrid(3212, 3218, 0)  // Lumbridge Castle courtyard
        )
        
        val nearestHub = hubs.minByOrNull { player.coords.chebyshevDistance(it) } ?: hubs[0]
        
        if (player.coords.chebyshevDistance(nearestHub) <= 4) {
            // Already at a hub, wander slightly
            val rx = Random.nextInt(-3, 4)
            val rz = Random.nextInt(-3, 4)
            player.walk(nearestHub.translate(rx, rz))
            return NodeStatus.SUCCESS
        }
        
        // Path to the hub
        player.routeRequest = RouteRequestCoord(
            destination = nearestHub,
            clientRequest = false
        )
        return NodeStatus.RUNNING
    }
}

class SpamBeggingNode : ActionNode() {
    private val phrases = listOf(
        "doubling coins 2 trades!",
        "buying gf 10k gp!",
        "please spare some gp, just got scammed for my bank :(",
        "can someone give me 100gp for an axe please?",
        "cyan:wave: doubling money! legit!",
        "selling rare burnt food 1k each",
        "dancing for gp!",
        "buying gf",
        "free gp please?"
    )

    override fun execute(player: Player, state: BotState): NodeStatus {
        // Only chat 15% of the ticks so it's not too spammy
        if (Random.nextInt(100) < 15) {
            val phrase = phrases.random()
            player.publicMessage = PublicMessage(
                text = phrase,
                colour = 0,
                effect = 0,
                clanType = null,
                modIcon = player.modLevel.clientCode,
                autoTyper = false,
                pattern = null
            )
        }
        return NodeStatus.SUCCESS
    }
}

class BeggarTreeBuilder {
    companion object {
        fun build(): BehaviorNode {
            return SequenceNode(listOf(
                WalkToHubNode(),
                SpamBeggingNode()
            ))
        }
    }
}
