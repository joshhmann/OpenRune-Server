package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CraftingTutor : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.aide_tutor_crafting") { tutorDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.tutorDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Looking to learn the art of crafting?")
        chatPlayer(neutral, "What can you tell me?")
        when (
            choice2(
                "Tell me about Crafting.",
                1,
                "Not right now, thanks.",
                2,
            )
        ) {
            1 -> {
                chatNpc(
                    neutral,
                    "Crafting lets you create all sorts of useful items. " +
                        "You can make leather armour from cowhides, cut " +
                        "gems, spin wool into string, and much more.",
                )
                chatNpc(
                    neutral,
                    "There's a spinning wheel upstairs in the castle, " +
                        "and you can find a furnace and anvils in " +
                        "the town's workshop.",
                )
                goodbyeDialogue()
            }
            2 -> goodbyeDialogue()
        }
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatPlayer(happy, "Thanks for the info!")
        chatNpc(happy, "Good luck with your crafting!")
    }
}
