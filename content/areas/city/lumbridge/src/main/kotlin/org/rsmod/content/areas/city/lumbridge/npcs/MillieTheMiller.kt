package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MillieTheMiller : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.millie_the_miller") { startDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Hello there! I'm Millie, the miller. Need some help with the mill?")
            mainMenu()
        }

    private suspend fun Dialogue.mainMenu() {
        val choice = choice2("How do I make flour?", 1, "Goodbye.", 2)
        when (choice) {
            1 -> {
                chatPlayer(quiz, "How do I make flour?")
                chatNpc(
                    neutral,
                    "It's simple! Just go to the top floor and put some grain into the hopper.",
                )
                chatNpc(
                    neutral,
                    "Then operate the controls next to it. You can collect the flour from the bin on the ground floor.",
                )
                chatNpc(happy, "You'll need an empty pot to collect the flour, of course!")
            }
            2 -> {
                chatPlayer(neutral, "Goodbye.")
            }
        }
    }
}
