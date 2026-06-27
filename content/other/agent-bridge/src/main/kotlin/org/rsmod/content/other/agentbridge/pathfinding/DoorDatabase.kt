package org.rsmod.content.other.agentbridge.pathfinding

/**
 * Database of doors, gates, and other openable obstacles that may block paths. These are doors that
 * are initially closed and need to be opened.
 *
 * Note: This is a seed list - a full implementation would scan the cache for all locs with "Open"
 * options at runtime.
 */
object DoorDatabase {
    /**
     * One-way doors that should NOT be routed through. These can only be opened from one side;
     * entering traps the player.
     */
    val ONE_WAY_DOORS: Set<DoorKey> =
        setOf(
            // Draynor Manor front doors - only open from outside
            DoorKey(0, 3108, 3353),
            DoorKey(0, 3109, 3353),
        )

    /** Common doors in the F2P area. A production system would load this from cache scanning. */
    val COMMON_DOORS: Map<DoorKey, DoorInfo> = buildMap {
        // Lumbridge
        put(DoorKey(0, 3213, 3216), DoorInfo(0, 3213, 3216, "Door", isGate = false))
        put(DoorKey(0, 3212, 3215), DoorInfo(0, 3212, 3215, "Door", isGate = false))

        // Varrock
        put(DoorKey(0, 3217, 3430), DoorInfo(0, 3217, 3430, "Large door", isGate = true))
        put(DoorKey(0, 3218, 3430), DoorInfo(0, 3218, 3430, "Large door", isGate = true))

        // Falador
        put(DoorKey(0, 2961, 3382), DoorInfo(0, 2961, 3382, "Gate", isGate = true))
        put(DoorKey(0, 2962, 3382), DoorInfo(0, 2962, 3382, "Gate", isGate = true))

        // Al Kharid
        put(DoorKey(0, 3267, 3227), DoorInfo(0, 3267, 3227, "Gate", isGate = true))
        put(DoorKey(0, 3268, 3227), DoorInfo(0, 3268, 3227, "Gate", isGate = true))
    }

    /** Check if a door is one-way (should not be routed through). */
    fun isOneWayDoor(level: Int, x: Int, z: Int): Boolean =
        ONE_WAY_DOORS.contains(DoorKey(level, x, z))

    /** Get door info if this location is a known door. */
    fun getDoor(level: Int, x: Int, z: Int): DoorInfo? = COMMON_DOORS[DoorKey(level, x, z)]

    /** Find doors near a path's waypoints. */
    fun findDoorsAlongPath(waypoints: List<Waypoint>): List<DoorInfo> {
        val doors = mutableListOf<DoorInfo>()
        val seen = mutableSetOf<DoorKey>()

        for (wp in waypoints) {
            // Check the waypoint and its 4 cardinal neighbors
            val candidates =
                listOf(
                    DoorKey(wp.level, wp.x, wp.z),
                    DoorKey(wp.level, wp.x, wp.z + 1),
                    DoorKey(wp.level, wp.x, wp.z - 1),
                    DoorKey(wp.level, wp.x + 1, wp.z),
                    DoorKey(wp.level, wp.x - 1, wp.z),
                )

            for (key in candidates) {
                if (!seen.contains(key)) {
                    seen.add(key)
                    val door = COMMON_DOORS[key]
                    if (door != null && !ONE_WAY_DOORS.contains(key)) {
                        doors.add(door)
                    }
                }
            }
        }

        return doors
    }
}

/** Key for door lookup. */
data class DoorKey(val level: Int, val x: Int, val z: Int)

/** Information about a door or gate. */
data class DoorInfo(
    val level: Int,
    val x: Int,
    val z: Int,
    val name: String,
    val isGate: Boolean,
    /** Whether the door blocks line-of-sight for ranged attacks. */
    val blocksRange: Boolean = false,
)
