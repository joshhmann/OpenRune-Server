package org.rsmod.content.other.progressivebots

import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry to prevent multiple progressive bots from clustering on the same
 * NPC or resource node (e.g. tree, rock).
 */
object ClaimRegistry {
    // Map of NPC slotId -> bot username claiming it
    private val npcClaims = ConcurrentHashMap<Int, String>()
    
    // Map of Coordinate (resource loc) -> bot username claiming it
    private val locClaims = ConcurrentHashMap<CoordGrid, String>()

    fun claimNpc(npc: Npc, botUsername: String): Boolean {
        val existingClaim = npcClaims.putIfAbsent(npc.slotId, botUsername)
        return existingClaim == null || existingClaim == botUsername
    }

    fun releaseNpc(npc: Npc, botUsername: String) {
        npcClaims.remove(npc.slotId, botUsername)
    }

    fun isNpcClaimedByOther(npc: Npc, botUsername: String): Boolean {
        val claim = npcClaims[npc.slotId]
        return claim != null && claim != botUsername
    }

    fun claimLoc(coords: CoordGrid, botUsername: String): Boolean {
        val existingClaim = locClaims.putIfAbsent(coords, botUsername)
        return existingClaim == null || existingClaim == botUsername
    }

    fun releaseLoc(coords: CoordGrid, botUsername: String) {
        locClaims.remove(coords, botUsername)
    }

    fun isLocClaimedByOther(coords: CoordGrid, botUsername: String): Boolean {
        val claim = locClaims[coords]
        return claim != null && claim != botUsername
    }
    
    fun releaseAll(botUsername: String) {
        npcClaims.entries.removeIf { it.value == botUsername }
        locClaims.entries.removeIf { it.value == botUsername }
    }
}
