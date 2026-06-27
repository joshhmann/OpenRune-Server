package org.rsmod.content.other.agentbridge.shops

import com.google.inject.AbstractModule
import com.google.inject.Scopes

/**
 * Guice module for AgentBridge shop porcelain.
 *
 * Provides shop automation utilities for buying and selling with automatic navigation to
 * shopkeepers.
 */
class ShopsModule : AbstractModule() {
    override fun configure() {
        bind(ShopPorcelain::class.java).`in`(Scopes.SINGLETON)
    }
}
