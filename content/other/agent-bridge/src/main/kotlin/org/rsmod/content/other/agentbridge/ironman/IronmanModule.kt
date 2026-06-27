package org.rsmod.content.other.agentbridge.ironman

import com.google.inject.AbstractModule
import com.google.inject.Scopes

/**
 * Guice module for AgentBridge ironman mode.
 *
 * Provides ironman mode configuration and enforcement for legitimate bot gameplay.
 */
class IronmanModule : AbstractModule() {
    override fun configure() {
        bind(IronmanMode::class.java).`in`(Scopes.SINGLETON)
    }
}
