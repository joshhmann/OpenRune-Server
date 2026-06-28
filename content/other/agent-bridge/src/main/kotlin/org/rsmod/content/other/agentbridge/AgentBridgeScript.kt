package org.rsmod.content.other.agentbridge

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.other.agentbridge.banking.BankPorcelain
import org.rsmod.content.other.agentbridge.grounditems.GroundItemPorcelain
import org.rsmod.content.other.agentbridge.ironman.IronmanMode
import org.rsmod.content.other.agentbridge.pathfinding.DoorDatabase
import org.rsmod.content.other.agentbridge.pathfinding.PathfindingService
import org.rsmod.content.other.agentbridge.porcelain.BotPorcelain
import org.rsmod.content.other.agentbridge.prayer.PrayerPorcelain
import org.rsmod.content.other.agentbridge.shops.ShopPorcelain
import org.rsmod.events.EventBus
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Drives the AgentBridge per-tick loop:
 * 1. Dequeues any pending bot actions and executes them on the game thread.
 * 2. Broadcasts the player's state snapshot to all connected agent clients.
 * 3. Tracks events (animations, XP gains, inventory changes) for push-based notifications.
 *
 * Pattern mirrors MapClockScript, but only for AgentBridge-owned bots. Human players and ambient
 * playerbots are outside the AgentBridge control lane.
 */
class AgentBridgeScript
@Inject
constructor(
    private val server: AgentBridgeServer,
    private val hunt: Hunt,
    private val eventBus: EventBus,
    private val locRegistry: LocRegistry,
    private val locInteractions: LocInteractions,
    private val npcInteractions: NpcInteractions,
    private val heldUInteractions: HeldUInteractions,
    private val npcList: NpcList,
    private val protectedAccess: ProtectedAccessLauncher,
    private val pathfinding: PathfindingService,
    private val botPorcelain: BotPorcelain,
    private val bankPorcelain: BankPorcelain,
    private val shopPorcelain: ShopPorcelain,
    private val groundItemPorcelain: GroundItemPorcelain,
    private val prayerPorcelain: PrayerPorcelain,
    private val ironmanMode: IronmanMode,
    private val agentBotService: AgentBotService,
) : PluginScript() {
    private val logger = InlineLogger()

    /** Per-player state tracking for event detection. */
    private val playerStates = ConcurrentHashMap<String, PlayerStateTracker>()

    /** Per-player pending waits that are being tracked. */
    private val pendingWaits = ConcurrentHashMap<String, PendingWait>()

    /** Pending wait result per player - set when wait completes. */
    private val waitResults = ConcurrentHashMap<String, WaitResult>()

    /** Per-player pending door operations. */
    private val pendingDoorOps = ConcurrentHashMap<String, PendingDoorOp>()

    /** Set of blocked door locations (for locked doors). */
    private val blockedDoors = ConcurrentHashMap.newKeySet<DoorKey>()

    override fun ScriptContext.startup() {
        val config = AgentBridgeConfig.config
        if (!config.enabled) {
            logger.info { "[AgentBridge] Disabled via config" }
            return
        }

        server.start()
        eventBus.subscribeUnbound(GameLifecycle.LateCycle::class.java) { processSystemActions() }
        onPlayerSoftTimer(AGENT_BRIDGE_TIMER) {
            server.ensureClientTap(player)

            val playerKey = player.avatar.name.lowercase()
            val prevState = playerStates[playerKey]
            val nearbyNpcs = buildNearbyNpcList(player)
            val currentState = PlayerStateTracker.from(player, nearbyNpcs)
            val combatState = currentState.combatState
            val currentTick = server.getCurrentTick()

            // Detect events by comparing states
            val events =
                if (prevState != null) {
                    detectEvents(prevState, currentState, currentTick)
                } else {
                    emptyList()
                }

            // Store current state for next tick
            playerStates[playerKey] = currentState

            // Process any pending waits
            val waitResult = processPendingWaits(player, playerKey, currentTick, currentState)

            // Build filtered nearby entity lists
            val nearbyLocs = buildNearbyLocList(player)

            // Process any pending door operations (needs nearbyLocs)
            val doorEvents = processPendingDoorOps(player, playerKey, currentTick, nearbyLocs)

            // Combine events
            val allEvents = events + doorEvents

            // Execute any pending actions (unless we're waiting or doing door ops)
            var actionResult: org.rsmod.content.other.agentbridge.ActionResult? = null
            val action = server.pollAction(player.avatar.name)
            if (action != null) {
                System.err.println(
                    "[AgentBridge] Polled action: ${action::class.simpleName} for '${player.avatar.name}'"
                )
            }
            if (action != null && pendingDoorOps[playerKey] == null) {
                // Check ironman mode enforcement
                actionResult =
                    if (ironmanMode.isEnabled && !ironmanMode.isActionAllowed(action)) {
                        ActionResult(
                            false,
                            ironmanMode.getBlockedMessage(action),
                            captureXpSnapshot(player),
                            captureXpSnapshot(player),
                        )
                    } else {
                        executeAction(player, action)
                    }
            }

            // Broadcast state with events and wait result
            server.broadcast(
                player,
                nearbyNpcs,
                nearbyLocs,
                combatState,
                allEvents,
                actionResult,
                waitResult?.let {
                    org.rsmod.content.other.agentbridge.WaitResult(
                        it.success,
                        it.message,
                        it.waitedTicks,
                    )
                },
            )
        }
    }

    /** Process system-level actions (spawn_bot, despawn_bot) on the game thread. */
    private fun processSystemActions() {
        try {
            while (true) {
                val action = server.pollSystemAction() ?: break
                when (action) {
                    is BotAction.SpawnBot -> {
                        val bot = agentBotService.spawn(action.name, action.x, action.z)
                        if (bot != null) {
                            server.registerBotPlayer(bot)
                            System.err.println("[AgentBridge] System spawned bot " + action.name)
                        }
                    }
                    is BotAction.DespawnBot -> {
                        val playerName_lc = action.name.lowercase()
                        // Clean up AgentBridge state BEFORE removing from player list
                        val bot = agentBotService.find(playerName_lc)
                        if (bot != null) {
                            playerStates.remove(playerName_lc)
                            pendingWaits.remove(playerName_lc)
                            waitResults.remove(playerName_lc)
                            pendingDoorOps.remove(playerName_lc)
                        }
                        val removed = agentBotService.despawn(playerName_lc)
                        if (removed) {
                            System.err.println("[AgentBridge] System despawned bot " + action.name)
                        }
                    }
                    is BotAction.ListBots -> {
                        System.err.println(
                            "[AgentBridge] Agent bot count: " + agentBotService.count()
                        )
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            System.err.println("[AgentBridge] System action error: ${e.message}")
        }
    }

    companion object {
        /** Chebyshev tile radius for nearby entity scanning. */
        private const val SCAN_RADIUS = 16

        /** Maximum number of NPCs to include in state. */
        private const val MAX_NEARBY_NPCS = 20

        /** Maximum number of objects to include in state. */
        private const val MAX_NEARBY_LOCS = 20

        /** Minimum distance to include an object (skip very close "null" objects). */
        private const val MIN_LOC_DISTANCE = 0

        /**
         * How many ticks to wait before considering a wait timed out (timeoutMs / 600ms per tick).
         */
        private fun ticksFromMs(ms: Int): Int = (ms / 600).coerceAtLeast(1)
    }

    /** Tracks a pending wait operation for a player. */
    private data class PendingWait(
        val waitType: String,
        val startTick: Int,
        val maxTicks: Int,
        val params: Map<String, Any>,
    )

    /** Result of a completed wait. */
    private data class WaitResult(val success: Boolean, val message: String, val waitedTicks: Int)

    /** Tracks a pending door operation. */
    private data class PendingDoorOp(
        val x: Int,
        val z: Int,
        val plane: Int,
        val startTick: Int,
        val maxTicks: Int,
        val stage: DoorOpStage,
        val locId: Int = -1,
    )

    /** Stage of door operation. */
    private enum class DoorOpStage {
        WALK_ADJACENT,
        OPEN_DOOR,
        WAIT_FOR_OPEN,
    }

    /** Key for door lookup. */
    private data class DoorKey(val level: Int, val x: Int, val z: Int)

    private fun buildNearbyNpcList(player: Player): List<NearbyNpcSnapshot> {
        return hunt
            .findNpcs(player.coords, SCAN_RADIUS, HuntVis.Off)
            .map { npc ->
                val dx = max(npc.coords.x, player.coords.x) - minOf(npc.coords.x, player.coords.x)
                val dz = max(npc.coords.z, player.coords.z) - minOf(npc.coords.z, player.coords.z)
                val dist = max(dx, dz)

                NearbyNpcSnapshot(
                    id = npc.type.id,
                    index = npc.slotId,
                    name = npc.name,
                    x = npc.coords.x,
                    z = npc.coords.z,
                    distance = dist,
                    animation = npc.pendingSequence.id,
                    hp = npc.hitpoints,
                    maxHp = npc.type.hitpoints,
                    healthPercent =
                        if (npc.type.hitpoints > 0) {
                            ((npc.hitpoints * 100) / npc.type.hitpoints).coerceIn(0, 100)
                        } else {
                            null
                        },
                    inCombat = npc.isInCombat(),
                    combatCycle = npc.vars["varn.lastattack"],
                    targetIndex =
                        when {
                            npc.faceEntity.isNpc -> npc.faceEntity.npcSlot
                            npc.faceEntity.isPlayer -> npc.faceEntity.playerSlot
                            else -> -1
                        },
                )
            }
            .filter { it.name.isNotBlank() && it.name != "null" }
            .sortedBy { it.distance }
            .take(MAX_NEARBY_NPCS)
            .toList()
    }

    private fun buildNearbyLocList(player: Player): List<NearbyLocSnapshot> {
        return hunt
            .findLocs(player.coords, SCAN_RADIUS, HuntVis.Off)
            .mapNotNull { loc ->
                val type = ServerCacheManager.getObject(loc.id) ?: return@mapNotNull null

                // Filter out null/blank named objects
                if (type.name.isBlank() || type.name == "null") return@mapNotNull null

                val dx = max(loc.coords.x, player.coords.x) - minOf(loc.coords.x, player.coords.x)
                val dz = max(loc.coords.z, player.coords.z) - minOf(loc.coords.z, player.coords.z)
                val dist = max(dx, dz)

                // Skip if too close (often "null" floor decorations)
                if (dist < MIN_LOC_DISTANCE) return@mapNotNull null

                NearbyLocSnapshot(
                    id = loc.id,
                    name = type.name,
                    x = loc.coords.x,
                    z = loc.coords.z,
                    distance = dist,
                )
            }
            .sortedBy { it.distance }
            .take(MAX_NEARBY_LOCS)
            .toList()
    }

    /**
     * Process any pending waits for a player. Returns a WaitResult if a wait completed this tick.
     */
    private fun processPendingWaits(
        player: Player,
        playerKey: String,
        currentTick: Int,
        currentState: PlayerStateTracker,
    ): WaitResult? {
        val wait = pendingWaits[playerKey] ?: return null

        val waitedTicks = currentTick - wait.startTick

        // Check for timeout
        if (waitedTicks >= wait.maxTicks) {
            pendingWaits.remove(playerKey)
            val result =
                WaitResult(
                    success = false,
                    message = "${wait.waitType} timed out after ${wait.maxTicks} ticks",
                    waitedTicks = waitedTicks,
                )
            waitResults[playerKey] = result
            return result
        }

        // Check wait condition
        val conditionMet =
            when (wait.waitType) {
                "ready" -> checkReadyCondition(player, currentState)
                "position" -> checkPositionCondition(currentState, wait.params)
                "inventory_full" -> player.inv.isFull()
                "inventory_empty" -> player.inv.isEmpty()
                "dialog_open" -> player.ui.modals.isNotEmpty()
                "bank_open" -> player.ui.modals.any { it.value == 12 } // Bank interface ID
                "shop_open" -> player.ui.modals.any { it.value == 301 } // Shop interface ID
                "in_combat" -> player.isInCombat()
                "hp_below" -> {
                    val threshold = wait.params["threshold"] as? Int ?: return null
                    player.hitpoints <= threshold
                }
                else -> false
            }

        if (conditionMet) {
            pendingWaits.remove(playerKey)
            val result =
                WaitResult(
                    success = true,
                    message = "${wait.waitType} completed after $waitedTicks ticks",
                    waitedTicks = waitedTicks,
                )
            waitResults[playerKey] = result
            return result
        }

        return null
    }

    private fun checkReadyCondition(player: Player, state: PlayerStateTracker): Boolean {
        // Valid position (not 0,0), and in-game (has entities nearby)
        return state.x != 0 && state.z != 0 && state.animation != -1
    }

    private fun checkPositionCondition(
        state: PlayerStateTracker,
        params: Map<String, Any>,
    ): Boolean {
        val targetX = params["x"] as? Int ?: return false
        val targetZ = params["z"] as? Int ?: return false
        val tolerance = params["tolerance"] as? Int ?: 3

        val dx = max(state.x, targetX) - minOf(state.x, targetX)
        val dz = max(state.z, targetZ) - minOf(state.z, targetZ)
        val dist = max(dx, dz)

        return dist <= tolerance
    }

    /**
     * Process any pending door operations for a player. Returns door events if the operation
     * completed.
     */
    private fun processPendingDoorOps(
        player: Player,
        playerKey: String,
        currentTick: Int,
        nearbyLocs: List<NearbyLocSnapshot>,
    ): List<PlayerEvent> {
        val doorOp = pendingDoorOps[playerKey] ?: return emptyList()
        val events = mutableListOf<PlayerEvent>()
        val waitedTicks = currentTick - doorOp.startTick

        // Check for timeout
        if (waitedTicks >= doorOp.maxTicks) {
            pendingDoorOps.remove(playerKey)
            events.add(
                PlayerEvent.DoorLocked(
                    tick = currentTick,
                    x = doorOp.x,
                    z = doorOp.z,
                    plane = doorOp.plane,
                    name = "Door",
                    reason = "timeout",
                )
            )
            return events
        }

        when (doorOp.stage) {
            DoorOpStage.WALK_ADJACENT -> {
                // Walk to an adjacent tile first
                val target = findAdjacentTile(player.coords, doorOp.x, doorOp.z)
                if (target != null) {
                    player.clearPendingAction(eventBus)
                    player.walk(target)
                    // Update to next stage
                    pendingDoorOps[playerKey] = doorOp.copy(stage = DoorOpStage.OPEN_DOOR)
                } else {
                    // Already adjacent or can't find path
                    pendingDoorOps[playerKey] = doorOp.copy(stage = DoorOpStage.OPEN_DOOR)
                }
            }

            DoorOpStage.OPEN_DOOR -> {
                // Check if we're adjacent now
                val dx = max(player.coords.x, doorOp.x) - minOf(player.coords.x, doorOp.x)
                val dz = max(player.coords.z, doorOp.z) - minOf(player.coords.z, doorOp.z)
                val dist = max(dx, dz)

                if (dist <= 1) {
                    // Find the door loc
                    val doorLoc = nearbyLocs.find { it.x == doorOp.x && it.z == doorOp.z }
                    if (doorLoc != null) {
                        // Check if already open (has Close option)
                        val hasOpenOption = doorLoc.name.contains("Open", ignoreCase = true)
                        if (!hasOpenOption) {
                            // Door is already open
                            pendingDoorOps.remove(playerKey)
                            events.add(
                                PlayerEvent.DoorOpened(
                                    tick = currentTick,
                                    x = doorOp.x,
                                    z = doorOp.z,
                                    plane = doorOp.plane,
                                    name = doorLoc.name,
                                )
                            )
                        } else {
                            // Send interact to open door
                            // Need to get the actual loc from registry
                            val locInfo =
                                locRegistry.findType(
                                    CoordGrid(doorOp.x, doorOp.z, doorOp.plane),
                                    doorLoc.id,
                                )
                            if (locInfo != null) {
                                val type = ServerCacheManager.getObject(locInfo.id)
                                if (type != null) {
                                    val boundLoc = BoundLocInfo(locInfo, type)
                                    val interaction =
                                        InteractionLocOp(
                                            target = boundLoc,
                                            op = InteractionOp.Op1,
                                            hasOpTrigger =
                                                locInteractions.hasOpTrigger(
                                                    player,
                                                    boundLoc,
                                                    InteractionOp.Op1,
                                                    type,
                                                ),
                                            hasApTrigger =
                                                locInteractions.hasApTrigger(
                                                    player,
                                                    boundLoc,
                                                    InteractionOp.Op1,
                                                ),
                                        )
                                    val routeRequest = RouteRequestLoc(loc = locInfo, type = type)
                                    player.clearPendingAction(eventBus)
                                    player.faceLoc(locInfo, type.width, type.length)
                                    player.interaction = interaction
                                    player.routeRequest = routeRequest
                                    pendingDoorOps[playerKey] =
                                        doorOp.copy(
                                            stage = DoorOpStage.WAIT_FOR_OPEN,
                                            locId = doorLoc.id,
                                        )
                                }
                            }
                        }
                    } else {
                        // Door not found - might have been opened already or destroyed
                        pendingDoorOps.remove(playerKey)
                        events.add(
                            PlayerEvent.DoorOpened(
                                tick = currentTick,
                                x = doorOp.x,
                                z = doorOp.z,
                                plane = doorOp.plane,
                                name = "Door",
                            )
                        )
                    }
                }
                // else still walking, wait for next tick
            }

            DoorOpStage.WAIT_FOR_OPEN -> {
                // Check if door is now open
                val doorLoc = nearbyLocs.find { it.x == doorOp.x && it.z == doorOp.z }
                if (doorLoc == null || !doorLoc.name.contains("Open", ignoreCase = true)) {
                    // Door is open (no longer has Open option, or is gone)
                    pendingDoorOps.remove(playerKey)
                    events.add(
                        PlayerEvent.DoorOpened(
                            tick = currentTick,
                            x = doorOp.x,
                            z = doorOp.z,
                            plane = doorOp.plane,
                            name = doorLoc?.name ?: "Door",
                        )
                    )
                }
                // else still waiting
            }
        }

        return events
    }

    /** Find an adjacent tile to target for door interaction. */
    private fun findAdjacentTile(from: CoordGrid, targetX: Int, targetZ: Int): CoordGrid? {
        val candidates =
            listOf(
                CoordGrid(targetX, targetZ - 1, from.level),
                CoordGrid(targetX, targetZ + 1, from.level),
                CoordGrid(targetX - 1, targetZ, from.level),
                CoordGrid(targetX + 1, targetZ, from.level),
            )
        // Sort by distance from player
        return candidates
            .sortedBy { coord ->
                val dx = max(coord.x, from.x) - minOf(coord.x, from.x)
                val dz = max(coord.z, from.z) - minOf(coord.z, from.z)
                max(dx, dz)
            }
            .firstOrNull()
    }

    private fun detectEvents(
        prev: PlayerStateTracker,
        current: PlayerStateTracker,
        tick: Int,
    ): List<PlayerEvent> {
        val events = mutableListOf<PlayerEvent>()

        // Animation events
        if (prev.animation != current.animation) {
            if (prev.animation != 0) {
                events.add(PlayerEvent.AnimationEnd(tick, prev.animation))
            }
            if (current.animation != 0) {
                events.add(PlayerEvent.AnimationStart(tick, current.animation))
            }
        }

        // Position change events
        if (prev.x != current.x || prev.z != current.z || prev.plane != current.plane) {
            events.add(PlayerEvent.PositionChanged(tick, current.x, current.z, current.plane))
        }

        // Combat start/end events
        if (!prev.inCombat && current.inCombat) {
            events.add(PlayerEvent.CombatStarted(tick, current.targetIndex))
        } else if (prev.inCombat && !current.inCombat) {
            events.add(PlayerEvent.CombatEnded(tick))
        }

        // Damage taken events
        if (current.hitpoints < prev.hitpoints) {
            events.add(
                PlayerEvent.DamageTaken(
                    tick = tick,
                    damage = prev.hitpoints - current.hitpoints,
                    sourceType = if (current.targetIndex >= 0) "npc" else "unknown",
                    sourceIndex = current.targetIndex,
                )
            )
        }

        // Damage dealt / kill events (based on nearby NPC HP deltas by slot index)
        current.nearbyNpcHp.forEach { (npcIndex, currentHp) ->
            val prevHp = prev.nearbyNpcHp[npcIndex] ?: return@forEach
            if (currentHp < prevHp) {
                events.add(
                    PlayerEvent.DamageDealt(
                        tick = tick,
                        damage = prevHp - currentHp,
                        targetType = "npc",
                        targetIndex = npcIndex,
                    )
                )
                if (currentHp == 0) {
                    events.add(
                        PlayerEvent.Kill(tick = tick, targetType = "npc", targetIndex = npcIndex)
                    )
                }
            }
        }

        // XP gain events
        current.skills.forEach { (skill, xp) ->
            val prevXp = prev.skills[skill] ?: 0
            if (xp > prevXp) {
                events.add(PlayerEvent.XpGain(tick, skill, xp - prevXp, xp))
            }
        }

        // Inventory change events
        current.inventory.forEach { (slot, item) ->
            val prevItem = prev.inventory[slot]
            if (prevItem == null && item != null) {
                events.add(
                    PlayerEvent.ItemAdded(tick, item.id, item.name ?: "Unknown", item.qty, slot)
                )
            } else if (prevItem != null && item == null) {
                events.add(
                    PlayerEvent.ItemRemoved(
                        tick,
                        prevItem.id,
                        prevItem.name ?: "Unknown",
                        prevItem.qty,
                        slot,
                    )
                )
            } else if (prevItem != null && item != null) {
                if (prevItem.id != item.id) {
                    events.add(
                        PlayerEvent.ItemRemoved(
                            tick,
                            prevItem.id,
                            prevItem.name ?: "Unknown",
                            prevItem.qty,
                            slot,
                        )
                    )
                    events.add(
                        PlayerEvent.ItemAdded(tick, item.id, item.name ?: "Unknown", item.qty, slot)
                    )
                } else if (prevItem.qty != item.qty) {
                    val diff = item.qty - prevItem.qty
                    if (diff > 0) {
                        events.add(
                            PlayerEvent.ItemAdded(tick, item.id, item.name ?: "Unknown", diff, slot)
                        )
                    } else {
                        events.add(
                            PlayerEvent.ItemRemoved(
                                tick,
                                item.id,
                                item.name ?: "Unknown",
                                -diff,
                                slot,
                            )
                        )
                    }
                }
            }
        }

        return events
    }

    private fun executeAction(player: Player, action: BotAction): ActionResult {
        val xpBefore = captureXpSnapshot(player)

        val result =
            when (action) {
                is BotAction.Walk -> {
                    player.clearPendingAction(eventBus)
                    player.walk(CoordGrid(action.x, action.z, player.level))
                    ActionResult(true, "Walking to (${action.x}, ${action.z})", xpBefore, xpBefore)
                }

                is BotAction.Teleport -> {
                    player.coords = CoordGrid(action.x, action.z, action.plane)
                    ActionResult(
                        true,
                        "Teleported to (${action.x}, ${action.z}, ${action.plane})",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.InteractLoc -> {
                    val coords = CoordGrid(action.x, action.z, player.level)
                    val locInfo = locRegistry.findType(coords, action.id)
                    if (locInfo == null) {
                        return ActionResult(
                            false,
                            "No loc id=${action.id} at $coords",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    val type =
                        ServerCacheManager.getObject(locInfo.id)
                            ?: return ActionResult(
                                false,
                                "Unknown loc type id=${action.id}",
                                xpBefore,
                                xpBefore,
                            )
                    val boundLoc = BoundLocInfo(locInfo, type)
                    val op = interactionOp(action.option)
                    val opTrigger = locInteractions.hasOpTrigger(player, boundLoc, op, type)
                    val apTrigger = locInteractions.hasApTrigger(player, boundLoc, op, type)
                    if (!locInteractions.hasOp(boundLoc, type, player.vars, op)) {
                        return ActionResult(
                            false,
                            "No handler for op=$op on loc=$boundLoc",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    val interaction =
                        InteractionLocOp(
                            target = boundLoc,
                            op = op,
                            hasOpTrigger = opTrigger,
                            hasApTrigger = apTrigger,
                        )
                    val routeRequest = RouteRequestLoc(loc = locInfo, type = type)
                    player.clearPendingAction(eventBus)
                    player.resetFaceEntity()
                    player.faceLoc(locInfo, type.width, type.length)
                    player.interaction = interaction
                    player.routeRequest = routeRequest
                    ActionResult(
                        true,
                        "Interacting with ${type.name}",
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.InteractNpc -> {
                    val npc = npcList[action.index]
                    if (npc == null) {
                        return ActionResult(
                            false,
                            "No NPC at index=${action.index}",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    if (npc.isDelayed) {
                        return ActionResult(false, "NPC is delayed", xpBefore, xpBefore)
                    }
                    val op = interactionOp(action.option)
                    if (!npcInteractions.hasOp(npc, player.vars, op)) {
                        return ActionResult(
                            false,
                            "No handler for op=$op on npc=$npc",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    val opTrigger = npcInteractions.hasOpTrigger(player, npc, op)
                    val apTrigger = npcInteractions.hasApTrigger(player, npc, op)
                    val interaction =
                        InteractionNpcOp(
                            target = npc,
                            op = op,
                            hasOpTrigger = opTrigger,
                            hasApTrigger = apTrigger,
                        )
                    val routeRequest = RouteRequestPathingEntity(npc.avatar, clientRequest = false)
                    player.clearPendingAction(eventBus)
                    player.faceNpc(npc)
                    player.interaction = interaction
                    player.routeRequest = routeRequest
                    ActionResult(
                        true,
                        "Interacting with ${npc.name}",
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.InteractHeld -> {
                    val selectedSlot = action.selectedSlot
                    val targetSlot = action.targetSlot
                    if (selectedSlot !in player.inv.indices || targetSlot !in player.inv.indices) {
                        return ActionResult(
                            false,
                            "Invalid slot(s): selected=$selectedSlot target=$targetSlot",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    if (selectedSlot == targetSlot) {
                        return ActionResult(
                            false,
                            "Selected slot and target slot must be different",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    val selectedObj =
                        player.inv[selectedSlot]
                            ?: return ActionResult(
                                false,
                                "No item in selected slot=$selectedSlot",
                                xpBefore,
                                xpBefore,
                            )
                    val targetObj =
                        player.inv[targetSlot]
                            ?: return ActionResult(
                                false,
                                "No item in target slot=$targetSlot",
                                xpBefore,
                                xpBefore,
                            )
                    val selectedType =
                        ServerCacheManager.getItem(selectedObj.id)
                            ?: return ActionResult(
                                false,
                                "Unknown item type id=${selectedObj.id} in selected slot",
                                xpBefore,
                                xpBefore,
                            )
                    val targetType =
                        ServerCacheManager.getItem(targetObj.id)
                            ?: return ActionResult(
                                false,
                                "Unknown item type id=${targetObj.id} in target slot",
                                xpBefore,
                                xpBefore,
                            )
                    player.clearPendingAction(eventBus)
                    player.resetFaceEntity()
                    if (player.isAccessProtected) {
                        return ActionResult(
                            false,
                            "Player access is currently protected; try again next tick",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    protectedAccess.launch(player) {
                        heldUInteractions.interact(
                            access = this,
                            inventory = inv,
                            selectedItemServerType = selectedType,
                            selectedSlot = selectedSlot,
                            targetItemServerType = targetType,
                            targetSlot = targetSlot,
                        )
                    }
                    ActionResult(
                        true,
                        "Using ${selectedType.name} on ${targetType.name}",
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.SpawnItem -> {
                    val objType = ServerCacheManager.getItem(action.itemId)
                    if (objType == null) {
                        return ActionResult(
                            false,
                            "Unknown item id=${action.itemId}",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    val result = player.invAdd(player.inv, objType.id, action.count)
                    val msg =
                        if (result.success) {
                            "Added ${action.count}x ${objType.name}"
                        } else {
                            "Failed to add ${action.count}x ${objType.name} (inventory full?)"
                        }
                    ActionResult(result.success, msg, xpBefore, captureXpSnapshot(player))
                }

                is BotAction.ClearInventory -> {
                    var cleared = 0
                    for (slot in player.inv.indices) {
                        val obj = player.inv[slot]
                        if (obj != null) {
                            player.invDel(player.inv, obj.id, obj.count)
                            cleared++
                        }
                    }
                    ActionResult(true, "Cleared $cleared items from inventory", xpBefore, xpBefore)
                }

                is BotAction.DeleteItem -> {
                    val objType = ServerCacheManager.getItem(action.itemId)
                    if (objType == null) {
                        return ActionResult(
                            false,
                            "Unknown item id=${action.itemId}",
                            xpBefore,
                            xpBefore,
                        )
                    }
                    val result = player.invDel(player.inv, action.itemId, action.count)
                    val msg =
                        if (result.success) {
                            "Removed ${action.count}x ${objType.name}"
                        } else {
                            "Failed to remove ${action.count}x ${objType.name}"
                        }
                    ActionResult(result.success, msg, xpBefore, xpBefore)
                }

                is BotAction.EnsureItem -> {
                    val existing =
                        (0 until player.inv.size).sumOf { slot ->
                            val obj = player.inv[slot]
                            if (obj?.id == action.itemId) obj.count.toLong() else 0
                        }
                    val needed = action.count - existing
                    val result =
                        if (needed > 0) {
                            val objType =
                                ServerCacheManager.getItem(action.itemId)
                                    ?: return ActionResult(
                                        false,
                                        "Unknown item id=${action.itemId}",
                                        xpBefore,
                                        xpBefore,
                                    )
                            val addResult = player.invAdd(player.inv, objType.id, needed.toInt())
                            if (addResult.success) {
                                ActionResult(
                                    true,
                                    "Added ${needed}x ${objType.name}",
                                    xpBefore,
                                    captureXpSnapshot(player),
                                )
                            } else {
                                ActionResult(
                                    false,
                                    "Failed to add ${needed}x ${objType.name}",
                                    xpBefore,
                                    xpBefore,
                                )
                            }
                        } else {
                            ActionResult(true, "Already has ${existing}x item", xpBefore, xpBefore)
                        }
                    result
                }

                is BotAction.WaitTicks -> {
                    // This is handled by the action queue system - just acknowledge
                    ActionResult(true, "Waiting ${action.ticks} ticks", xpBefore, xpBefore)
                }

                is BotAction.WaitForAnimation -> {
                    // This is handled client-side via event listening
                    ActionResult(
                        true,
                        "Waiting for animation ${action.animationId}",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.WaitForXp -> {
                    // This is handled client-side via event listening
                    ActionResult(true, "Waiting for XP in ${action.skill}", xpBefore, xpBefore)
                }

                is BotAction.WaitForItem -> {
                    // This is handled client-side via event listening
                    ActionResult(true, "Waiting for item ${action.itemId}", xpBefore, xpBefore)
                }

                is BotAction.GetState -> {
                    ActionResult(true, "State retrieved", xpBefore, captureXpSnapshot(player))
                }

                is BotAction.WaitForReady -> {
                    val playerKey = player.avatar.name.lowercase()
                    val maxTicks = ticksFromMs(action.timeoutMs)
                    pendingWaits[playerKey] =
                        PendingWait(
                            waitType = "ready",
                            startTick = server.getCurrentTick(),
                            maxTicks = maxTicks,
                            params = emptyMap(),
                        )
                    ActionResult(
                        true,
                        "Waiting for ready (max ${action.timeoutMs}ms)",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.WaitForPosition -> {
                    val playerKey = player.avatar.name.lowercase()
                    val maxTicks = ticksFromMs(action.timeoutMs)
                    pendingWaits[playerKey] =
                        PendingWait(
                            waitType = "position",
                            startTick = server.getCurrentTick(),
                            maxTicks = maxTicks,
                            params =
                                mapOf(
                                    "x" to action.x,
                                    "z" to action.z,
                                    "tolerance" to action.tolerance,
                                ),
                        )
                    ActionResult(
                        true,
                        "Waiting for position (${action.x}, ${action.z}) +/- ${action.tolerance}",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.WaitForCondition -> {
                    val playerKey = player.avatar.name.lowercase()
                    val maxTicks = ticksFromMs(action.timeoutMs)
                    pendingWaits[playerKey] =
                        PendingWait(
                            waitType = action.conditionType,
                            startTick = server.getCurrentTick(),
                            maxTicks = maxTicks,
                            params = emptyMap(),
                        )
                    ActionResult(
                        true,
                        "Waiting for condition: ${action.conditionType}",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.FindPath -> {
                    val result =
                        pathfinding.findPath(
                            player,
                            action.x,
                            action.z,
                            action.plane,
                            action.maxWaypoints,
                        )
                    val doorList =
                        org.rsmod.content.other.agentbridge.pathfinding.DoorDatabase
                            .findDoorsAlongPath(result.waypoints)
                    val msg =
                        if (result.success) {
                            val doorsMsg =
                                if (doorList.isNotEmpty())
                                    " (passes through ${doorList.size} doors)"
                                else ""
                            if (result.reachedDestination) {
                                "Path found: ${result.waypoints.size} waypoints$doorsMsg"
                            } else {
                                "Partial path: ${result.waypoints.size} waypoints (couldn't reach destination)$doorsMsg"
                            }
                        } else {
                            "No path found"
                        }
                    ActionResult(true, msg, xpBefore, xpBefore)
                }

                is BotAction.CheckWalkable -> {
                    val walkable = pathfinding.isTileWalkable(action.plane, action.x, action.z)
                    ActionResult(
                        true,
                        "Tile (${action.x}, ${action.z}, ${action.plane}) is ${if (walkable) "walkable" else "blocked"}",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.OpenDoor -> {
                    val playerKey = player.avatar.name.lowercase()
                    val maxTicks = ticksFromMs(action.timeoutMs)
                    pendingDoorOps[playerKey] =
                        PendingDoorOp(
                            x = action.x,
                            z = action.z,
                            plane = action.plane,
                            startTick = server.getCurrentTick(),
                            maxTicks = maxTicks,
                            stage = DoorOpStage.WALK_ADJACENT,
                        )
                    ActionResult(
                        true,
                        "Opening door at (${action.x}, ${action.z}, ${action.plane})",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.BlockDoor -> {
                    val key = DoorKey(action.plane, action.x, action.z)
                    blockedDoors.add(key)
                    ActionResult(
                        true,
                        "Door at (${action.x}, ${action.z}, ${action.plane}) blocked for pathfinding",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.WalkWithDoors -> {
                    // Find path first
                    val pathResult =
                        pathfinding.findPath(player, action.x, action.z, action.plane, 500)

                    if (!pathResult.success || pathResult.waypoints.isEmpty()) {
                        ActionResult(
                            false,
                            "No path found to (${action.x}, ${action.z}, ${action.plane})",
                            xpBefore,
                            xpBefore,
                        )
                    } else {
                        // Find doors along path
                        val doors =
                            org.rsmod.content.other.agentbridge.pathfinding.DoorDatabase
                                .findDoorsAlongPath(pathResult.waypoints)
                        val blocked =
                            doors.filter { blockedDoors.contains(DoorKey(it.level, it.x, it.z)) }

                        if (blocked.isNotEmpty()) {
                            // Some doors are blocked, need to re-route
                            ActionResult(
                                false,
                                "Path blocked by ${blocked.size} locked door(s), need to re-route",
                                xpBefore,
                                xpBefore,
                            )
                        } else if (doors.isNotEmpty()) {
                            // Start opening the first door
                            val firstDoor = doors.first()
                            val playerKey = player.avatar.name.lowercase()
                            pendingDoorOps[playerKey] =
                                PendingDoorOp(
                                    x = firstDoor.x,
                                    z = firstDoor.z,
                                    plane = firstDoor.level,
                                    startTick = server.getCurrentTick(),
                                    maxTicks = ticksFromMs(8000),
                                    stage = DoorOpStage.WALK_ADJACENT,
                                )
                            ActionResult(
                                true,
                                "Walking to (${action.x}, ${action.z}), opening ${doors.size} door(s) along the way",
                                xpBefore,
                                xpBefore,
                            )
                        } else {
                            // No doors, just walk
                            player.clearPendingAction(eventBus)
                            player.walk(CoordGrid(action.x, action.z, action.plane))
                            ActionResult(
                                true,
                                "Walking to (${action.x}, ${action.z}, ${action.plane}) - no doors in path",
                                xpBefore,
                                xpBefore,
                            )
                        }
                    }
                }

                is BotAction.ChopTree -> {
                    val result = botPorcelain.chopTree(player, action.targetName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BurnLogs -> {
                    val result = botPorcelain.burnLogs(player, action.logType)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.PickupItem -> {
                    val result = botPorcelain.pickupItem(player, action.itemName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.TalkTo -> {
                    val result = botPorcelain.talkTo(player, action.npcName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.UseItemOnLoc -> {
                    val result = botPorcelain.useItemOnLoc(player, action.itemName, action.locName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.UseItemOnNpc -> {
                    val result = botPorcelain.useItemOnNpc(player, action.itemName, action.npcName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.DismissBlockingUI -> {
                    val result = botPorcelain.dismissBlockingUI(player)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.OpenBank -> {
                    val result = bankPorcelain.openBank(player)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BankDeposit -> {
                    val result =
                        bankPorcelain.deposit(player, action.slot, action.amount, action.note)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BankWithdraw -> {
                    val result =
                        bankPorcelain.withdraw(player, action.slot, action.amount, action.note)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.CloseBank -> {
                    val result = bankPorcelain.closeBank(player)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.FindBankItem -> {
                    val result = bankPorcelain.findBankItem(player, action.pattern)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.GetBankItems -> {
                    // This is handled by state tracking - just acknowledge
                    ActionResult(
                        true,
                        "Bank items will be included in next state snapshot",
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BankDepositAll -> {
                    val result = bankPorcelain.depositAll(player, action.pattern, action.keepAmount)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BankWithdrawByName -> {
                    val result =
                        bankPorcelain.withdrawByName(
                            player,
                            action.itemName,
                            action.amount,
                            action.note,
                        )
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.ExecutePorcelain -> {
                    // Generic porcelain execution based on type
                    val result =
                        when (action.porcelainType) {
                            "chopTree" -> botPorcelain.chopTree(player, action.params["targetName"])
                            "burnLogs" -> botPorcelain.burnLogs(player, action.params["logType"])
                            "pickupItem" ->
                                botPorcelain.pickupItem(player, action.params["itemName"] ?: "")
                            "talkTo" -> botPorcelain.talkTo(player, action.params["npcName"] ?: "")
                            else ->
                                BotPorcelain.PorcelainResult(
                                    false,
                                    "Unknown porcelain type: ${action.porcelainType}",
                                )
                        }
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                // ===== SHOP ACTIONS =====
                is BotAction.OpenShop -> {
                    val result = shopPorcelain.openShop(player, action.shopkeeperName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BuyFromShop -> {
                    val result = shopPorcelain.buyFromShop(player, action.slot, action.amount)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.BuyByName -> {
                    val result = shopPorcelain.buyByName(player, action.itemName, action.amount)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.SellToShop -> {
                    val result =
                        shopPorcelain.sellToShop(player, action.inventorySlot, action.amount)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.CloseShop -> {
                    val result = shopPorcelain.closeShop(player)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.FindShopItem -> {
                    val result = shopPorcelain.findShopItem(player, action.pattern)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.GetShopState -> {
                    // Shop state is tracked in PlayerState - just acknowledge
                    ActionResult(
                        true,
                        "Shop state will be included in next snapshot",
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.HaggleShop -> {
                    // Haggling not fully implemented yet
                    ActionResult(false, "Haggling not yet implemented", xpBefore, xpBefore)
                }

                // ===== GROUND ITEM ACTIONS =====
                is BotAction.ScanGroundItems -> {
                    val result =
                        groundItemPorcelain.scanGroundItems(player, action.radius, action.pattern)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.FindGroundItem -> {
                    val result =
                        groundItemPorcelain.findGroundItem(player, action.pattern, action.radius)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.PickupGroundItem -> {
                    val result =
                        groundItemPorcelain.pickupGroundItem(
                            player,
                            action.x,
                            action.z,
                            action.itemId,
                        )
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.PickupNearest -> {
                    val result =
                        groundItemPorcelain.pickupNearest(
                            player,
                            action.pattern,
                            action.maxDistance,
                        )
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.WaitForGroundItem -> {
                    val result =
                        groundItemPorcelain.waitForGroundItem(
                            player,
                            action.pattern,
                            action.timeoutMs,
                        )
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.ScanNearbyLocs -> {
                    // Extended loc scan - acknowledge and include in next state
                    ActionResult(true, "Extended location scan requested", xpBefore, xpBefore)
                }

                is BotAction.LootArea -> {
                    val result =
                        groundItemPorcelain.lootArea(player, action.radius, action.patterns)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                // ===== PRAYER ACTIONS =====
                is BotAction.TogglePrayer -> {
                    val result =
                        prayerPorcelain.togglePrayer(player, action.prayerName, action.prayerIndex)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.ActivatePrayer -> {
                    val result =
                        prayerPorcelain.activatePrayer(
                            player,
                            action.prayerName,
                            action.allowToggle,
                        )
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.DeactivatePrayer -> {
                    val result = prayerPorcelain.deactivatePrayer(player, action.prayerName)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.DeactivateAllPrayers -> {
                    val result = prayerPorcelain.deactivateAllPrayers(player)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.GetPrayerState -> {
                    val state = prayerPorcelain.getPrayerState(player)
                    ActionResult(
                        true,
                        "Prayer: ${state.prayerPoints}/${state.maxPrayerPoints} | Active: ${state.activePrayers.size} | Drain: ${state.drainRate}/tick",
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.WaitForPrayerPoints -> {
                    // Queue wait action
                    ActionResult(
                        true,
                        "Waiting for ${action.minPoints} prayer points",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.ActivateBestCombatPrayer -> {
                    val result = prayerPorcelain.activateBestCombatPrayer(player, action.prayerType)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                // ===== COMBAT ACTIONS =====
                is BotAction.AttackNpc -> {
                    val result = botPorcelain.attackNpc(player, action.index, action.timeoutMs)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.FightUntilHp -> {
                    if (!player.isInCombat()) {
                        ActionResult(false, "Not in combat", xpBefore, xpBefore)
                    } else {
                        val playerKey = player.avatar.name.lowercase()
                        val maxTicks = ticksFromMs(action.timeoutMs)
                        pendingWaits[playerKey] =
                            PendingWait(
                                waitType = "hp_below",
                                startTick = server.getCurrentTick(),
                                maxTicks = maxTicks,
                                params = mapOf("threshold" to action.threshold.coerceAtLeast(1)),
                            )
                        ActionResult(
                            true,
                            "Waiting until hitpoints <= ${action.threshold}",
                            xpBefore,
                            captureXpSnapshot(player),
                        )
                    }
                }

                is BotAction.EatFood -> {
                    val result = botPorcelain.eatFood(player, action.foodItem)
                    ActionResult(
                        result.success,
                        result.message,
                        xpBefore,
                        captureXpSnapshot(player),
                    )
                }

                is BotAction.SpawnBot -> {
                    val a = action as BotAction.SpawnBot
                    val bot = agentBotService.spawn(a.name, a.x, a.z)
                    if (bot != null) {
                        server.registerBotPlayer(bot)
                    }
                    ActionResult(
                        bot != null,
                        if (bot != null) "Spawned bot" else "Failed to spawn bot (no slots)",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.DespawnBot -> {
                    val a = action as BotAction.DespawnBot
                    val removed = agentBotService.despawn(a.name)
                    ActionResult(
                        removed,
                        if (removed) "Despawned bot" else "Bot not found",
                        xpBefore,
                        xpBefore,
                    )
                }

                is BotAction.ListBots -> {
                    val count = agentBotService.count()
                    ActionResult(true, "Active bots", xpBefore, xpBefore)
                }

                else -> ActionResult(false, "Unknown action", xpBefore, xpBefore)
            }

        return result.copy(xpAfter = captureXpSnapshot(player))
    }

    private fun captureXpSnapshot(player: Player): Map<String, Int> {
        return mapOf(
            "attack" to player.statMap.getXP("stat.attack"),
            "defence" to player.statMap.getXP("stat.defence"),
            "strength" to player.statMap.getXP("stat.strength"),
            "hitpoints" to player.statMap.getXP("stat.hitpoints"),
            "ranged" to player.statMap.getXP("stat.ranged"),
            "prayer" to player.statMap.getXP("stat.prayer"),
            "magic" to player.statMap.getXP("stat.magic"),
            "cooking" to player.statMap.getXP("stat.cooking"),
            "woodcutting" to player.statMap.getXP("stat.woodcutting"),
            "fletching" to player.statMap.getXP("stat.fletching"),
            "fishing" to player.statMap.getXP("stat.fishing"),
            "firemaking" to player.statMap.getXP("stat.firemaking"),
            "crafting" to player.statMap.getXP("stat.crafting"),
            "smithing" to player.statMap.getXP("stat.smithing"),
            "mining" to player.statMap.getXP("stat.mining"),
            "herblore" to player.statMap.getXP("stat.herblore"),
            "agility" to player.statMap.getXP("stat.agility"),
            "thieving" to player.statMap.getXP("stat.thieving"),
            "slayer" to player.statMap.getXP("stat.slayer"),
            "farming" to player.statMap.getXP("stat.farming"),
            "runecrafting" to player.statMap.getXP("stat.runecrafting"),
            "hunter" to player.statMap.getXP("stat.hunter"),
            "construction" to player.statMap.getXP("stat.construction"),
        )
    }

    private fun interactionOp(option: Int): InteractionOp =
        when (option) {
            1 -> InteractionOp.Op1
            2 -> InteractionOp.Op2
            3 -> InteractionOp.Op3
            4 -> InteractionOp.Op4
            5 -> InteractionOp.Op5
            6 -> InteractionOp.Op5
            7 -> InteractionOp.Op5
            8 -> InteractionOp.Op5
            9 -> InteractionOp.Op5
            10 -> InteractionOp.Op5
            else -> InteractionOp.Op1
        }
}

/** Tracks player state for event detection. */
private data class PlayerStateTracker(
    val x: Int,
    val z: Int,
    val plane: Int,
    val animation: Int,
    val hitpoints: Int,
    val inCombat: Boolean,
    val targetIndex: Int,
    val combatState: CombatStateSnapshot,
    val nearbyNpcHp: Map<Int, Int>,
    val skills: Map<String, Int>,
    val inventory: Map<Int, InvItemSnapshot?>,
) {
    companion object {
        fun from(player: Player, nearbyNpcs: List<NearbyNpcSnapshot>): PlayerStateTracker {
            val inv = mutableMapOf<Int, InvItemSnapshot?>()
            for (slot in player.inv.indices) {
                val obj = player.inv[slot]
                inv[slot] =
                    if (obj != null) {
                        InvItemSnapshot(slot, obj.id, obj.count, null)
                    } else null
            }
            val targetIndex =
                when {
                    player.faceEntity.isNpc -> player.faceEntity.npcSlot
                    player.faceEntity.isPlayer -> player.faceEntity.playerSlot
                    else -> -1
                }
            val inCombat = player.isInCombat()
            val combatState =
                CombatStateSnapshot(
                    inCombat = inCombat,
                    targetIndex = targetIndex,
                    lastDamageTick = player.vars["varp.lastcombat"],
                )
            val nearbyNpcHp = nearbyNpcs.associate { it.index to it.hp }

            return PlayerStateTracker(
                x = player.coords.x,
                z = player.coords.z,
                plane = player.coords.level,
                animation = player.pendingSequence.id,
                hitpoints = player.hitpoints,
                inCombat = inCombat,
                targetIndex = targetIndex,
                combatState = combatState,
                nearbyNpcHp = nearbyNpcHp,
                skills =
                    mapOf(
                        "attack" to player.statMap.getXP("stat.attack"),
                        "defence" to player.statMap.getXP("stat.defence"),
                        "strength" to player.statMap.getXP("stat.strength"),
                        "hitpoints" to player.statMap.getXP("stat.hitpoints"),
                        "ranged" to player.statMap.getXP("stat.ranged"),
                        "prayer" to player.statMap.getXP("stat.prayer"),
                        "magic" to player.statMap.getXP("stat.magic"),
                        "cooking" to player.statMap.getXP("stat.cooking"),
                        "woodcutting" to player.statMap.getXP("stat.woodcutting"),
                        "fletching" to player.statMap.getXP("stat.fletching"),
                        "fishing" to player.statMap.getXP("stat.fishing"),
                        "firemaking" to player.statMap.getXP("stat.firemaking"),
                        "crafting" to player.statMap.getXP("stat.crafting"),
                        "smithing" to player.statMap.getXP("stat.smithing"),
                        "mining" to player.statMap.getXP("stat.mining"),
                        "herblore" to player.statMap.getXP("stat.herblore"),
                        "agility" to player.statMap.getXP("stat.agility"),
                        "thieving" to player.statMap.getXP("stat.thieving"),
                        "slayer" to player.statMap.getXP("stat.slayer"),
                        "farming" to player.statMap.getXP("stat.farming"),
                        "runecrafting" to player.statMap.getXP("stat.runecrafting"),
                        "hunter" to player.statMap.getXP("stat.hunter"),
                        "construction" to player.statMap.getXP("stat.construction"),
                    ),
                inventory = inv,
            )
        }
    }
}

private fun minOf(a: Int, b: Int): Int = if (a < b) a else b
