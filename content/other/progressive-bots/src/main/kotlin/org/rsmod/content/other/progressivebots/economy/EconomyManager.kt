@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.progressivebots.economy

import dev.openrune.rscm.RSCM
import org.rsmod.api.player.stat.stat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.content.other.progressivebots.tree.ActionNode
import org.rsmod.content.other.progressivebots.tree.NodeStatus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

object EconomyManager {
    fun getUpgradeTarget(state: BotState, currentLevel: Int, skill: String): ToolUpgrade? {
        return null
    }

    fun sellInventoryGatheredItems(player: Player): Int {
        val inv = player.inv
        var gpGained = 0
        
        // Items a skiller bot might gather and sell
        val gatherableNames = setOf(
            "obj.logs", "obj.oak_logs", "obj.willow_logs", "obj.teak_logs", "obj.maple_logs", "obj.mahogany_logs", "obj.yew_logs", "obj.magic_logs",
            "obj.copper_ore", "obj.tin_ore", "obj.iron_ore", "obj.coal", "obj.mithril_ore", "obj.adamantite_ore", "obj.runite_ore",
            "obj.iron_bar", "obj.bronze_bar", "obj.steel_bar", "obj.mithril_bar", "obj.adamant_bar", "obj.rune_bar",
            "obj.bronze_dagger", "obj.iron_dagger", "obj.steel_dagger", "obj.mithril_dagger", "obj.adamant_dagger", "obj.rune_dagger"
        )
        
        val coinsId = ServerCacheManager.getItem("obj.coins".asRSCM(RSCMType.OBJ))?.id ?: 995
        
        for (i in inv.indices) {
            val obj = inv[i] ?: continue
            val objType = ServerCacheManager.getItem(obj.id) ?: continue
            
            val internalName = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
            val fullName = "obj.$internalName"
            
            if (fullName in gatherableNames) {
                val cost = objType.cost.coerceAtLeast(1)
                gpGained += cost * obj.count
                inv[i] = null
            }
        }
        
        if (gpGained > 0) {
            var added = false
            for (i in inv.indices) {
                val obj = inv[i]
                if (obj != null && obj.id == coinsId) {
                    inv[i] = InvObj(obj.id, obj.count + gpGained)
                    added = true
                    break
                }
            }
            if (!added) {
                val freeSlot = inv.indexOfFirst { it == null }
                if (freeSlot != -1) {
                    inv[freeSlot] = InvObj(coinsId, gpGained)
                }
            }
            inv.modifiedSlots.set(0, inv.size)
        }
        return gpGained
    }
    
    fun buyToolOrWeapon(player: Player, internalName: String): Boolean {
        val inv = player.inv
        val itemType = ServerCacheManager.getItem(internalName.asRSCM(RSCMType.OBJ)) ?: return false
        val cost = itemType.cost.coerceAtLeast(1)
        
        val coinsId = ServerCacheManager.getItem("obj.coins".asRSCM(RSCMType.OBJ))?.id ?: 995
        var coinsSlot = -1
        var coinsCount = 0
        for (i in inv.indices) {
            val obj = inv[i]
            if (obj != null && obj.id == coinsId) {
                coinsSlot = i
                coinsCount = obj.count
                break
            }
        }
        
        if (coinsCount < cost) return false
        
        if (coinsCount == cost) {
            inv[coinsSlot] = null
        } else {
            inv[coinsSlot] = InvObj(coinsId, coinsCount - cost)
        }
        
        val freeSlot = inv.indexOfFirst { it == null }
        if (freeSlot != -1) {
            inv[freeSlot] = InvObj(itemType.id, 1)
            inv.modifiedSlots.set(0, inv.size)
            return true
        }
        
        return false
    }
}

class ShopVisitNode : ActionNode() {
    override fun execute(player: Player, state: BotState): NodeStatus {
        val shops = listOf(
            CoordGrid(3211, 3245, 0), // Lumbridge General Store
            CoordGrid(3217, 3415, 0), // Varrock General Store
            CoordGrid(3165, 3485, 0)  // Grand Exchange
        )
        
        val nearestShop = shops.minByOrNull { player.coords.chebyshevDistance(it) } ?: shops[0]
        
        // If not near the shop, walk there first
        if (player.coords.chebyshevDistance(nearestShop) > 4) {
            player.routeRequest = org.rsmod.game.movement.RouteRequestCoord(
                destination = nearestShop,
                clientRequest = false
            )
            return NodeStatus.RUNNING
        }
        
        // Sell skilling loot first to get GP
        EconomyManager.sellInventoryGatheredItems(player)
        
        // Buy upgrade tool if needed
        val attackLevel = player.stat("attack")
        val wcLevel = player.stat("woodcutting")
        
        val bestAxe = ProgressionRegistry.getBestAxe(wcLevel)
        val bestWeapon = ProgressionRegistry.getBestWeapon(attackLevel)
        
        if ("obj.$bestAxe" !in player.inv) {
            EconomyManager.buyToolOrWeapon(player, "obj.$bestAxe")
        }
        
        if ("obj.$bestWeapon" !in player.inv) {
            EconomyManager.buyToolOrWeapon(player, "obj.$bestWeapon")
        }
        
        return NodeStatus.SUCCESS
    }
}
