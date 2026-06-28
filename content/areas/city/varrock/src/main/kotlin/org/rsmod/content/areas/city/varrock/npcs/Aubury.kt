package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Aubury : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aubury") { auburyDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.auburyDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        val runeMystState = vars["varp.runemysteries"]

        chatNpc(happy, "Welcome to my rune shop. Can I help you?")
        chatPlayer(neutral, "Hello.")

        // Rune Mysteries — if player has the research package
        if (runeMystState == 2 && access.inv.count("obj.research_package") > 0) {
            deliverPackageDialogue()
        } else {
            standardDialogue()
        }
    }

    private suspend fun Dialogue.deliverPackageDialogue() {
        chatNpc(
            quiz,
            "Ah, I see you have a package from Archmage Sedridor! " +
                "Our research into the essence mine incantation is nearly complete.",
        )
        chatPlayer(happy, "He asked me to deliver this to you.")
        access.invDel(access.inv, "obj.research_package")
        vars["varp.runemysteries"] = 3
        access.invAdd(access.inv, "obj.research_notes")
        chatNpc(
            neutral,
            "Splendid! I've been working on something similar here. " +
                "Please, take these research notes back to Sedridor. " +
                "Together, they should complete the incantation.",
        )
        chatNpc(
            happy,
            "Would you like a cup of tea before you go? It's quite " +
                "a walk back to the Wizards' Tower.",
        )
        when (
            choice2(
                "Yes, please!",
                1,
                "No thanks, I'd better get going.",
                2,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, please!")
                chatNpc(happy, "Here you go!")
                chatPlayer(happy, "Aaah, that hit the spot.")
                chatNpc(happy, "My special blend. Always revitalising!")
            }
            2 -> {
                chatPlayer(happy, "No thanks, I'd better get going.")
                chatNpc(happy, "Safe travels, then!")
            }
        }
    }

    private suspend fun Dialogue.standardDialogue() {
        when (
            choice2(
                "Do you sell runes?",
                1,
                "Goodbye.",
                2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Do you sell runes?")
                chatNpc(neutral, "Of course! I have a wide selection. Feel free to browse.")
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Goodbye.")
        chatNpc(happy, "Come back if you need any runes!")
    }
}
