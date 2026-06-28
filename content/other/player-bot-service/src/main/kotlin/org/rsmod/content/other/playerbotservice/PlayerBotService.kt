package org.rsmod.content.other.playerbotservice

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
 * DB-backed bot lifecycle bridge.
 *
 * Bots must use the same account metadata, registry, session event, save, and logout lifecycle as
 * normal players. Bot features must not special-case or weaken core human login/logout behavior,
 * and must not mutate PlayerList directly.
 */
@Singleton
class PlayerBotService
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
    private val activeBots: MutableSet<Player> =
        Collections.newSetFromMap(IdentityHashMap<Player, Boolean>())

    companion object {
        private val BOT_PASSWORD = "bot_pass"
    }

    fun spawnBot(displayName: String, x: Int = 3222, z: Int = 3222): Player? {
        val slot =
            playerRegistry.nextFreeSlot()
                ?: run {
                    logger.warn { "[PlayerBot] No free player slots available" }
                    return null
                }

        val dbName = displayName.lowercase()

        // Load existing bot metadata first; create the account and character only once.
        val metadata =
            registerBotAccount(dbName)
                ?: run {
                    logger.error { "[PlayerBot] Failed to register account for '$displayName'" }
                    return null
                }

        val player = Player(client = NoopClient)

        // Apply all transforms from metadata (sets identity fields, stats, inventories, equipment)
        for (transform in metadata.transformers) {
            transform.apply(player)
        }

        val existing = playerRegistry.findOnlineByCharacterId(player.characterId)
        if (existing != null) {
            if (existing !in activeBots) {
                logger.warn {
                    "[PlayerBot] '$displayName' is already online but was not spawned by " +
                        "PlayerBotService (cid=${player.characterId}, slot=${existing.slotId})"
                }
                return null
            }
            logger.warn {
                "[PlayerBot] '$displayName' is already online " +
                    "(cid=${player.characterId}, slot=${existing.slotId})"
            }
            return existing
        }

        // Override spawn position (the applier may have set a default from the DB)
        player.coords = CoordGrid(x, z, 0)

        // Keep bots on the normal player registration path: this assigns uid and publishes
        // SessionStateEvent.Initialize through PlayerRegistry.
        player.slotId = slot
        val result = playerRegistry.add(player)
        if (!result.isSuccess()) {
            logger.warn { "[PlayerBot] Failed to register '$displayName': $result" }
            return null
        }
        activeBots += player
        eventBus.publish(SessionStateEvent.Login(player))
        eventBus.publish(SessionStateEvent.EngineLogin(player))

        logger.info {
            "[PlayerBot] Registered and spawned '$displayName' " +
                "(db=$dbName, uid=${player.userId}, cid=${player.characterId}, " +
                "aid=${player.accountId}) at ($x, $z) slot=$slot"
        }
        return player
    }

    private fun registerBotAccount(name: String): CharacterMetadataList? {
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

                    val passwordHash = passwordHashing.hash(BOT_PASSWORD.toCharArray())
                    val accountId =
                        repository.insertOrSelectAccountId(connection, name, passwordHash)
                            ?: return@withTransaction null
                    val characterId =
                        repository.insertAndSelectCharacterId(connection, accountId)
                            ?: return@withTransaction null
                    val metadataList =
                        repository.selectAndCreateMetadataList(connection, name)
                            ?: return@withTransaction null

                    for (pipeline in pipelines) {
                        pipeline.append(connection, metadataList)
                    }

                    metadataList
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "[PlayerBot] Failed to register account for '$name'" }
            null
        }
    }

    fun despawnBot(name: String): Boolean {
        val player = findBot(name) ?: return false
        activeBots -= player
        // Queue normal logout/save/delete processing; never remove directly from PlayerList.
        player.forceDisconnect = true
        logger.info { "[PlayerBot] Queued '${name}' for logout" }
        return true
    }

    fun findBot(name: String): Player? {
        val lower = name.lowercase()
        for (player in playerRegistry.playerList) {
            if (player in activeBots && player.avatar.name.lowercase() == lower) {
                return player
            }
        }
        return null
    }

    fun botCount(): Int {
        var count = 0
        for (player in playerRegistry.playerList) {
            if (player in activeBots) {
                count++
            }
        }
        return count
    }
}
