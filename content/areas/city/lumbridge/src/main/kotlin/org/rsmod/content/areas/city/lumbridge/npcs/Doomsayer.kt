package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Doomsayer : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.cws_doomsayer") { doomsayerDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.doomsayerDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(worried, "Woe betwixt thee, adventurer...")
        chatNpc(
            worried,
            "These are perilous times we live in. Danger lurks " +
                "behind every corner, and death awaits the careless!",
        )
        chatPlayer(confused, "Uh... are you okay?")
        chatNpc(
            shifty,
            "I am the Doomsayer. It is my solemn duty to warn " +
                "travellers of the many dangers that await them.",
        )
        when (
            choice3(
                "What dangers?",
                1,
                "Can you stop warning me?",
                2,
                "Goodbye, grim one.",
                3,
            )
        ) {
            1 -> dangersDialogue()
            2 -> toggleDialogue()
            3 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.dangersDialogue() {
        chatPlayer(quiz, "What dangers?")
        chatNpc(
            worried,
            "The Wilderness to the north is filled with cutthroats " +
                "and murderers. The swamps are home to terrible " +
                "monsters! And the dungeons... oh, the dungeons...",
        )
        chatNpc(
            shifty,
            "But if you're careful, and you keep your wits about you, " +
                "you might just survive.",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.toggleDialogue() {
        chatPlayer(neutral, "Can you stop warning me?")
        chatNpc(
            shifty,
            "If you wish to disable my warnings, you may. But do " +
                "not say I didn't warn you!",
        )
        chatNpc(
            neutral,
            "Say 'toggle' if you'd like me to stop, or 'continue' " +
                "if you wish to keep receiving my valuable advice.",
        )
        chatPlayer(happy, "I'll think about it.")
        goodbyeDialogue()
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Goodbye, Doomsayer.")
        chatNpc(worried, "May the gods protect you... you'll need it.")
    }
}
