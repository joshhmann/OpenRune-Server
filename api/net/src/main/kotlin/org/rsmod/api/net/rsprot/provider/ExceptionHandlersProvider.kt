package org.rsmod.api.net.rsprot.provider

import com.github.michaelbull.logging.InlineLogger
import io.netty.channel.ChannelHandlerContext
import net.rsprot.protocol.api.ChannelExceptionHandler
import net.rsprot.protocol.api.IncomingGameMessageConsumerExceptionHandler
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.api.handlers.ExceptionHandlers
import net.rsprot.protocol.message.IncomingGameMessage
import org.rsmod.game.entity.Player

object ExceptionHandlersProvider {
    private val logger = InlineLogger()

    fun provide(): ExceptionHandlers<Player> {
        val channelHandler =
            ChannelExceptionHandler { ctx: ChannelHandlerContext, cause: Throwable ->
                logger.warn(cause) {
                    "Network channel exception from ${ctx.channel().remoteAddress()}: ${cause.message}"
                }
                throw cause
            }
        val messageHandler =
            IncomingGameMessageConsumerExceptionHandler {
                _: Session<Player>,
                message: IncomingGameMessage,
                throwable: Throwable ->
                logger.warn(throwable) {
                    "Game message handler exception for message=${message::class.simpleName}: " +
                        throwable.message
                }
                throw throwable
            }
        return ExceptionHandlers(channelHandler, messageHandler)
    }
}
