package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MeleeCombatTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_melee") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello there! Ready to learn the art of melee combat?")
        chatPlayer(neutral, "What can you teach me?")
        when (
            choice3(
                "Tell me about melee combat.",
                1,
                "What weapons should I use?",
                2,
                "Not right now, thanks.",
                3,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Melee combat is fighting up close and personal. " +
                        "You'll need good armour and a strong weapon. " +
                        "Attack, Strength, and Defence are your key skills.",
                )
                chatNpc(
                    neutral,
                    "Attack determines what weapons you can wield " +
                        "and how accurately you hit. Strength increases " +
                        "your damage. Defence helps you avoid getting hit.",
                )
                goodbyeDialogue()
            }
            2 -> {
                chatNpc(
                    neutral,
                    "Start with a bronze sword or scimitar. As you " +
                        "progress, you can upgrade to iron, steel, " +
                        "mithril, adamant, and rune weapons.",
                )
                chatNpc(
                    neutral,
                    "A shield in your off-hand will help you block " +
                        "attacks. You can buy basic equipment from " +
                        "Bob's Brilliant Axes in Lumbridge.",
                )
                goodbyeDialogue()
            }
            3 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the advice!")
        chatNpc(happy, "Good luck out there!")
    }
}
