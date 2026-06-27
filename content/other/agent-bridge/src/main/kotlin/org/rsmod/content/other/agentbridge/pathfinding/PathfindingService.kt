package org.rsmod.content.other.agentbridge.pathfinding

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.route.RouteFactory
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

/**
 * Pathfinding service for AgentBridge. Provides long-distance pathfinding and door detection using
 * RSMod's RouteFinding.
 */
@Singleton
class PathfindingService
@Inject
constructor(private val routeFactory: RouteFactory, private val collisionFlags: CollisionFlagMap) {
    /**
     * Find a path from player's current position to destination. Returns waypoints and whether the
     * destination was reached.
     */
    fun findPath(
        player: Player,
        destX: Int,
        destZ: Int,
        destPlane: Int = player.coords.level,
        maxWaypoints: Int = 500,
    ): PathResult {
        val route =
            routeFactory.create(
                source = player.avatar,
                destination = CoordGrid(destX, destZ, destPlane),
                collision = CollisionStrategy.Normal,
            )

        val waypoints = route.waypoints.map { coord -> Waypoint(coord.x, coord.z, coord.level) }

        // Check if we reached the destination
        val lastWaypoint = waypoints.lastOrNull()
        val reachedDestination =
            lastWaypoint != null &&
                lastWaypoint.x == destX &&
                lastWaypoint.z == destZ &&
                lastWaypoint.level == destPlane

        return PathResult(
            success = waypoints.isNotEmpty() || reachedDestination,
            waypoints = waypoints,
            reachedDestination = reachedDestination,
            truncated = waypoints.size >= maxWaypoints,
        )
    }

    /** Check if a tile is walkable (no blocking collision flags). */
    fun isTileWalkable(level: Int, x: Int, z: Int): Boolean {
        val flags = collisionFlags[level, x, z]
        return (flags and WALK_BLOCKED) == 0
    }

    /** Get collision flags for a tile. */
    fun getCollisionFlags(level: Int, x: Int, z: Int): Int {
        return collisionFlags[level, x, z]
    }

    /** Calculate Chebyshev distance between two points. */
    fun distance(x1: Int, z1: Int, x2: Int, z2: Int): Int {
        val dx = maxOf(x1, x2) - minOf(x1, x2)
        val dz = maxOf(z1, z2) - minOf(z1, z2)
        return maxOf(dx, dz)
    }

    companion object {
        /** Collision flag: tile is not walkable. */
        private const val WALK_BLOCKED = 0x1
    }
}

/** Result of a pathfinding query. */
data class PathResult(
    /** Whether a path was found (may be partial). */
    val success: Boolean,
    /** Waypoints from source to destination (or as far as could be reached). */
    val waypoints: List<Waypoint>,
    /** Whether the final waypoint is the requested destination. */
    val reachedDestination: Boolean,
    /** Whether the path was truncated due to waypoint limit. */
    val truncated: Boolean,
)

/** A single waypoint in a path. */
data class Waypoint(val x: Int, val z: Int, val level: Int)
