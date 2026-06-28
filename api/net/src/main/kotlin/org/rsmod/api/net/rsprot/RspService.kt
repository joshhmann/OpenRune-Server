package org.rsmod.api.net.rsprot

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.api.NetworkService
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.server.services.Service

@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class RspService
@Inject
constructor(private val service: NetworkService<Player>, private val config: ServerConfig) :
    Service {
    private val logger = InlineLogger()

    override suspend fun startup() {
        logger.info {
            "Starting network service on port ${config.gamePort} " +
                "(revision=${config.revision}, world=${config.world})"
        }
        service.start()
        logger.info { "Network service started on port ${config.gamePort}." }
    }

    override suspend fun shutdown() {
        logger.info { "Attempting to shut down network service." }
        try {
            service.shutdown()
            logger.info { "Network service successfully shut down." }
        } catch (t: Throwable) {
            logger.error(t) { "Network service failed to shut down." }
        }
    }
}
