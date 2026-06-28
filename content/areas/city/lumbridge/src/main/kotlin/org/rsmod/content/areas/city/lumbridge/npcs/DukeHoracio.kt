package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DukeHoracio : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.duke_of_lumbridge") { dukeDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.dukeDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(
            neutral,
            "Greetings, adventurer. I am Duke Horacio, ruler of Lumbridge " +
                "and these parts.",
        )
        chatPlayer(neutral, "Hello, Your Excellency.")
        chatNpc(
            neutral,
            "Welcome to my castle. There's much to see and do in Lumbridge. " +
                "If you need anything, just ask around.",
        )
        when (choice2("Ask about the castle", 1, "Goodbye", 2)) {
            1 -> castleDialogue(npc)
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.castleDialogue(npc: Npc) {
        chatPlayer(quiz, "Can you tell me about this castle?")
        chatNpc(
            neutral,
            "Lumbridge Castle has stood for centuries. It guards the " +
                "crossing of the River Lum and serves as the seat of power " +
                "for the region.",
        )
        chatNpc(
            neutral,
            "Downstairs you'll find my kitchen, where my cook prepares " +
                "fine meals. The upper floors house my personal quarters " +
                "and the bank.",
        )
        chatNpc(
            happy,
            "Outside the castle walls, you'll find the town of Lumbridge " +
                "with various shops and services to help you on your journey.",
        )
        when (
            choice2(
                "Is there anything I can help with?",
                1,
                "Thank you, that's all.",
                2,
            )
        ) {
            1 -> helpDialogue(npc)
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.helpDialogue(npc: Npc) {
        chatNpc(
            neutral,
            "Not at the moment, but if you prove yourself worthy, " +
                "there may be tasks I can entrust to you.",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Goodbye, Your Excellency.")
        chatNpc(happy, "Farewell, adventurer. May your travels be safe.")
    }
}
