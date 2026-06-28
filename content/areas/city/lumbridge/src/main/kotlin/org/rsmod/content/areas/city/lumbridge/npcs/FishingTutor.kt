package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishingTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_fishing") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello! Interested in fishing?")
        chatPlayer(neutral, "Yes, tell me about it!")
        when (
            choice2(
                "Tell me about Fishing.",
                1,
                "Not now, thanks.",
                2,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Fishing is a relaxing way to gather food. You'll " +
                        "need a fishing rod and bait, or a net for " +
                        "smaller fish. You can fish in rivers, seas, " +
                        "and even some dungeons.",
                )
                chatNpc(
                    neutral,
                    "The River Lum just south of here has plenty of " +
                        "fish. You can buy a fishing rod and net from " +
                        "the general store in Lumbridge.",
                )
                goodbyeDialogue()
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the advice!")
        chatNpc(happy, "Tight lines!")
    }
}
