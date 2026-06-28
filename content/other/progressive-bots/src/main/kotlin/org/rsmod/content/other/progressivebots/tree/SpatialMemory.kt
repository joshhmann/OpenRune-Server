package org.rsmod.content.other.progressivebots.tree

import org.rsmod.map.CoordGrid

data class MemoryNode(val type: String, val coords: CoordGrid, var depletedUntil: Int = 0)

class SpatialMemory {
    private val nodes = mutableMapOf<CoordGrid, MemoryNode>()

    fun registerNode(coords: CoordGrid, type: String) {
        if (!nodes.containsKey(coords)) {
            nodes[coords] = MemoryNode(type, coords)
        }
    }

    fun markDepleted(coords: CoordGrid, durationTicks: Int, currentTick: Int) {
        val node = nodes[coords]
        if (node != null) {
            node.depletedUntil = currentTick + durationTicks
        }
    }

    fun isDepleted(coords: CoordGrid, currentTick: Int): Boolean {
        val node = nodes[coords] ?: return false
        return currentTick < node.depletedUntil
    }

    fun findNearestActiveNode(
        botCoords: CoordGrid,
        type: String,
        currentTick: Int,
        maxDistance: Int = 30,
    ): MemoryNode? {
        var closest: MemoryNode? = null
        var minDistance = maxDistance

        for ((coords, node) in nodes) {
            if (node.type != type || isDepleted(coords, currentTick)) continue

            if (coords.level != botCoords.level) continue
            val dist = botCoords.chebyshevDistance(coords)
            if (dist < minDistance) {
                minDistance = dist
                closest = node
            }
        }
        return closest
    }
}
