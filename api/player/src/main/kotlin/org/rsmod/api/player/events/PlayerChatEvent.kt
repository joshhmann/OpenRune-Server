package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public data class PlayerChatEvent(
    public val player: Player,
    public val text: String
) : UnboundEvent
