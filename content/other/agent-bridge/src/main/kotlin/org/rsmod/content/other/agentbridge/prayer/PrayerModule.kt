package org.rsmod.content.other.agentbridge.prayer

import com.google.inject.AbstractModule
import com.google.inject.Scopes

/**
 * Guice module for AgentBridge prayer porcelain.
 *
 * Provides prayer automation utilities for activating/deactivating prayers and monitoring prayer
 * points.
 */
class PrayerModule : AbstractModule() {
    override fun configure() {
        bind(PrayerPorcelain::class.java).`in`(Scopes.SINGLETON)
    }
}
