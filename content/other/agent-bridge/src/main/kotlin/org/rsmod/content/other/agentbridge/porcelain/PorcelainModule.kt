package org.rsmod.content.other.agentbridge.porcelain

import com.google.inject.AbstractModule
import com.google.inject.Scopes

/**
 * Guice module for AgentBridge porcelain actions.
 *
 * Provides high-level bot automation utilities that wrap low-level actions with convenient target
 * resolution, pathfinding, and event waiting.
 */
class PorcelainModule : AbstractModule() {
    override fun configure() {
        bind(ActionHelper::class.java).`in`(Scopes.SINGLETON)
        bind(BotPorcelain::class.java).`in`(Scopes.SINGLETON)
    }
}
