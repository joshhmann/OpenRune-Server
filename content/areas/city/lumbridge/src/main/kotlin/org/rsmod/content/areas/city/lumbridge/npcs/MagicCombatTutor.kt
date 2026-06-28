package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MagicCombatTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_magic") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Welcome, aspiring mage!")
        chatPlayer(neutral, "Hello! Can you teach me about magic?")
        when (
            choice3(
                "Tell me about Magic.",
                1,
                "What runes do I need?",
                2,
                "Maybe later.",
                3,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Magic is a versatile skill. You can cast combat " +
                        "spells to attack from a distance, teleport to " +
                        "various locations, or enchant items.",
                )
                chatNpc(
                    neutral,
                    "To cast spells you'll need a staff, runes, and " +
                        "the appropriate Magic level. Different spells " +
                        "require different rune combinations.",
                )
                goodbyeDialogue()
            }
            2 -> {
                chatNpc(
                    neutral,
                    "There are four elemental runes: air, water, " +
                        "earth, and fire. You'll also need mind, chaos, " +
                        "death, or blood runes for stronger spells.",
                )
                chatNpc(
                    neutral,
                    "You can find rune shops in Varrock and other " +
                        "major cities. Some monsters also drop runes " +
                        "when defeated.",
                )
                goodbyeDialogue()
            }
            3 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the lesson!")
        chatNpc(happy, "May your magic be strong!")
    }
}
