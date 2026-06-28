package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RangedCombatTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_ranging") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hi there! Interested in ranged combat?")
        chatPlayer(neutral, "What can you tell me?")
        when (
            choice3(
                "Tell me about Ranged.",
                1,
                "What equipment do I need?",
                2,
                "Not now, thanks.",
                3,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Ranged combat lets you attack from a distance. " +
                        "You'll need a bow and arrows, or other ranged " +
                        "weapons like darts or knives.",
                )
                chatNpc(
                    neutral,
                    "Your Ranged level determines what weapons you " +
                        "can use. Higher levels let you use more " +
                        "powerful equipment.",
                )
                goodbyeDialogue()
            }
            2 -> {
                chatNpc(
                    neutral,
                    "A simple shortbow and some arrows will get you " +
                        "started. You can find bronze arrows in the " +
                        "general store, or you can fletch your own " +
                        "bows and arrows.",
                )
                chatNpc(
                    neutral,
                    "Don't forget to wear leather armour — it's " +
                        "lightweight and doesn't hinder your aim!",
                )
                goodbyeDialogue()
            }
            3 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the tips!")
        chatNpc(happy, "Happy shooting!")
    }
}
