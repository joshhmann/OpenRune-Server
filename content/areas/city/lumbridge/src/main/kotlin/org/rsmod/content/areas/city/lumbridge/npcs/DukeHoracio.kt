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
        val runeMystState = vars["varp.runemysteries"]

        chatNpc(
            neutral,
            "Greetings, adventurer. I am Duke Horacio, ruler of Lumbridge " +
                "and these parts.",
        )
        chatPlayer(neutral, "Hello, Your Excellency.")

        // Rune Mysteries quest — not started yet
        if (runeMystState <= 0) {
            chatNpc(
                neutral,
                "Welcome to my castle. There's much to see and do in Lumbridge. " +
                    "If you need anything, just ask around.",
            )
            when (
                choice3(
                    "Ask about the castle",
                    1,
                    "Do you have any quests for me?",
                    2,
                    "Goodbye",
                    3,
                )
            ) {
                1 -> castleDialogue(npc)
                2 -> runeMysteriesQuestStart()
                3 -> goodbyeDialogue()
            }
        } else if (runeMystState <= 3 && runeMystState > 0) {
            // Quest in progress
            questInProgressDialogue()
        } else {
            // Quest complete (or other varp value)
            questCompleteDialogue()
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
                "Do you have any quests for me?",
                1,
                "Thank you, that's all.",
                2,
            )
        ) {
            1 -> runeMysteriesQuestStart()
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.runeMysteriesQuestStart() {
        chatPlayer(quiz, "Do you have any quests for me?")
        chatNpc(
            neutral,
            "As a matter of fact, I do. This talisman was found in the " +
                "old Wizards' Tower ruins. I'd like you to take it to " +
                "Archmage Sedridor at the Wizards' Tower.",
        )
        chatNpc(
            neutral,
            "He's conducting research into the old incantations, and " +
                "this may be just what he needs.",
        )
        access.invAdd(access.inv, "obj.air_talisman")
        vars["varp.runemysteries"] = 1
        chatPlayer(happy, "I'll take it to him right away!")
        chatNpc(happy, "Thank you, adventurer. I'm sure he'll be most grateful.")
    }

    private suspend fun Dialogue.questInProgressDialogue() {
        chatNpc(
            neutral,
            "Have you delivered that talisman to Archmage Sedridor yet?",
        )
        chatPlayer(neutral, "I'm working on it!")
        chatNpc(happy, "Good, good. Take your time — this research is important.")
    }

    private suspend fun Dialogue.questCompleteDialogue() {
        chatNpc(
            neutral,
            "Welcome to my castle. There's much to see and do in Lumbridge. " +
                "If you need anything, just ask around.",
        )
        when (choice2("Ask about the castle", 1, "Goodbye", 2)) {
            1 -> {
                chatPlayer(quiz, "Can you tell me about this castle?")
                chatNpc(
                    neutral,
                    "Lumbridge Castle has stood for centuries... ah, I already " +
                        "told you all about it, didn't I?",
                )
                chatNpc(happy, "I'm glad you helped with that talisman business.")
                goodbyeDialogue()
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Goodbye, Your Excellency.")
        chatNpc(happy, "Farewell, adventurer. May your travels be safe.")
    }
}
