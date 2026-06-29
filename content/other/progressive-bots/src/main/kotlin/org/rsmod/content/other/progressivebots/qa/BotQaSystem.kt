package org.rsmod.content.other.progressivebots.qa

import org.rsmod.content.other.progressivebots.economy.ShopVisitNode
import org.rsmod.content.other.progressivebots.tree.BehaviorNode
import org.rsmod.content.other.progressivebots.tree.FightTreeBuilder
import org.rsmod.content.other.progressivebots.tree.GatherTreeBuilder
import org.rsmod.content.other.progressivebots.tree.ProductionTreeBuilder

/**
 * Registry for QA test tasks that progressive bots can execute on demand.
 *
 * Tasks are registered by name and can be triggered via:
 * - In-game `::botqa <username> <task>` command
 * - Programmatic invocation from AgentBridge or test runner
 *
 * Each task returns a [BehaviorNode] that will be ticked on the game thread
 * until it completes (SUCCESS or FAILURE).
 */
object BotQaSystem {
    private val tasks = mutableMapOf<String, () -> BehaviorNode>()

    init {
        // Original skill/combat tasks
        register("test_woodcutting") { GatherTreeBuilder.build("woodcutting") }
        register("test_mining") { GatherTreeBuilder.build("mining") }
        register("test_smithing") { ProductionTreeBuilder.build() }
        register("test_combat") { FightTreeBuilder.build() }
        register("test_shop") { ShopVisitNode() }

        // ===== LUMBRIDGE QA TASKS =====
        // NPC dialogue verification
        register("qa_npc_hans") { QaLumbridgeTests.buildNpcDialogueTest("Hans", 3222, 3219) }
        register("qa_npc_cook") { QaLumbridgeTests.buildNpcDialogueTest("Cook", 3209, 3216) }
        register("qa_npc_duke") { QaLumbridgeTests.buildNpcDialogueTest("Duke Horacio", 3214, 3224) }
        register("qa_npc_father_aereck") { QaLumbridgeTests.buildNpcDialogueTest("Father Aereck", 3241, 3207) }
        register("qa_npc_bob") { QaLumbridgeTests.buildNpcDialogueTest("Bob", 3231, 3203) }
        register("qa_npc_shopkeeper") { QaLumbridgeTests.buildNpcDialogueTest("Shopkeeper", 3214, 3246) }

        // Multi-NPC tour (visits all major NPCs in sequence)
        register("qa_lumbridge_npc_tour") { QaLumbridgeTests.buildNpcTourTest() }

        // Door interaction test
        register("qa_lumbridge_doors") { QaLumbridgeTests.buildDoorTest() }

        // Item test (spawn items, verify inventory)
        register("qa_item_test") { QaLumbridgeTests.buildItemTest() }

        // Quest state check
        register("qa_quest_cook_assistant") { QaLumbridgeTests.buildQuestTest("Cook's Assistant") }
        register("qa_quest_romeo_juliet") { QaLumbridgeTests.buildQuestTest("Romeo & Juliet") }
        register("qa_quest_rune_mysteries") { QaLumbridgeTests.buildQuestTest("Rune Mysteries") }
        register("qa_quest_restless_ghost") { QaLumbridgeTests.buildQuestTest("Restless Ghost") }
        register("qa_quest_sheep_shearer") { QaLumbridgeTests.buildQuestTest("Sheep Shearer") }

        // Full Lumbridge sweep (everything)
        register("qa_lumbridge_sweep") { QaLumbridgeTests.buildFullLumbridgeSweep() }
    }

    fun register(name: String, builder: () -> BehaviorNode) {
        tasks[name.lowercase()] = builder
    }

    fun getTask(name: String): BehaviorNode? {
        return tasks[name.lowercase()]?.invoke()
    }

    fun getRegisteredTasks(): Set<String> = tasks.keys

    /**
     * Get task names categorized by prefix for easier discovery.
     */
    fun getTaskCategories(): Map<String, List<String>> {
        val categories = mutableMapOf<String, MutableList<String>>()
        for (taskName in tasks.keys.sorted()) {
            val prefix = taskName.substringBefore("_").ifBlank { "other" }
            categories.getOrPut(prefix) { mutableListOf() }.add(taskName)
        }
        return categories
    }

    /** Get Lumbridge-specific QA tasks. */
    fun getQaTasks(): Set<String> = tasks.keys.filter { it.startsWith("qa_") }.toSet()

    /** Get count of all registered tasks. */
    fun taskCount(): Int = tasks.size
}
