package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LumbridgeGuide : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.lumbridge_guide") { guideDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.guideDialogue(npc: Npc) =
        startDialogue(npc) { mainDialogue(npc) }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Greetings! I'm the Lumbridge Guide.")
        chatPlayer(neutral, "Hello. What can you tell me?")
        when (
            choice4(
                "Where should I go?",
                1,
                "Tell me about this area.",
                2,
                "What is there to do around here?",
                3,
                "I'm fine, thanks.",
                4,
            )
        ) {
            1 -> directionsDialogue()
            2 -> areaDialogue()
            3 -> activitiesDialogue()
            4 -> {
                chatPlayer(happy, "I'm fine, thanks!")
                chatNpc(happy, "Good luck on your adventures!")
            }
        }
    }

    private suspend fun Dialogue.directionsDialogue() {
        chatPlayer(quiz, "Where should I go?")
        chatNpc(
            neutral,
            "Well, you could head north to the famous city of " +
                "Varrock, or west to Draynor Village and beyond " +
                "to Falador.",
        )
        chatNpc(
            neutral,
            "If you cross the bridge to the east, you'll reach " +
                "Al Kharid, a desert town. There's a toll gate, " +
                "so bring 10 coins!",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.areaDialogue() {
        chatPlayer(quiz, "Tell me about this area.")
        chatNpc(
            neutral,
            "Lumbridge is a peaceful farming community. We have " +
                "a castle, a church, a general store, and Bob's " +
                "Brilliant Axes if you need tools.",
        )
        chatNpc(
            neutral,
            "There are also tutors around town who can teach you " +
                "about various skills. Look for them south of the " +
                "castle.",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.activitiesDialogue() {
        chatPlayer(quiz, "What is there to do around here?")
        chatNpc(
            neutral,
            "You can train your skills by cutting trees, fishing " +
                "in the river, or cooking the food you catch. " +
                "There's also a mine in the swamp to the south-west.",
        )
        chatNpc(
            neutral,
            "If you're feeling brave, there are goblins to fight " +
                "north of town, and the Lumbridge Catacombs beneath " +
                "the castle are full of dangerous creatures.",
        )
        goodbyeDialogue()
    }

    private suspend fun Dialogue.goodbyeDialogue() {
        chatNpc(happy, "Feel free to come back if you need any more help!")
        chatPlayer(happy, "Thanks, I will!")
    }
}
