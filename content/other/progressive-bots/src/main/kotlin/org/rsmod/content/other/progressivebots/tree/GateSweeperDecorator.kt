package org.rsmod.content.other.progressivebots.tree

import dev.openrune.ServerCacheManager
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.content.other.progressivebots.BotState
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.map.zone.ZoneKey

/**
 * Universal background behavior node to automatically sweep and open gates/doors within a 3-tile
 * radius if they are closed. This runs alongside the main goal to prevent pathing getting
 * permanently blocked.
 */
class GateSweeperDecorator(private val child: BehaviorNode, private val radius: Int = 3) :
    BehaviorNode {

    private var sweepCounter = kotlin.random.Random.nextInt(0, 5)

    override fun execute(player: Player, state: BotState): NodeStatus {
        sweepCounter++
        if (sweepCounter >= 3) {
            sweepCounter = 0
            sweepForGates(player, state)
        }
        return child.execute(player, state)
    }

    private fun sweepForGates(player: Player, state: BotState) {
        val locReg = state.locRegistry ?: return
        val eventBus = state.eventBus ?: return
        val pZone = ZoneKey.from(player.coords)

        // Scan the player's zone and the 8 surrounding zones
        for (x in -1..1) {
            for (z in -1..1) {
                val zKey = ZoneKey(pZone.x + x, pZone.z + z, pZone.level)
                val locs = locReg.findAll(zKey)

                for (loc in locs) {
                    val dist = player.coords.chebyshevDistance(loc.coords)
                    if (dist > radius) continue

                    val type = ServerCacheManager.getObject(loc.id) ?: continue

                    // Fast check without string allocations
                    val op1 = type.actions.getOpOrNull(0) ?: continue
                    if (op1.equals("Open", ignoreCase = true)) {
                        val name = type.name
                        if (
                            name.contains("door", ignoreCase = true) ||
                                name.contains("gate", ignoreCase = true)
                        ) {
                            // Replicate OpLocHandler to open the door
                            player.clearPendingAction(eventBus)
                            player.resetFaceEntity()
                            player.faceLoc(loc, type.width, type.length)

                            val boundLoc = BoundLocInfo(loc, type)
                            player.interaction =
                                InteractionLocOp(
                                    target = boundLoc,
                                    op = InteractionOp.Op1,
                                    hasOpTrigger = true,
                                    hasApTrigger = true,
                                )

                            player.routeRequest =
                                RouteRequestLoc(
                                    destination = loc.coords,
                                    width = type.width,
                                    length = type.length,
                                    shape = loc.shapeId,
                                    angle = loc.angleId,
                                    forceApproachFlags = type.forceApproachFlags,
                                    clientRequest = false,
                                )
                            return // only open one door per sweep
                        }
                    }
                }
            }
        }
    }
}
