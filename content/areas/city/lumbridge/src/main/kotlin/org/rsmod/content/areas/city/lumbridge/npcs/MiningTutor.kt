package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MiningTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_mining") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello there! Here to learn about mining?")
        chatPlayer(neutral, "Yes, what can you tell me?")
        when (
            choice2(
                "Tell me about Mining.",
                1,
                "Thanks, goodbye!",
                2,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Mining is a great way to make money and gather " +
                        "resources. You'll need a pickaxe to mine ore " +
                        "from rocks. The better your pickaxe, the faster " +
                        "you'll mine.",
                )
                chatNpc(
                    neutral,
                    "The Lumbridge Swamp mine has tin and copper ore, " +
                        "which can be smelted into bronze bars at a furnace. " +
                        "Speak with the Smithing Apprentice in town for " +
                        "more details.",
                )
                goodbyeDialogue()
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the tips!")
        chatNpc(happy, "Happy mining!")
    }
}
