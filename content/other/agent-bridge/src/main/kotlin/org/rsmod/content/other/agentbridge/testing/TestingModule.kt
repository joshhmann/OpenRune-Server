package org.rsmod.content.other.agentbridge.testing

import org.rsmod.module.ExtendedModule

class TestingModule : ExtendedModule() {
    override fun bind() {
        bindInstance<SaveStateManager>()
        bindInstance<TestResultReporter>()
        bindInstance<ActionRetry>()
        LearningDocs.initializeDefaults()
    }
}
