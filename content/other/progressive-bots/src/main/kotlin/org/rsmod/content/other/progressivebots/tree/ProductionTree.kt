@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.progressivebots.tree

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.content.other.progressivebots.ClaimRegistry
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.game.movement.RouteRequestCoord

class HasOresNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val hasCopper = "obj.copper_ore" in player.inv
        val hasTin = "obj.tin_ore" in player.inv
        val hasIron = "obj.iron_ore" in player.inv
        
        return if ((hasCopper && hasTin) || hasIron) {
            NodeStatus.SUCCESS
        } else {
            NodeStatus.FAILURE
        }
    }
}

class HasBarsNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val hasBronze = "obj.bronze_bar" in player.inv
        val hasIron = "obj.iron_bar" in player.inv
        return if (hasBronze || hasIron) NodeStatus.SUCCESS else NodeStatus.FAILURE
    }
}

class SmeltOresNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val locReg = state.locRegistry ?: return NodeStatus.FAILURE
        val pZone = ZoneKey.from(player.coords)
        
        var nearestFurnace: CoordGrid? = null
        var minDistance = Int.MAX_VALUE
        
        // Scan 3x3 zones for furnace
        for (x in -1..1) {
            for (z in -1..1) {
                val zKey = ZoneKey(pZone.x + x, pZone.z + z, pZone.level)
                val locs = locReg.findAll(zKey)
                for (loc in locs) {
                    val type = ServerCacheManager.getObject(loc.id) ?: continue
                    if (type.name.lowercase().contains("furnace")) {
                        val dist = player.coords.chebyshevDistance(loc.coords)
                        if (dist < minDistance) {
                            minDistance = dist
                            nearestFurnace = loc.coords
                        }
                    }
                }
            }
        }
        
        if (nearestFurnace == null) {
            // Fallback: if no furnace found, just perform simulated instant smelting
            return smeltInstant(player)
        }
        
        // Check if we are near the furnace (within 2 tiles)
        if (player.coords.chebyshevDistance(nearestFurnace) <= 2) {
            return smeltInstant(player)
        }
        
        // Path to the furnace
        player.routeRequest = RouteRequestCoord(
            destination = nearestFurnace,
            clientRequest = false
        )
        return NodeStatus.RUNNING
    }

    private fun smeltInstant(player: Player): NodeStatus {
        val inv = player.inv
        val copperId = ServerCacheManager.getItem("obj.copper_ore".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val tinId = ServerCacheManager.getItem("obj.tin_ore".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val ironId = ServerCacheManager.getItem("obj.iron_ore".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val bronzeBarId = ServerCacheManager.getItem("obj.bronze_bar".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val ironBarId = ServerCacheManager.getItem("obj.iron_bar".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE

        var smelted = false
        val ironCount = inv.count { it?.id == ironId }
        
        if (ironCount > 0) {
            for (i in inv.indices) {
                if (inv[i]?.id == ironId) inv[i] = null
            }
            var added = 0
            for (i in inv.indices) {
                if (inv[i] == null && added < ironCount) {
                    inv[i] = InvObj(ironBarId, 1)
                    added++
                }
            }
            smelted = true
        } else {
            val copperCount = inv.count { it?.id == copperId }
            val tinCount = inv.count { it?.id == tinId }
            val pairs = minOf(copperCount, tinCount)
            
            if (pairs > 0) {
                var removedCopper = 0
                var removedTin = 0
                for (i in inv.indices) {
                    if (inv[i]?.id == copperId && removedCopper < pairs) {
                        inv[i] = null
                        removedCopper++
                    }
                    if (inv[i]?.id == tinId && removedTin < pairs) {
                        inv[i] = null
                        removedTin++
                    }
                }
                var added = 0
                for (i in inv.indices) {
                    if (inv[i] == null && added < pairs) {
                        inv[i] = InvObj(bronzeBarId, 1)
                        added++
                    }
                }
                smelted = true
            }
        }

        return if (smelted) {
            inv.modifiedSlots.set(0, inv.size)
            NodeStatus.SUCCESS
        } else {
            NodeStatus.FAILURE
        }
    }
}

class SmithBarsNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val locReg = state.locRegistry ?: return NodeStatus.FAILURE
        val pZone = ZoneKey.from(player.coords)
        
        var nearestAnvil: CoordGrid? = null
        var minDistance = Int.MAX_VALUE
        
        // Scan 3x3 zones for anvil
        for (x in -1..1) {
            for (z in -1..1) {
                val zKey = ZoneKey(pZone.x + x, pZone.z + z, pZone.level)
                val locs = locReg.findAll(zKey)
                for (loc in locs) {
                    val type = ServerCacheManager.getObject(loc.id) ?: continue
                    if (type.name.lowercase().contains("anvil")) {
                        val dist = player.coords.chebyshevDistance(loc.coords)
                        if (dist < minDistance) {
                            minDistance = dist
                            nearestAnvil = loc.coords
                        }
                    }
                }
            }
        }
        
        if (nearestAnvil == null) {
            return smithInstant(player)
        }
        
        if (player.coords.chebyshevDistance(nearestAnvil) <= 2) {
            return smithInstant(player)
        }
        
        player.routeRequest = RouteRequestCoord(
            destination = nearestAnvil,
            clientRequest = false
        )
        return NodeStatus.RUNNING
    }

    private fun smithInstant(player: Player): NodeStatus {
        val inv = player.inv
        val bronzeBarId = ServerCacheManager.getItem("obj.bronze_bar".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val ironBarId = ServerCacheManager.getItem("obj.iron_bar".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val bronzeDaggerId = ServerCacheManager.getItem("obj.bronze_dagger".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        val ironDaggerId = ServerCacheManager.getItem("obj.iron_dagger".asRSCM(RSCMType.OBJ))?.id ?: return NodeStatus.FAILURE
        
        var smithed = false
        val ironBars = inv.count { it?.id == ironBarId }
        
        if (ironBars > 0) {
            for (i in inv.indices) {
                if (inv[i]?.id == ironBarId) inv[i] = InvObj(ironDaggerId, 1)
            }
            smithed = true
        } else {
            val bronzeBars = inv.count { it?.id == bronzeBarId }
            if (bronzeBars > 0) {
                for (i in inv.indices) {
                    if (inv[i]?.id == bronzeBarId) inv[i] = InvObj(bronzeDaggerId, 1)
                }
                smithed = true
            }
        }
        
        return if (smithed) {
            inv.modifiedSlots.set(0, inv.size)
            NodeStatus.SUCCESS
        } else {
            NodeStatus.FAILURE
        }
    }
}

class ProductionTreeBuilder {
    companion object {
        fun build(): BehaviorNode {
            return SelectorNode(listOf(
                SequenceNode(listOf(
                    HasBarsNode(),
                    SmithBarsNode()
                )),
                SequenceNode(listOf(
                    HasOresNode(),
                    SmeltOresNode()
                )),
                GatherTreeBuilder.build("mining")
            ))
        }
    }
}
