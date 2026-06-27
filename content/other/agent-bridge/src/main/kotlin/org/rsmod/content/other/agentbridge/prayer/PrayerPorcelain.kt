package org.rsmod.content.other.agentbridge.prayer

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.annotations.InternalApi
import org.rsmod.content.other.agentbridge.AgentBridgeServer
import org.rsmod.content.other.agentbridge.BotAction
import org.rsmod.content.other.agentbridge.PrayerName
import org.rsmod.content.other.agentbridge.PrayerState
import org.rsmod.content.other.agentbridge.porcelain.BotPorcelain
import org.rsmod.game.entity.Player

/**
 * High-level prayer porcelain actions for bot automation. Provides convenient methods for managing
 * prayers.
 */
@Singleton
class PrayerPorcelain @Inject constructor(private val server: AgentBridgeServer) {
    private val logger = InlineLogger()

    companion object {
        /** Prayer varp indices in the client. */
        private const val PRAYER_VARP_START = 83 // Standard prayer varp block start

        /** Low prayer threshold for alerts. */
        private const val LOW_PRAYER_THRESHOLD = 10
    }

    /**
     * Toggles a prayer by name or index.
     *
     * @param player The player
     * @param prayerName Prayer name (e.g., "PROTECT_FROM_MELEE")
     * @param prayerIndex Prayer index (0-14), used if name not provided
     * @return Result of the operation
     */
    fun togglePrayer(
        player: Player,
        prayerName: String? = null,
        prayerIndex: Int = -1,
    ): BotPorcelain.PorcelainResult {
        val prayer =
            resolvePrayer(prayerName, prayerIndex)
                ?: return BotPorcelain.PorcelainResult(
                    success = false,
                    message = "Unknown prayer: ${prayerName ?: "index $prayerIndex"}",
                )

        // Check level requirement
        if (player.prayerLvl < prayer.level) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message =
                    "Prayer ${prayer.name} requires level ${prayer.level}, you have ${player.prayerLvl}",
            )
        }

        queueAction(player, BotAction.TogglePrayer(prayer.name, prayer.index))

        val isActive = isPrayerActive(player, prayer.index)
        val action = if (isActive) "Deactivating" else "Activating"

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "$action ${prayer.name.replace("_", " ")}",
        )
    }

    /**
     * Activates a specific prayer.
     *
     * @param player The player
     * @param prayerName Prayer name
     * @param allowToggle If true, won't error if already active
     * @return Result of the operation
     */
    fun activatePrayer(
        player: Player,
        prayerName: String,
        allowToggle: Boolean = true,
    ): BotPorcelain.PorcelainResult {
        val prayer =
            PrayerName.byName(prayerName)
                ?: return BotPorcelain.PorcelainResult(
                    success = false,
                    message = "Unknown prayer: $prayerName",
                )

        // Check if already active
        if (isPrayerActive(player, prayer.index)) {
            if (allowToggle) {
                return BotPorcelain.PorcelainResult(
                    success = true,
                    message = "${prayer.name.replace("_", " ")} is already active",
                )
            }
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "${prayer.name.replace("_", " ")} is already active",
            )
        }

        // Check level requirement
        if (player.prayerLvl < prayer.level) {
            return BotPorcelain.PorcelainResult(
                success = false,
                message = "Prayer ${prayer.name} requires level ${prayer.level}",
            )
        }

        queueAction(player, BotAction.ActivatePrayer(prayer.name, allowToggle))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Activating ${prayer.name.replace("_", " ")}",
        )
    }

    /**
     * Deactivates a specific prayer.
     *
     * @param player The player
     * @param prayerName Prayer name
     * @return Result of the operation
     */
    fun deactivatePrayer(player: Player, prayerName: String): BotPorcelain.PorcelainResult {
        val prayer =
            PrayerName.byName(prayerName)
                ?: return BotPorcelain.PorcelainResult(
                    success = false,
                    message = "Unknown prayer: $prayerName",
                )

        if (!isPrayerActive(player, prayer.index)) {
            return BotPorcelain.PorcelainResult(
                success = true,
                message = "${prayer.name.replace("_", " ")} is already inactive",
            )
        }

        queueAction(player, BotAction.DeactivatePrayer(prayer.name))

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Deactivating ${prayer.name.replace("_", " ")}",
        )
    }

    /**
     * Deactivates all prayers.
     *
     * @param player The player
     * @return Result of the operation
     */
    fun deactivateAllPrayers(player: Player): BotPorcelain.PorcelainResult {
        val activeCount = getActivePrayers(player).size

        if (activeCount == 0) {
            return BotPorcelain.PorcelainResult(
                success = true,
                message = "No prayers are currently active",
            )
        }

        queueAction(player, BotAction.DeactivateAllPrayers)

        return BotPorcelain.PorcelainResult(
            success = true,
            message = "Deactivating all $activeCount prayers",
        )
    }

    /**
     * Gets the current prayer state.
     *
     * @param player The player
     * @return Prayer state snapshot
     */
    @OptIn(InternalApi::class)
    fun getPrayerState(player: Player): PrayerState {
        val prayerPoints = player.prayerPoints
        val maxPrayer = player.prayerLvl * 10
        val prayerLevel = player.prayerLvl
        val activePrayers = getActivePrayers(player)
        val drainRate = calculateDrainRate(activePrayers)

        return PrayerState(
            prayerPoints = prayerPoints,
            maxPrayerPoints = maxPrayer,
            prayerLevel = prayerLevel,
            activePrayers = activePrayers.mapValues { it.value.name },
            drainRate = drainRate,
        )
    }

    /**
     * Activates the best available combat prayer.
     *
     * @param player The player
     * @param type Type of combat prayer to activate
     * @return Result of the operation
     */
    fun activateBestCombatPrayer(
        player: Player,
        type: BotAction.CombatPrayerType = BotAction.CombatPrayerType.MELEE,
    ): BotPorcelain.PorcelainResult {
        val availablePrayers = PrayerName.availableAtLevel(player.prayerLvl)

        val bestPrayer =
            when (type) {
                BotAction.CombatPrayerType.MELEE -> {
                    // Prefer: Ultimate Strength > Superhuman Strength > Burst of Strength
                    availablePrayers.find { it == PrayerName.ULTIMATE_STRENGTH }
                        ?: availablePrayers.find { it == PrayerName.SUPERHUMAN_STRENGTH }
                        ?: availablePrayers.find { it == PrayerName.BURST_OF_STRENGTH }
                }
                BotAction.CombatPrayerType.DEFENSE -> {
                    // Prefer: Steel Skin > Rock Skin > Thick Skin
                    availablePrayers.find { it == PrayerName.STEEL_SKIN }
                        ?: availablePrayers.find { it == PrayerName.ROCK_SKIN }
                        ?: availablePrayers.find { it == PrayerName.THICK_SKIN }
                }
                BotAction.CombatPrayerType.PROTECTION -> {
                    // Return list of available protection prayers
                    return BotPorcelain.PorcelainResult(
                        success = true,
                        message =
                            "Available protection prayers: " +
                                listOfNotNull(
                                        availablePrayers.find {
                                            it == PrayerName.PROTECT_FROM_MELEE
                                        },
                                        availablePrayers.find {
                                            it == PrayerName.PROTECT_FROM_MISSILES
                                        },
                                        availablePrayers.find {
                                            it == PrayerName.PROTECT_FROM_MAGIC
                                        },
                                    )
                                    .joinToString(", ") { it.name.replace("_", " ") },
                    )
                }
            }

        return if (bestPrayer != null) {
            activatePrayer(player, bestPrayer.name)
        } else {
            BotPorcelain.PorcelainResult(
                success = false,
                message = "No ${type.name.lowercase()} prayers available at your level",
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Private Helpers
    // ---------------------------------------------------------------------------------------------

    /** Resolves a prayer from name or index. */
    private fun resolvePrayer(name: String?, index: Int): PrayerName? {
        return if (name != null) {
            PrayerName.byName(name)
        } else if (index >= 0) {
            PrayerName.byIndex(index)
        } else {
            null
        }
    }

    /** Checks if a prayer is currently active. */
    private fun isPrayerActive(player: Player, index: Int): Boolean {
        // Prayer state is typically stored in varps
        // This is a simplified check - actual implementation would read varp
        return false // Placeholder - needs actual varp reading
    }

    /** Gets all currently active prayers. */
    private fun getActivePrayers(player: Player): Map<Int, PrayerName> {
        // This would read the prayer varps and return active prayers
        // Placeholder implementation
        return emptyMap()
    }

    /** Calculates total drain rate for active prayers. */
    private fun calculateDrainRate(activePrayers: Map<Int, PrayerName>): Double {
        return activePrayers.values.map { it.drainRate }.sum()
    }

    /** Queues an action via reflection. */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun queueAction(player: Player, action: BotAction) {
        try {
            val playerKey = player.avatar.name.lowercase()
            val pendingActionsField = server.javaClass.getDeclaredField("pendingActions")
            pendingActionsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val pendingActions =
                pendingActionsField.get(server)
                    as
                    java.util.concurrent.ConcurrentHashMap<
                        String,
                        java.util.concurrent.ConcurrentLinkedQueue<BotAction>,
                    >
            val queue =
                pendingActions.getOrPut(playerKey) { java.util.concurrent.ConcurrentLinkedQueue() }
            queue.offer(action)
        } catch (e: Exception) {
            logger.error { "[PrayerPorcelain] Failed to queue action: ${e.message}" }
        }
    }

    /** Extension property for prayer level. */
    @OptIn(InternalApi::class)
    private val Player.prayerLvl: Int
        get() = this.statMap.getBaseLevel("stat.prayer").toInt() and 0xFF

    /** Extension property for prayer points. */
    @OptIn(InternalApi::class)
    private val Player.prayerPoints: Int
        get() = (this.statMap.getCurrentLevel("stat.prayer").toInt() and 0xFF) * 10
}
