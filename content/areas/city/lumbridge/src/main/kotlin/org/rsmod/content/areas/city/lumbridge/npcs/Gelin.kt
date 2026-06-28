package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Gelin : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.deadman_gelin") { gelinDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.gelinDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello there! Fine day for a walk in the graveyard, isn't it?")
        chatPlayer(confused, "Is it?")
        when (
            choice3(
                "Tell me about this place.",
                1,
                "Do you know any history?",
                2,
                "Goodbye.",
                3,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "This is the Lumbridge graveyard. It's been here " +
                        "as long as anyone can remember. The people of " +
                        "Lumbridge have buried their dead here for " +
                        "generations.",
                )
                chatNpc(
                    shifty,
                    "Some say the graveyard is haunted... but I'm " +
                        "sure it's just the wind.",
                )
                goodbyeDialogue()
            }
            2 -> {
                chatNpc(
                    neutral,
                    "Lumbridge has a long and storied history. The " +
                        "castle was built over three hundred years ago " +
                        "by the first Duke of Lumbridge.",
                )
                chatNpc(
                    neutral,
                    "The town has survived wars, plagues, and even " +
                        "a dragon attack! Though that was before " +
                        "my time, of course.",
                )
                goodbyeDialogue()
            }
            3 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Goodbye!")
        chatNpc(happy, "Take care, and mind the gravestones!")
    }
}
