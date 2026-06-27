package org.rsmod.content.other.progressivebots

/**
 * Bot planner archetype — defines the bot's behavior profile.
 *
 * Each archetype drives what decisions the bot makes each tick:
 * - **Skiller**: Focuses on gathering skills (woodcutting, mining, fishing, etc.)
 * - **Fighter**: Combat-focused (attack NPCs, train combat skills)
 * - **Balanced**: Mix of skills + combat + questing
 * - **Social**: Walks around populated areas, chats, emotes
 * - **Vendor**: Buys low, sells high, manages shop inventory
 * - **PKer**: Hunts players in the Wilderness
 */
enum class BotPlanner {
    Skiller,
    Fighter,
    Balanced,
    Social,
    Vendor,
    PKer,
}
