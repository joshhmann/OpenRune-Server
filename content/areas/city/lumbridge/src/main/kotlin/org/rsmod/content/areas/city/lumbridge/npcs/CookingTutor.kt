package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CookingTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_cooking") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello! Would you like to learn about cooking?")
        chatPlayer(neutral, "Yes, please!")
        when (
            choice2(
                "Tell me about Cooking.",
                1,
                "Thanks, goodbye!",
                2,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Cooking is an essential skill for any adventurer. " +
                        "You can catch fish, cook them over a fire or " +
                        "range, and eat them to restore health.",
                )
                chatNpc(
                    neutral,
                    "The castle kitchen has a range you can use. " +
                        "Start with shrimp or sardines — they're easy " +
                        "to catch and hard to burn!",
                )
                goodbyeDialogue()
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the tips!")
        chatNpc(happy, "Happy cooking!")
    }
}
