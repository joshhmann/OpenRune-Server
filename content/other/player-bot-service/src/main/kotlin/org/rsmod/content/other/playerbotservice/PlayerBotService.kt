package org.rsmod.content.other.playerbotservice

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.inv.map.InvMapInit
import org.rsmod.api.pw.hash.PasswordHashing
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

@Singleton
class PlayerBotService
@Inject
constructor(
    private val playerList: PlayerList,
    private val invMapInit: InvMapInit,
    private val database: GameDatabase,
    private val repository: CharacterAccountRepository,
    private val pipelines: Set<CharacterDataStage.Pipeline>,
    private val applier: CharacterAccountApplier,
    private val passwordHashing: PasswordHashing,
) {
    private val logger = com.github.michaelbull.logging.InlineLogger()

    companion object {
        val BOT_USER_ID_OFFSET = 10_000L
        private val BOT_PASSWORD = "bot_pass"
    }

    fun spawnBot(displayName: String, x: Int = 3222, z: Int = 3222): Player? {
        val slot =
            playerList.nextFreeSlot()
                ?: run {
                    logger.warn { "[PlayerBot] No free player slots available" }
                    return null
                }

        val dbName = displayName.lowercase()

        // Register account + character in the database, load full metadata
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

        // Override spawn position (the applier may have set a default from the DB)
        player.coords = CoordGrid(x, z, 0)

        invMapInit.init(player)
        playerList[slot] = player

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
        playerList.remove(player.slotId)
        logger.info { "[PlayerBot] Despawned '${name}'" }
        return true
    }

    fun findBot(name: String): Player? {
        val lower = name.lowercase()
        for (player in playerList) {
            if (player != null && player.avatar.name.lowercase() == lower) {
                return player
            }
        }
        return null
    }

    fun botCount(): Int {
        var count = 0
        for (player in playerList) {
            if (player != null && player.client is NoopClient) {
                count++
            }
        }
        return count
    }
}
