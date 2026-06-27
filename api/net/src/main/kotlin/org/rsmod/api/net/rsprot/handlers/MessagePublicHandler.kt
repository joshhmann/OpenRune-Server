package org.rsmod.api.net.rsprot.handlers

import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import org.rsmod.api.player.events.PlayerChatEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PublicMessage

class MessagePublicHandler @Inject constructor(
    private val eventBus: EventBus
) : MessageHandler<MessagePublic> {
    override fun handle(player: Player, message: MessagePublic) {
        val publicMessage =
            PublicMessage(
                text = message.message,
                colour = message.colour,
                effect = message.effect,
                clanType = if (message.clanType == -1) null else message.clanType,
                modIcon = player.modLevel.clientCode,
                autoTyper = false,
                pattern = message.pattern?.asByteArray(),
            )
        player.publicMessage = publicMessage
        
        // Publish event for bots to hear chat messages
        eventBus.publish(PlayerChatEvent(player, message.message))
    }
}
