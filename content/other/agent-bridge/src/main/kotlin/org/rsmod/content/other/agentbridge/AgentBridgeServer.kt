package org.rsmod.content.other.agentbridge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.rsmod.annotations.InternalApi
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

private const val PORT = 43595

/**
 * Singleton WebSocket server that:
 * - Broadcasts per-player state snapshots to LLM testing agents every tick.
 * - Receives action commands from agents and queues them for execution on the game thread.
 *
 * Action protocol (inbound JSON):
 * ```
 * { "player": "TestBot", "type": "walk", "x": 3222, "z": 3219 }
 * { "player": "TestBot", "type": "teleport", "x": 3222, "z": 3219, "plane": 0 }
 * { "player": "TestBot", "type": "interact_loc", "id": 10820, "x": 3223, "z": 3219, "option": 1 }
 * { "player": "TestBot", "type": "interact_npc", "index": 7, "option": 1 }
 * { "player": "TestBot", "type": "interact_held", "selected_slot": 0, "target_slot": 1 }
 * { "player": "TestBot", "type": "spawn_item", "item_id": 1511, "count": 1 }
 * { "player": "TestBot", "type": "clear_inventory" }
 * { "player": "TestBot", "type": "ensure_item", "item_id": 1438, "count": 1 }
 * { "player": "TestBot", "type": "wait_ticks", "ticks": 5 }
 * { "player": "TestBot", "type": "get_state" }
 * ```
 *
 * The `player` field targets a specific player by name. Omitting it broadcasts to all players.
 * Actions are queued thread-safely and dequeued by [AgentBridgeScript] on the game thread.
 */
@Singleton
class AgentBridgeServer @Inject constructor(private val clock: MapClock) {
    private val logger = InlineLogger()
    private val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val clients = CopyOnWriteArrayList<WebSocket>()
    private val started = AtomicBoolean(false)

    /** Pending actions per player (keyed by lowercase player name). Game thread reads these. */
    private val pendingActions = ConcurrentHashMap<String, ConcurrentLinkedQueue<BotAction>>()
    private val systemActions = ConcurrentLinkedQueue<BotAction>()
    private val telemetry = ConcurrentHashMap<String, PlayerTelemetry>()
    /** Track which bots each WebSocket connection spawned, for cleanup on disconnect. */
    private val connBots = ConcurrentHashMap<WebSocket, CopyOnWriteArrayList<String>>()

    /** Current game tick for event timestamps. */
    private val currentTick = AtomicInteger(0)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (!isPortAvailable(PORT)) {
            logger.warn {
                "[AgentBridge] Port $PORT already in use. Skipping AgentBridge startup for this process."
            }
            return
        }
        val server = buildServer()
        server.isReuseAddr = true
        server.isDaemon = true
        server.start()
    }

    private fun isPortAvailable(port: Int): Boolean =
        runCatching {
                ServerSocket().use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(port))
                    true
                }
            }
            .getOrDefault(false)

    /** Get the current game tick. */
    fun getCurrentTick(): Int = currentTick.get()

    /**
     * Broadcast game state to all connected agent clients. Called from the game thread.
     * [nearbyNpcs] and [nearbyLocs] are computed by [AgentBridgeScript] using Hunt.
     */
    fun broadcast(
        player: Player,
        nearbyNpcs: List<NearbyNpcSnapshot>,
        nearbyLocs: List<NearbyLocSnapshot>,
        combatState: CombatStateSnapshot,
        events: List<PlayerEvent> = emptyList(),
        actionResult: ActionResult? = null,
        waitResult: WaitResult? = null,
    ) {
        currentTick.set(clock.cycle)
        ensureClientTap(player)
        if (clients.isEmpty()) return
        val telemetryState = telemetry[player.avatar.name.lowercase()]
        val snapshot =
            player.toSnapshot(
                clock.cycle,
                nearbyNpcs,
                nearbyLocs,
                combatState,
                telemetryState,
                events,
            )

        val payload =
            mutableMapOf<String, Any?>(
                "type" to "state",
                "tick" to clock.cycle,
                "timestamp" to System.currentTimeMillis(),
                "player" to snapshot.player,
                "dialog" to snapshot.dialog,
                "gameMessages" to snapshot.gameMessages,
                "combat" to combatState,
                "events" to events,
            )

        // Include action result if present
        if (actionResult != null) {
            payload["lastAction"] =
                mapOf("success" to actionResult.success, "message" to actionResult.message)
        }

        // Include wait result if present
        if (waitResult != null) {
            payload["waitResult"] =
                mapOf(
                    "success" to waitResult.success,
                    "message" to waitResult.message,
                    "waitedTicks" to waitResult.waitedTicks,
                )
        }

        val json = mapper.writeValueAsString(payload)
        val openClients = clients.filter { it.isOpen }
        for (client in openClients) {
            client.send(json)
        }
    }

    /**
     * Dequeue the next pending action for [playerName]. Called from the game thread. Returns null
     * if no actions are queued.
     */
    fun pollAction(playerName: String): BotAction? = pendingActions[playerName.lowercase()]?.poll()

    /** Poll the next system-level action (spawn_bot, despawn_bot, etc). Called from game thread. */
    fun pollSystemAction(): BotAction? = systemActions.poll()

    fun ensureClientTap(player: Player) {
        if (player.client is AgentBridgeTapClient) {
            return
        }
        val playerKey = player.avatar.name.lowercase()
        val current = player.client
        player.client = AgentBridgeTapClient(current, telemetrySink(playerKey))
    }

    fun registerBotPlayer(player: Player) {
        ensureClientTap(player)
        player.softTimer(AGENT_BRIDGE_TIMER, 1)
    }

    private fun enqueue(playerName: String, action: BotAction) {
        pendingActions.getOrPut(playerName.lowercase()) { ConcurrentLinkedQueue() }.offer(action)
    }

    private fun parseSystemAction(
        node: JsonNode,
        type: String,
        conn: WebSocket? = null,
    ): BotAction? {
        return try {
            when (type) {
                "spawn_bot" -> {
                    val botName = node.req("name").asText()
                    if (conn != null) {
                        connBots.getOrPut(conn) { CopyOnWriteArrayList() }.add(botName)
                    }
                    BotAction.SpawnBot(
                        name = botName,
                        x = node.get("x")?.asInt() ?: 3222,
                        z = node.get("z")?.asInt() ?: 3222,
                    )
                }
                "despawn_bot" -> {
                    val botName = node.req("name").asText()
                    // Remove from all connection tracking (bot may have been spawned by any conn)
                    for (entry in connBots.entries) {
                        entry.value.remove(botName)
                    }
                    BotAction.DespawnBot(name = botName)
                }
                "list_bots" -> BotAction.ListBots
                else -> return null
            }
        } catch (e: IllegalArgumentException) {
            logger.error { "[AgentBridge] Missing field in $type action: ${e.message}" }
            null
        }
    }

    private fun parseAndEnqueue(conn: WebSocket, message: String) {
        val node: JsonNode =
            try {
                mapper.readTree(message)
            } catch (_: Exception) {
                logger.error { "[AgentBridge] Invalid JSON: $message" }
                return
            }

        val type = node.get("type")?.asText() ?: return
        System.err.println(
            "[AgentBridge] Received action type=$type for player=${node.get("player")?.asText()}"
        )

        // System-level actions (no player target needed)
        if (type in listOf("spawn_bot", "despawn_bot", "list_bots")) {
            val action = parseSystemAction(node, type, conn) ?: return
            systemActions.offer(action)
            return
        }

        val playerName = node.get("player")?.asText() ?: return

        val action: BotAction =
            try {
                when (type) {
                    "walk" -> BotAction.Walk(x = node.req("x").asInt(), z = node.req("z").asInt())
                    "teleport" ->
                        BotAction.Teleport(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            plane = node.get("plane")?.asInt() ?: 0,
                        )
                    "interact_loc" ->
                        BotAction.InteractLoc(
                            id = node.req("id").asInt(),
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            option = node.get("option")?.asInt() ?: 1,
                        )
                    "interact_npc" ->
                        BotAction.InteractNpc(
                            index = node.req("index").asInt(),
                            option = node.get("option")?.asInt() ?: 1,
                        )
                    "interact_held" ->
                        BotAction.InteractHeld(
                            selectedSlot = node.req("selected_slot").asInt(),
                            targetSlot = node.req("target_slot").asInt(),
                        )
                    "spawn_item" ->
                        BotAction.SpawnItem(
                            itemId = node.req("item_id").asInt(),
                            count = node.get("count")?.asInt() ?: 1,
                        )
                    "clear_inventory" -> BotAction.ClearInventory
                    "delete_item" ->
                        BotAction.DeleteItem(
                            itemId = node.req("item_id").asInt(),
                            count = node.get("count")?.asInt() ?: 1,
                        )
                    "ensure_item" ->
                        BotAction.EnsureItem(
                            itemId = node.req("item_id").asInt(),
                            count = node.get("count")?.asInt() ?: 1,
                        )
                    "wait_ticks" -> BotAction.WaitTicks(ticks = node.req("ticks").asInt())
                    "wait_for_animation" ->
                        BotAction.WaitForAnimation(
                            animationId = node.req("animation_id").asInt(),
                            timeoutMs = node.get("timeout")?.asInt() ?: 5000,
                        )
                    "wait_for_xp" ->
                        BotAction.WaitForXp(
                            skill = node.req("skill").asText(),
                            minAmount = node.req("min_amount").asInt(),
                            timeoutMs = node.get("timeout")?.asInt() ?: 10000,
                        )
                    "wait_for_item" ->
                        BotAction.WaitForItem(
                            itemId = node.req("item_id").asInt(),
                            timeoutMs = node.get("timeout")?.asInt() ?: 5000,
                        )
                    "wait_for_ready" ->
                        BotAction.WaitForReady(timeoutMs = node.get("timeout")?.asInt() ?: 15000)
                    "wait_for_position" ->
                        BotAction.WaitForPosition(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            tolerance = node.get("tolerance")?.asInt() ?: 3,
                            timeoutMs = node.get("timeout")?.asInt() ?: 30000,
                        )
                    "wait_for_condition" ->
                        BotAction.WaitForCondition(
                            conditionType = node.req("condition").asText(),
                            timeoutMs = node.get("timeout")?.asInt() ?: 30000,
                        )
                    "find_path" ->
                        BotAction.FindPath(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            plane = node.get("plane")?.asInt() ?: 0,
                            maxWaypoints = node.get("max_waypoints")?.asInt() ?: 500,
                        )
                    "check_walkable" ->
                        BotAction.CheckWalkable(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            plane = node.get("plane")?.asInt() ?: 0,
                        )
                    "open_door" ->
                        BotAction.OpenDoor(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            plane = node.get("plane")?.asInt() ?: 0,
                            timeoutMs = node.get("timeout")?.asInt() ?: 8000,
                        )
                    "block_door" ->
                        BotAction.BlockDoor(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            plane = node.get("plane")?.asInt() ?: 0,
                        )
                    "walk_with_doors" ->
                        BotAction.WalkWithDoors(
                            x = node.req("x").asInt(),
                            z = node.req("z").asInt(),
                            plane = node.get("plane")?.asInt() ?: 0,
                            tolerance = node.get("tolerance")?.asInt() ?: 3,
                        )
                    "attack_npc" ->
                        BotAction.AttackNpc(
                            index = node.req("index").asInt(),
                            timeoutMs = node.get("timeout")?.asInt() ?: 10000,
                        )
                    "fight_until_hp" ->
                        BotAction.FightUntilHp(
                            threshold = node.req("threshold").asInt(),
                            timeoutMs = node.get("timeout")?.asInt() ?: 60000,
                        )
                    "eat_food" -> BotAction.EatFood(foodItem = node.get("food_item")?.asText())
                    "set_combat_style" ->
                        BotAction.SetCombatStyle(style = node.req("style").asText())
                    "get_state" -> BotAction.GetState
                    else -> {
                        logger.info { "[AgentBridge] Unknown action type: $type" }
                        return
                    }
                }
            } catch (e: IllegalArgumentException) {
                logger.error { "[AgentBridge] Missing field in $type action: ${e.message}" }
                return
            }

        enqueue(playerName, action)
    }

    private fun JsonNode.req(field: String): JsonNode =
        get(field) ?: throw IllegalArgumentException("Missing required field: $field")

    private fun buildServer(): WebSocketServer =
        object : WebSocketServer(InetSocketAddress(PORT)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                clients.add(conn)
                logger.info { "[AgentBridge] Agent connected: ${conn.remoteSocketAddress}" }
            }

            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
                clients.remove(conn)
                logger.info { "[AgentBridge] Agent disconnected: ${conn.remoteSocketAddress}" }
                val botNames = connBots.remove(conn)
                if (botNames != null) {
                    for (botName in botNames) {
                        systemActions.offer(BotAction.DespawnBot(name = botName))
                    }
                    logger.info {
                        "[AgentBridge] Queued despawn for ${botNames.size} bots from disconnected client"
                    }
                }
            }

            override fun onMessage(conn: WebSocket, message: String) {
                parseAndEnqueue(conn, message)
            }

            override fun onError(conn: WebSocket?, ex: Exception) {
                // Log errors silently — don't crash the game server.
                logger.error { "[AgentBridge] WebSocket error: ${ex.message}" }
            }

            override fun onStart() {
                logger.info { "[AgentBridge] WebSocket server listening on port $PORT" }
            }
        }

    private fun telemetrySink(playerKey: String): PacketTapSink =
        object : PacketTapSink {
            override fun onGameMessage(type: Int, text: String) {
                val state = telemetry.getOrPut(playerKey) { PlayerTelemetry() }
                if (text.isBlank()) return
                if (state.gameMessages.size >= 20) {
                    state.gameMessages.removeFirst()
                }
                state.gameMessages.addLast(GameMessageSnapshot(type = type, text = text))
            }

            override fun onInterfaceText(interfaceId: Int, componentId: Int, text: String) {
                val state = telemetry.getOrPut(playerKey) { PlayerTelemetry() }
                if (text.isBlank()) return
                if (state.interfaceTexts.size >= 80) {
                    state.interfaceTexts.removeFirst()
                }
                state.interfaceTexts.addLast(
                    InterfaceTextSnapshot(
                        interfaceId = interfaceId,
                        componentId = componentId,
                        text = text,
                    )
                )
            }
        }
}

private data class PlayerTelemetry(
    val gameMessages: ArrayDeque<GameMessageSnapshot> = ArrayDeque(),
    val interfaceTexts: ArrayDeque<InterfaceTextSnapshot> = ArrayDeque(),
)

private data class InterfaceTextSnapshot(
    val interfaceId: Int,
    val componentId: Int,
    val text: String,
)

// ---------------------------------------------------------------------------
// State serialisation helpers
// ---------------------------------------------------------------------------

private fun Player.toSnapshot(
    tick: Int,
    nearbyNpcs: List<NearbyNpcSnapshot>,
    nearbyLocs: List<NearbyLocSnapshot>,
    combatState: CombatStateSnapshot,
    telemetry: PlayerTelemetry?,
    events: List<PlayerEvent>,
): StateSnapshot {
    val skills = buildMap {
        put("attack", statSnapshot("stat.attack"))
        put("defence", statSnapshot("stat.defence"))
        put("strength", statSnapshot("stat.strength"))
        put("hitpoints", statSnapshot("stat.hitpoints"))
        put("ranged", statSnapshot("stat.ranged"))
        put("prayer", statSnapshot("stat.prayer"))
        put("magic", statSnapshot("stat.magic"))
        put("cooking", statSnapshot("stat.cooking"))
        put("woodcutting", statSnapshot("stat.woodcutting"))
        put("fletching", statSnapshot("stat.fletching"))
        put("fishing", statSnapshot("stat.fishing"))
        put("firemaking", statSnapshot("stat.firemaking"))
        put("crafting", statSnapshot("stat.crafting"))
        put("smithing", statSnapshot("stat.smithing"))
        put("mining", statSnapshot("stat.mining"))
        put("herblore", statSnapshot("stat.herblore"))
        put("agility", statSnapshot("stat.agility"))
        put("thieving", statSnapshot("stat.thieving"))
        put("slayer", statSnapshot("stat.slayer"))
        put("farming", statSnapshot("stat.farming"))
        put("runecrafting", statSnapshot("stat.runecrafting"))
        put("hunter", statSnapshot("stat.hunter"))
        put("construction", statSnapshot("stat.construction"))
    }

    val inventory =
        inv.objs.mapIndexedNotNull { slot, obj ->
            if (obj != null) InvItemSnapshot(slot, obj.id, obj.count, null) else null
        }

    val equipment =
        worn.objs.mapIndexedNotNull { slot, obj ->
            if (obj != null) InvItemSnapshot(slot, obj.id, obj.count, null) else null
        }

    val modalInterfaceIds = ui.modals.values.toSet()
    val messageBuffer = telemetry?.gameMessages?.toList().orEmpty()
    val dialogLines = mutableListOf<String>()
    if (telemetry != null && modalInterfaceIds.isNotEmpty()) {
        for (entry in telemetry.interfaceTexts) {
            if (entry.interfaceId in modalInterfaceIds) {
                val line = entry.text.trim()
                if (line.isNotEmpty()) {
                    dialogLines.add(line)
                }
            }
        }
    }

    val dialogSnapshot =
        DialogSnapshot(
            isOpen = ui.getModalOrNull("component.chatbox:chatmodal") != null,
            modalInterfaceIds = modalInterfaceIds.toList(),
            lines = dialogLines.takeLast(12),
        )

    return StateSnapshot(
        tick = tick,
        player =
            PlayerSnapshot(
                name = avatar.name,
                position = PositionSnapshot(coords.x, coords.z, coords.level),
                skills = skills,
                inventory = inventory,
                equipment = equipment,
                animation = pendingSequence.id,
                nearbyNpcs = nearbyNpcs,
                nearbyLocs = nearbyLocs,
                dialog = dialogSnapshot,
                gameMessages = messageBuffer,
                combat = combatState,
            ),
        dialog = dialogSnapshot,
        gameMessages = messageBuffer,
        events = events,
    )
}

@OptIn(InternalApi::class)
private fun Player.statSnapshot(stat: String): SkillSnapshot =
    SkillSnapshot(level = statMap.getBaseLevel(stat).toInt() and 0xFF, xp = statMap.getXP(stat))

/** Internal action result tracking. */
data class ActionResult(
    val success: Boolean,
    val message: String,
    val xpBefore: Map<String, Int>,
    val xpAfter: Map<String, Int>,
)

/** Wait operation result for client notification. */
data class WaitResult(val success: Boolean, val message: String, val waitedTicks: Int)
