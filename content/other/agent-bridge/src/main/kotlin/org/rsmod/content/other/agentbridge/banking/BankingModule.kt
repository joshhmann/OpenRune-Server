package org.rsmod.content.other.agentbridge.banking

import com.google.inject.AbstractModule
import com.google.inject.Scopes

/**
 * Guice module for AgentBridge banking porcelain.
 *
 * Provides banking automation utilities for bot scripts, including bank location lookup and banking
 * operations.
 */
class BankingModule : AbstractModule() {
    override fun configure() {
        bind(BankPorcelain::class.java).`in`(Scopes.SINGLETON)
    }
}
