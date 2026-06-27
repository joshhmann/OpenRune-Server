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
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

class HasToolNode(private val skill: String) : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val level = player.stat(skill)
        val bestTool = when (skill) {
            "woodcutting" -> ProgressionRegistry.getBestAxe(level)
            "mining" -> ProgressionRegistry.getBestPickaxe(level)
            else -> return NodeStatus.FAILURE
        }
        
        // Check player inventory
        if ("obj.$bestTool" in player.inv) {
            return NodeStatus.SUCCESS
        }
        // Ideally we would also check equipment here.
        
        return NodeStatus.FAILURE
    }
}

class GetToolNode(private val skill: String) : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val level = player.stat(skill)
        val bestTool = when (skill) {
            "woodcutting" -> ProgressionRegistry.getBestAxe(level)
            "mining" -> ProgressionRegistry.getBestPickaxe(level)
            else -> return NodeStatus.FAILURE
        }

        // Attempt to buy the best tool using GP
        val bought = org.rsmod.content.other.progressivebots.economy.EconomyManager.buyToolOrWeapon(player, "obj.$bestTool")
        if (bought) {
            return NodeStatus.SUCCESS
        }
        
        // If we fail because of GP, spawn the basic level 1 bronze tool for free so we don't get soft-locked
        val fallbackTool = if (skill == "woodcutting") "bronze_axe" else "bronze_pickaxe"
        if ("obj.$fallbackTool" !in player.inv) {
            val coinsId = ServerCacheManager.getItem("obj.coins".asRSCM(dev.openrune.rscm.RSCMType.OBJ))?.id ?: 995
            val toolId = ServerCacheManager.getItem("obj.$fallbackTool".asRSCM(dev.openrune.rscm.RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
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

class IsInventoryFullNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        return if (player.inv.isFull()) NodeStatus.SUCCESS else NodeStatus.FAILURE
    }
}

class BankLootNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        // Simulating banking by clearing the inventory of all gatherable items
        // We will just clear it entirely except for our tools
        player.inv.fillNulls()
        return NodeStatus.SUCCESS
    }
}

class FindTargetNode(private val skill: String) : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val level = player.stat(skill)
        val targetName = when (skill) {
            "woodcutting" -> ProgressionRegistry.getBestWoodcuttingTarget(level)
            "mining" -> ProgressionRegistry.getBestMiningTarget(level)
            else -> return NodeStatus.FAILURE
        }

        val locReg = state.locRegistry ?: return NodeStatus.FAILURE
        val pZone = ZoneKey.from(player.coords)

        var bestCoords: CoordGrid? = null
        var bestId: Int = -1
        var minDistance = Int.MAX_VALUE

        // Scan the player's zone and the 8 surrounding zones
        for (x in -1..1) {
            for (z in -1..1) {
                val zKey = ZoneKey(pZone.x + x, pZone.z + z, pZone.level)
                val locs = locReg.findAll(zKey)
                
                for (loc in locs) {
                    val type = ServerCacheManager.getObject(loc.id) ?: continue
                    
                    // Simple name match (in reality, we might match ids directly from Registry)
                    // We assume targetName is lowercased, like "oak_tree" or "tree"
                    val locName = type.name.lowercase().replace(" ", "_")
                    
                    if (locName == targetName) {
                        // Check if claimed
                        if (ClaimRegistry.isLocClaimedByOther(loc.coords, player.avatar.name)) {
                            continue
                        }
                        
                        val dist = player.coords.chebyshevDistance(loc.coords)
                        if (dist < minDistance) {
                            minDistance = dist
                            bestCoords = loc.coords
                            bestId = loc.entity.id
                        }
                    }
                }
            }
        }

        if (bestCoords != null) {
            state.goalStack.destinationX = bestCoords.x
            state.goalStack.destinationZ = bestCoords.z
            // We use a custom field in GoalStack for locId if needed, or just let InteractTargetNode rescan
            // Actually, we should store locId. Let's just find it again in InteractTargetNode since it's fast
            ClaimRegistry.claimLoc(bestCoords, player.avatar.name)
            return NodeStatus.SUCCESS
        }

        return NodeStatus.FAILURE
    }
}

class InteractTargetNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val destX = state.goalStack.destinationX ?: return NodeStatus.FAILURE
        val destZ = state.goalStack.destinationZ ?: return NodeStatus.FAILURE
        val coords = CoordGrid(destX, destZ, player.level)
        
        val locReg = state.locRegistry ?: return NodeStatus.FAILURE
        val eventBus = state.eventBus ?: return NodeStatus.FAILURE
        
        // Find any valid loc at the destination
        val loc = locReg.findAll(ZoneKey.from(coords)).firstOrNull { it.coords == coords } ?: return NodeStatus.FAILURE
        val type = ServerCacheManager.getObject(loc.id) ?: return NodeStatus.FAILURE
        
        // We replicate OpLocHandler behavior to engage OpenRune's engine
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceLoc(loc, type.width, type.length)
        
        val boundLoc = BoundLocInfo(loc, type)
        // Assume Op1 is the default interaction (e.g. "Chop down")
        player.interaction = InteractionLocOp(
            target = boundLoc,
            op = InteractionOp.Op1,
            hasOpTrigger = true,
            hasApTrigger = true
        )
        
        player.routeRequest = RouteRequestLoc(
            destination = coords,
            width = type.width,
            length = type.length,
            shape = loc.shapeId,
            angle = loc.angleId,
            forceApproachFlags = type.forceApproachFlags,
            clientRequest = false
        )
        
        return NodeStatus.RUNNING // It stays running while the engine pathfinds and animates!
    }
}

class GatherTreeBuilder {
    companion object {
        fun build(skill: String): BehaviorNode {
            return SelectorNode(listOf(
                // 1. If we don't have the required tool, get it
                SequenceNode(listOf(
                    InvertNode(HasToolNode(skill)),
                    GetToolNode(skill)
                )),
                
                // 2. If inventory is full, bank the loot
                SequenceNode(listOf(
                    IsInventoryFullNode(),
                    BankLootNode()
                )),
                
                // 3. Otherwise, find a target and gather
                SequenceNode(listOf(
                    FindTargetNode(skill),
                    InteractTargetNode()
                ))
            ))
        }
    }
}
