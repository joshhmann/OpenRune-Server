package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankerTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_banker") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Welcome! I'm here to help you with banking.")
        chatPlayer(neutral, "What do I need to know?")
        when (
            choice2(
                "How does banking work?",
                1,
                "Thanks, goodbye!",
                2,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "You can store your items in the bank for safe " +
                        "keeping. Just use the bank booth or speak " +
                        "with a banker to access your account.",
                )
                chatNpc(
                    neutral,
                    "The bank on the second floor of the castle " +
                        "will serve you well. You can deposit and " +
                        "withdraw items whenever you need them.",
                )
                goodbyeDialogue()
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the help!")
        chatNpc(happy, "Anytime! Happy banking!")
    }
}
