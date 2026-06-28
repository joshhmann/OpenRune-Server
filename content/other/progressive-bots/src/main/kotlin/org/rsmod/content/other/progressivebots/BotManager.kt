package org.rsmod.content.other.progressivebots

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.PlayerChatEvent
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.script.onCommand
import org.rsmod.content.other.playerbotservice.BotConfig
import org.rsmod.content.other.playerbotservice.PlayerBotService
import org.rsmod.content.other.progressivebots.chat.ChatResponseSystem
import org.rsmod.content.other.progressivebots.qa.BotQaSystem
import org.rsmod.content.other.progressivebots.tree.GoalStack
import org.rsmod.content.other.progressivebots.tree.NodeStatus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PublicMessage
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.seq.EntitySeq
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Tracks a single progressive bot's state between ticks. */
data class BotState(
    val def: BotDef,
    var ticksAtCurrentPos: Int = 0,
    var lastX: Int = def.spawnX,
    var lastZ: Int = def.spawnZ,
    var personality: BotPersonality = BotPersonality.forPlanner(def.planner),
    val goalStack: GoalStack = GoalStack(),
    val spatialMemory: org.rsmod.content.other.progressivebots.tree.SpatialMemory =
        org.rsmod.content.other.progressivebots.tree.SpatialMemory(),
    var lastActionName: String = "Idle",
    /** Whether we've captured the decision for the current action (for trajectory). */
    var decisionCaptured: Boolean = false,
    val locRegistry: org.rsmod.api.registry.loc.LocRegistry? = null,
    val eventBus: org.rsmod.events.EventBus? = null,
    val npcList: org.rsmod.game.entity.NpcList? = null,
    val lastChatTimes: MutableMap<String, Long> = mutableMapOf(),
    var qaTaskNode: org.rsmod.content.other.progressivebots.tree.BehaviorNode? = null,
    var qaTaskName: String? = null,
    val pendingChats: java.util.concurrent.ConcurrentLinkedQueue<String> =
        java.util.concurrent.ConcurrentLinkedQueue(),
)

/**
 * Progressive bot script — manages all server-side autonomous bot players.
 *
 * Lifecycle:
 * 1. On startup: spawns all bots from [BotConfig], subscribes to tick events
 * 2. Each LateCycle: evaluates each bot's state and picks actions
 *
 * Bots are real Player objects with NoopClient, visible to all online players.
 */
class BotManager
@Inject
constructor(
    private val playerBotService: PlayerBotService,
    private val playerList: org.rsmod.game.entity.PlayerList,
    private val clock: MapClock,
    private val locRegistry: org.rsmod.api.registry.loc.LocRegistry,
    private val eventBus: org.rsmod.events.EventBus,
    private val npcList: org.rsmod.game.entity.NpcList,
) : PluginScript() {

    private val bots = mutableMapOf<String, BotState>()
    private var initialized = false
    private var spawnAttempted = false
    private val trajectory = TrajectoryCapture()

    override fun ScriptContext.startup() {
        if (initialized) return
        initialized = true

        // Gate: check if progressive bots are enabled in config
        if (!BotConfig.config.progressive) {
            logger.info { "[ProgressiveBots] Disabled via config (bots.progressive=false)" }
            return
        }

        eventBus.subscribeUnbound(GameLifecycle.LateCycle::class.java) { onTick() }
        eventBus.subscribeUnbound(PlayerChatEvent::class.java) { onPlayerChat(this) }

        onCommand("botqa") {
            this.internal = "modlevel.admin"
            this.desc = "Force progressive bot into QA mode: ::botqa username task"
            this.cheat { botQaCommand(this) }
        }

        logger.info {
            "[ProgressiveBots] Config enabled, will spawn ${org.rsmod.content.other.progressivebots.BotConfig.bots.size} bots on first tick"
        }
    }

    private fun botQaCommand(cheat: Cheat) =
        with(cheat) {
            if (args.size < 2) {
                player.mes("Usage: ::botqa <username> <task>")
                player.mes("Tasks: ${BotQaSystem.getRegisteredTasks().joinToString()}")
                return
            }
            val botName = args[0]
            val taskName = args[1]

            val botState = bots[botName.lowercase()]
            if (botState == null) {
                player.mes("Bot '$botName' not found or active.")
                return
            }

            val taskNode = BotQaSystem.getTask(taskName)
            if (taskNode == null) {
                player.mes("Task '$taskName' not found.")
                player.mes("Tasks: ${BotQaSystem.getRegisteredTasks().joinToString()}")
                return
            }

            botState.goalStack.clear()
            botState.qaTaskNode = taskNode
            botState.qaTaskName = taskName
            player.mes("Forced '$botName' to execute QA task '$taskName'.")
        }

    private fun attemptSpawning() {
        if (spawnAttempted) return
        spawnAttempted = true

        val total = org.rsmod.content.other.progressivebots.BotConfig.bots.size
        var spawned = 0

        for (def in org.rsmod.content.other.progressivebots.BotConfig.bots) {
            try {
                playerBotService.spawnBot(def.username, def.spawnX, def.spawnZ)
                bots[def.username] =
                    BotState(
                        def = def,
                        locRegistry = locRegistry,
                        eventBus = eventBus,
                        npcList = npcList,
                    )
                spawned++
            } catch (e: Exception) {
                logger.warn { "[ProgressiveBots] Failed to spawn '${def.username}': ${e.message}" }
            }
        }

        logger.info { "[ProgressiveBots] Spawned $spawned/$total progressive bots" }

        val actual = playerBotService.botCount()
        logger.info { "[ProgressiveBots] PlayerList reports $actual NoopClient bots" }
    }

    private fun onTick() {
        if (!initialized) return

        // Spawn bots on first tick (after DB is ready)
        attemptSpawning()

        for ((name, state) in bots.toList()) {
            val player = playerBotService.findBot(name) ?: continue
            try {
                tickBot(player, state)
            } catch (e: Exception) {
                logger.warn { "[ProgressiveBots] Exception ticketing bot $name: ${e.message}" }
            }
        }
    }

    private fun onPlayerChat(event: PlayerChatEvent) {
        val sender = event.player
        val senderName = sender.avatar.name
        if (bots.containsKey(senderName)) {
            // 5% chance that a bot replies to another bot to create organic social chatting
            if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= 5) {
                return
            }
        }

        val currentTime = System.currentTimeMillis()
        val senderCoords = sender.coords
        val potentialResponders = mutableListOf<Pair<Player, BotState>>()

        for ((name, state) in bots) {
            if (name == senderName) continue // Don't reply to yourself
            val botPlayer = playerBotService.findBot(name) ?: continue
            if (botPlayer.coords.chebyshevDistance(senderCoords) <= 8) {
                val lastTime = state.lastChatTimes[senderName] ?: 0L
                if (currentTime - lastTime >= 10000L) {
                    potentialResponders.add(botPlayer to state)
                }
            }
        }

        if (potentialResponders.isNotEmpty()) {
            val (botPlayer, state) = potentialResponders.random()
            state.lastChatTimes[senderName] = currentTime
            ChatResponseSystem.handleIncomingChat(sender, botPlayer, event.text).thenAccept {
                responseText ->
                if (responseText != null && responseText.isNotBlank()) {
                    state.pendingChats.add(responseText)
                }
            }
        }
    }

    private fun tickBot(player: Player, state: BotState) {
        val coords = player.coords
        val tick = clock.cycle

        if (coords.x != state.lastX || coords.z != state.lastZ) {
            // Bot moved — capture outcome of the previous action if we tracked one
            if (state.lastActionName != "Idle" && state.decisionCaptured) {
                trajectory.captureOutcome(
                    tick = tick,
                    botName = player.avatar.name.lowercase(),
                    x = coords.x,
                    z = coords.z,
                )
                state.decisionCaptured = false
            }
            state.lastX = coords.x
            state.lastZ = coords.z
            state.ticksAtCurrentPos = 0
        } else {
            state.ticksAtCurrentPos++
        }

        if (state.ticksAtCurrentPos % 5 != 0) return // Faster tick rate for BT

        var pendingChat: String? = null
        while (state.pendingChats.isNotEmpty()) {
            pendingChat = state.pendingChats.poll()
        }

        if (pendingChat != null) {
            player.publicMessage =
                PublicMessage(
                    text = pendingChat,
                    colour = 0,
                    effect = 0,
                    clanType = null,
                    modIcon = player.modLevel.clientCode,
                    autoTyper = false,
                    pattern = null,
                )
        }

        // Auto toggle run speed based on energy thresholds
        if (player.runEnergy >= 2000 && player.varMoveSpeed != MoveSpeed.Run) {
            player.varMoveSpeed = MoveSpeed.Run
        }

        val gp = player.inv.firstOrNull { it?.id == coinsId }?.count ?: 0

        val view =
            BotPlayerView(
                x = coords.x,
                z = coords.z,
                level = coords.level,
                inCombat = player.isInCombat(),
                animating = player.pendingSequence != EntitySeq.NULL,
                playerList = playerList,
                gpCount = gp,
            )

        if (view.inCombat || view.animating) return

        // Auto equip best gear from inventory visually every 50 ticks
        if (state.ticksAtCurrentPos % 50 == 0) {
            autoEquipBestGear(player)
        }

        // Check QA override mode
        val qaNode = state.qaTaskNode
        if (qaNode != null) {
            val status = qaNode.execute(player, state)
            if (status == NodeStatus.SUCCESS || status == NodeStatus.FAILURE) {
                player.publicMessage =
                    PublicMessage(
                        text = "QA Task [${state.qaTaskName}] complete: $status!",
                        colour = 0,
                        effect = 0,
                        clanType = null,
                        modIcon = player.modLevel.clientCode,
                        autoTyper = false,
                        pattern = null,
                    )
                state.qaTaskNode = null
                state.qaTaskName = null
            }
            return
        }

        // 1. Evaluate Utility if no active goal
        if (!state.goalStack.hasActiveGoal()) {
            val (tree, name) = state.personality.pickGoal(view, state)
            state.goalStack.setTree(tree)
            state.goalStack.playerListContext = playerList
            state.lastActionName = name

            // Capture trajectory on new decision
            val snapshot = TrajectoryCapture.snapshot(player, state.personality)
            trajectory.capture(tick, snapshot, state.def.planner, state.lastActionName)
            state.decisionCaptured = true
        }

        // 2. Tick the active goal tree
        state.goalStack.tick(player, state)
    }

    private fun autoEquipBestGear(player: Player) {
        val wcLevel = player.stat("stat.woodcutting")
        val miningLevel = player.stat("stat.mining")
        val attackLevel = player.stat("stat.attack")

        val bestAxe =
            org.rsmod.content.other.progressivebots.economy.ProgressionRegistry.getBestAxe(wcLevel)
        val bestPick =
            org.rsmod.content.other.progressivebots.economy.ProgressionRegistry.getBestPickaxe(
                miningLevel
            )
        val bestWeapon =
            org.rsmod.content.other.progressivebots.economy.ProgressionRegistry.getBestWeapon(
                attackLevel
            )

        val candidates = listOf("obj.$bestAxe", "obj.$bestPick", "obj.$bestWeapon")

        for (candidate in candidates) {
            val cacheObj =
                dev.openrune.ServerCacheManager.getItem(
                    candidate.asRSCM(dev.openrune.rscm.RSCMType.OBJ)
                ) ?: continue
            val itemId = cacheObj.id

            val currentRightHand = player.righthand
            if (currentRightHand != null && currentRightHand.id == itemId) {
                continue
            }

            var invSlot = -1
            for (i in player.inv.indices) {
                val obj = player.inv[i]
                if (obj != null && obj.id == itemId) {
                    invSlot = i
                    break
                }
            }

            if (invSlot != -1) {
                val oldRightHand = player.righthand
                player.righthand = player.inv[invSlot]
                player.inv[invSlot] = oldRightHand
                break
            }
        }
    }

    companion object {
        private val logger = com.github.michaelbull.logging.InlineLogger()

        private val coinsId: Int by lazy {
            dev.openrune.ServerCacheManager.getItem(
                    "obj.coins".asRSCM(dev.openrune.rscm.RSCMType.OBJ)
                )
                ?.id ?: 995
        }
    }
}
