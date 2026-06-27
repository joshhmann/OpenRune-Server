package org.rsmod.content.other.agentbridge.grounditems

import com.google.inject.AbstractModule
import com.google.inject.Scopes

/**
 * Guice module for AgentBridge ground item porcelain.
 *
 * Provides ground item scanning and pickup utilities with on-demand scanning and caching.
 */
class GroundItemsModule : AbstractModule() {
    override fun configure() {
        bind(GroundItemPorcelain::class.java).`in`(Scopes.SINGLETON)
    }
}
