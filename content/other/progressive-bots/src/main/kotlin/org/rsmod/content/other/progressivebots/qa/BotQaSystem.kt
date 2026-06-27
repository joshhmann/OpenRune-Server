package org.rsmod.content.other.progressivebots.qa

import org.rsmod.content.other.progressivebots.tree.BehaviorNode
import org.rsmod.content.other.progressivebots.tree.GatherTreeBuilder
import org.rsmod.content.other.progressivebots.tree.ProductionTreeBuilder
import org.rsmod.content.other.progressivebots.tree.FightTreeBuilder
import org.rsmod.content.other.progressivebots.economy.ShopVisitNode

object BotQaSystem {
    private val tasks = mutableMapOf<String, () -> BehaviorNode>()

    init {
        register("test_woodcutting") { GatherTreeBuilder.build("woodcutting") }
        register("test_mining") { GatherTreeBuilder.build("mining") }
        register("test_smithing") { ProductionTreeBuilder.build() }
        register("test_combat") { FightTreeBuilder.build() }
        register("test_shop") { ShopVisitNode() }
    }

    fun register(name: String, builder: () -> BehaviorNode) {
        tasks[name.lowercase()] = builder
    }

    fun getTask(name: String): BehaviorNode? {
        return tasks[name.lowercase()]?.invoke()
    }

    fun getRegisteredTasks(): Set<String> = tasks.keys
}
