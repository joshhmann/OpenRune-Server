package org.rsmod.content.other.agentbridge

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.runBlocking
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.pw.hash.PasswordHashing
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.player.isSuccess
import org.rsmod.events.EventBus
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.map.CoordGrid

/**
 * AgentBridge-owned bot lifecycle.
 *
 * These are not ambient playerbots. They exist only for LLM/QA agents and are the only players
 * AgentBridge is allowed to tap, soft-timer, or directly control.
 */
@Singleton
public class AgentBotService
@Inject
constructor(
    private val playerRegistry: PlayerRegistry,
    private val eventBus: EventBus,
    private val database: GameDatabase,
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<CharacterDataStage.Pipeline>,
    private val passwordHashing: PasswordHashing,
) {
    private val logger = com.github.michaelbull.logging.InlineLogger()
    private val activeAgentBots: MutableSet<Player> =
        Collections.newSetFromMap(IdentityHashMap<Player, Boolean>())

    fun spawn(name: String, x: Int = 3222, z: Int = 3222): Player? {
        val slot =
            playerRegistry.nextFreeSlot()
                ?: run {
                    logger.warn { "[AgentBridge] No free player slots available for agent bot" }
                    return null
                }

        val dbName = name.lowercase()
        val metadata =
            loadOrCreateAgentAccount(dbName)
                ?: run {
                    logger.error { "[AgentBridge] Failed to load/create agent bot '$name'" }
                    return null
                }

        val player = Player(client = NoopClient)
        for (transform in metadata.transformers) {
            transform.apply(player)
        }

        val existing = playerRegistry.findOnlineByCharacterId(player.characterId)
        if (existing != null) {
            if (existing !in activeAgentBots) {
                logger.warn {
                    "[AgentBridge] '$name' is already online outside AgentBridge " +
                        "(cid=${player.characterId}, slot=${existing.slotId})"
                }
                return null
            }
            return existing
        }

        player.coords = CoordGrid(x, z, 0)
        player.slotId = slot
        val result = playerRegistry.add(player)
        if (!result.isSuccess()) {
            logger.warn { "[AgentBridge] Failed to register agent bot '$name': $result" }
            return null
        }

        activeAgentBots += player
        eventBus.publish(SessionStateEvent.Login(player))
        eventBus.publish(SessionStateEvent.EngineLogin(player))
        logger.info {
            "[AgentBridge] Spawned agent bot '$name' " +
                "(db=$dbName, cid=${player.characterId}, slot=$slot) at ($x, $z)"
        }
        return player
    }

    fun despawn(name: String): Boolean {
        val player = find(name) ?: return false
        activeAgentBots -= player
        player.forceDisconnect = true
        logger.info { "[AgentBridge] Queued agent bot '$name' for logout" }
        return true
    }

    fun find(name: String): Player? {
        val lower = name.lowercase()
        for (player in playerRegistry.playerList) {
            if (player in activeAgentBots && player.avatar.name.lowercase() == lower) {
                return player
            }
        }
        return null
    }

    fun count(): Int {
        var count = 0
        for (player in playerRegistry.playerList) {
            if (player in activeAgentBots) {
                count++
            }
        }
        return count
    }

    private fun loadOrCreateAgentAccount(name: String): CharacterMetadataList? {
        return try {
            runBlocking {
                database.withTransaction { connection ->
                    val existing = repository.selectAndCreateMetadataList(connection, name)
                    if (existing != null) {
                        for (pipeline in pipelines) {
                            pipeline.append(connection, existing)
                        }
                        return@withTransaction existing
                    }

                    val passwordHash = passwordHashing.hash(AGENT_BOT_PASSWORD.toCharArray())
                    val accountId =
                        repository.insertOrSelectAccountId(connection, name, passwordHash)
                            ?: return@withTransaction null
                    repository.insertAndSelectCharacterId(connection, accountId)
                        ?: return@withTransaction null
                    val metadata =
                        repository.selectAndCreateMetadataList(connection, name)
                            ?: return@withTransaction null

                    for (pipeline in pipelines) {
                        pipeline.append(connection, metadata)
                    }

                    metadata
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[AgentBridge] Failed to load/create agent bot '$name'" }
            null
        }
    }

    private companion object {
        private const val AGENT_BOT_PASSWORD = "agent_bot_pass"
    }
}
