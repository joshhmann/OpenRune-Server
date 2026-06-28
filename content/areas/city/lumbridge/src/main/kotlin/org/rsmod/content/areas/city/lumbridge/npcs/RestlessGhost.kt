package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RestlessGhost : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.ghostx") { ghostDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.ghostDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(sad, "Woooo... woooo...")
        chatPlayer(neutral, "Hello? Are you okay?")
        chatNpc(
            sad,
            "I cannot rest... my bones lie in a dark place... " +
                "far from the light of the surface world...",
        )
        when (
            choice2(
                "How can I help you?",
                1,
                "That's terrifying. Goodbye!",
                2,
            )
        ) {
            1 -> helpDialogue()
            2 -> {
                chatPlayer(worried, "That's terrifying. Goodbye!")
                goodbyeDialogue()
            }
        }
    }

    private suspend fun Dialogue.helpDialogue() {
        chatPlayer(happy, "How can I help you?")
        chatNpc(
            sad,
            "I was a disciple of the great wizard Rhingold. " +
                "My task was to guard an important skull... " +
                "but I failed, and now I am cursed to wander " +
                "until my bones are laid to rest.",
        )
        chatNpc(
            shifty,
            "Speak with Father Urhney in the swamp. He may know " +
                "how you can help me...",
        )
        chatPlayer(happy, "I'll look into it, restless one.")
        goodbyeDialogue()
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatNpc(sad, "Woooo...")
    }
}
