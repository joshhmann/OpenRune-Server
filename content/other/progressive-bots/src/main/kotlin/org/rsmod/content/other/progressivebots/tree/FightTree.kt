@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.progressivebots.tree

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

import dev.openrune.ServerCacheManager
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.content.other.progressivebots.ClaimRegistry
import org.rsmod.content.other.progressivebots.economy.ProgressionRegistry
import org.rsmod.game.entity.Player
import org.rsmod.api.player.stat.stat
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class HasWeaponNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val level = player.stat("attack")
        val bestWeapon = ProgressionRegistry.getBestWeapon(level)
        
        if ("obj.$bestWeapon" in player.inv) {
            return NodeStatus.SUCCESS
        }
        // Ideally we would also check equipment here.
        return NodeStatus.FAILURE
    }
}

class GetWeaponNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val attackLevel = player.stat("attack")
        val bestWeapon = ProgressionRegistry.getBestWeapon(attackLevel)
        
        // Attempt to buy the best weapon using GP
        val bought = org.rsmod.content.other.progressivebots.economy.EconomyManager.buyToolOrWeapon(player, "obj.$bestWeapon")
        if (bought) {
            return NodeStatus.SUCCESS
        }
        
        // If we fail because of GP, spawn a bronze sword for free
        val fallback = "bronze_sword"
        if ("obj.$fallback" !in player.inv) {
            val toolId = ServerCacheManager.getItem("obj.$fallback".asRSCM(dev.openrune.rscm.RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
            val freeSlot = player.inv.indexOfFirst { it == null }
            if (freeSlot != -1) {
                player.inv[freeSlot] = org.rsmod.game.inv.InvObj(toolId, 1)
                player.inv.modifiedSlots.set(0, player.inv.size)
                return NodeStatus.SUCCESS
            }
        }
        
        return NodeStatus.FAILURE
    }
}

class FindNPCNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val combatLevel = 3 // Assuming level 3 for now
        val targetName = ProgressionRegistry.getBestNPCTarget(combatLevel)
        
        val npcList = state.npcList ?: return NodeStatus.FAILURE
        
        var bestCoords: CoordGrid? = null
        var bestId: Int = -1
        var minDistance = Int.MAX_VALUE

        // Iterate through all NPCs to find closest match
        for (npc in npcList) {
            val type = ServerCacheManager.getNpc(npc.id) ?: continue
            val npcName = type.name.lowercase().replace(" ", "_")
            
            if (npcName == targetName) {
                // Check if claimed
                if (ClaimRegistry.isLocClaimedByOther(npc.coords, player.avatar.name)) {
                    continue
                }
                
                val dist = player.coords.chebyshevDistance(npc.coords)
                if (dist < minDistance && dist < 20) { // Limit search radius
                    minDistance = dist
                    bestCoords = npc.coords
                    bestId = npc.id
                }
            }
        }

        if (bestCoords != null) {
            state.goalStack.destinationX = bestCoords.x
            state.goalStack.destinationZ = bestCoords.z
            // For NPCs, it's better to store the entity index, but we'll re-scan for simplicity
            ClaimRegistry.claimLoc(bestCoords, player.avatar.name)
            return NodeStatus.SUCCESS
        }

        return NodeStatus.FAILURE
    }
}

class InteractNPCNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val destX = state.goalStack.destinationX ?: return NodeStatus.FAILURE
        val destZ = state.goalStack.destinationZ ?: return NodeStatus.FAILURE
        val coords = CoordGrid(destX, destZ, player.level)
        
        val npcList = state.npcList ?: return NodeStatus.FAILURE
        val eventBus = state.eventBus ?: return NodeStatus.FAILURE
        
        // Find the NPC at the destination
        val npc = npcList.firstOrNull { it.coords == coords } ?: return NodeStatus.FAILURE
        val type = ServerCacheManager.getNpc(npc.id) ?: return NodeStatus.FAILURE
        
        // We replicate OpNpcHandler behavior to engage OpenRune's engine
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceNpc(npc)
        
        // Assume Op1 is the default interaction (e.g. "Attack")
        player.interaction = InteractionNpcOp(
            target = npc,
            op = InteractionOp.Op1,
            hasOpTrigger = true, // We assume true for now
            hasApTrigger = true
        )
        
        player.routeRequest = RouteRequestPathingEntity(
            destination = npc.avatar,
            clientRequest = false
        )
        
        return NodeStatus.RUNNING // Stays running while fighting
    }
}


class FightTreeBuilder {
    companion object {
        fun build(): BehaviorNode {
            return SelectorNode(listOf(
                SequenceNode(listOf(
                    InvertNode(HasWeaponNode()),
                    GetWeaponNode()
                )),
                SequenceNode(listOf(
                    FindNPCNode(),
                    InteractNPCNode()
                ))
            ))
        }
    }
}
