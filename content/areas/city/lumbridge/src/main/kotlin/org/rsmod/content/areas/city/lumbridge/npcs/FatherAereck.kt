package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FatherAereck : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.father_aereck") { fatherDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.fatherDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello, my child. Welcome to the Church of Lumbridge.")
        chatPlayer(neutral, "Hello, Father.")
        when (
            choice4(
                "Tell me about this church.",
                1,
                "Do you have any quests for me?",
                2,
                "Have you seen any ghosts around here?",
                3,
                "Goodbye, Father.",
                4,
            )
        ) {
            1 -> churchDialogue(npc)
            2 -> questHintDialogue(npc)
            3 -> ghostDialogue(npc)
            4 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.churchDialogue(npc: Npc) {
        chatPlayer(quiz, "Tell me about this church.")
        chatNpc(
            neutral,
            "This church has served the people of Lumbridge for many " +
                "generations. We hold services every Sunday, and anyone " +
                "is welcome to visit and pray.",
        )
        chatNpc(
            neutral,
            "If you ever need spiritual guidance, or just a quiet moment " +
                "of reflection, our doors are always open.",
        )
        chatPlayer(happy, "Thank you, Father.")
        goodbyeDialogue()
    }

    private suspend fun Dialogue.questHintDialogue(npc: Npc) {
        chatPlayer(quiz, "Do you have any quests for me?")
        chatNpc(
            neutral,
            "Not at the moment, but if you speak to the people of " +
                "Lumbridge, I'm sure you'll find someone in need of help.",
        )
        chatNpc(
            shifty,
            "I've heard the Duke sometimes has tasks for promising " +
                "adventurers, and the Cook in the castle kitchen has " +
                "been in quite a state lately.",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.ghostDialogue(npc: Npc) {
        chatPlayer(worried, "Have you seen any ghosts around here?")
        chatNpc(
            worried,
            "Ah, you must mean the poor soul that haunts our " +
                "graveyard. It's a tragic tale, really. " +
                "The ghost of a restless spirit wanders there.",
        )
        chatNpc(
            neutral,
            "If you wish to help him, you should speak with " +
                "Father Urhney. He lives in a small hut in the " +
                "Lumbridge Swamp, to the south-west. He may have " +
                "something that could help you communicate with " +
                "the ghost.",
        )
        chatPlayer(
            happy,
            "Thank you, Father. I'll look into it.",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Goodbye, Father.")
        chatNpc(happy, "May Saradomin watch over you, my child.")
    }
}
