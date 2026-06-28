package org.rsmod.content.areas.city.lumbridge.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Batch handler for Lumbridge minor NPCs that don't need their own file.
 * Keeps the NPC layer complete without a file-per-minor-NPC overhead.
 */
class LumbridgeMinorNpcs : PluginScript() {
    override fun ScriptContext.startup() {
        // Farmers (fields around Lumbridge)
        onOpNpc1("npc.farmer1") { farmerDialogue(it.npc) }
        onOpNpc1("npc.farmer1_f") { farmerDialogue(it.npc) }
        onOpNpc1("npc.farmer2") { farmerDialogue(it.npc) }
        onOpNpc1("npc.farmer2_f") { farmerDialogue(it.npc) }
        onOpNpc1("npc.farmer3") { farmerDialogue(it.npc) }
        onOpNpc1("npc.farmer3_f") { farmerDialogue(it.npc) }
        onOpNpc1("npc.farmer4") { farmerDialogue(it.npc) }

        // Fred the Farmer — cow field east of Lumbridge
        onOpNpc1("npc.fred_the_farmer") { fredDialogue(it.npc) }

        // Gillie Groats — milkmaid by cow field
        onOpNpc1("npc.gillie_the_milkmaid") { gillieDialogue(it.npc) }

        // Count Check — graveyard (event NPC)
        onOpNpc1("npc.count_check") { countCheckDialogue(it.npc) }

        // Border guards — Al Kharid toll gate
        onOpNpc1("npc.borderguard1") { borderGuardDialogue(it.npc) }

        // Seth Groats — cow field, east Lumbridge
        onOpNpc1("npc.favour_seth_groats") { sethDialogue(it.npc) }

        // Adventure Path guides — near spawn, help new players
        onOpNpc1("npc.ap_guide_active") { apGuideDialogue(it.npc) }
        onOpNpc1("npc.ap_guide_parent") { apGuideDialogue(it.npc) }

        // Imps — wandering around
        onOpNpc1("npc.imp") { impDialogue(it.npc) }
    }

    // ── Farmers ─────────────────────────────────────────

    private suspend fun ProtectedAccess.farmerDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "How're you doing, mate? Lovely day for it.")
            chatPlayer(happy, "Sure is.")
            chatNpc(happy, "Got plenty of work to do on the farm, always something needs fixing.")
        }

    // ── Fred the Farmer ──────────────────────────────────

    private suspend fun ProtectedAccess.fredDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Hello there!")
            chatPlayer(neutral, "Hello. Nice farm you've got.")
            chatNpc(
                neutral,
                "Aye, it's hard work but someone's got to do it. " +
                    "Got cows to milk, fields to tend, and fences to mend.",
            )
            when (
                choice2(
                    "Can I help with anything?",
                    1,
                    "Good luck with that.",
                    2,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Can I help with anything?")
                    chatNpc(
                        neutral,
                        "Not at the moment, but if you see any goblins " +
                            "causing trouble, give 'em a whack for me!",
                    )
                }
                2 -> chatPlayer(happy, "Good luck with that.")
            }
        }

    // ── Gillie Groats ────────────────────────────────────

    private suspend fun ProtectedAccess.gillieDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Hello, dearie. Can I get you some milk?")
            chatPlayer(neutral, "Not right now, thanks.")
            chatNpc(happy, "Well, you know where to find me if you need any!")
        }

    // ── Count Check ──────────────────────────────────────

    private suspend fun ProtectedAccess.countCheckDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Greetings, adventurer! Fine day for counting, isn't it?")
            chatPlayer(confused, "Counting?")
            chatNpc(
                neutral,
                "Indeed! Every day I check how many people are exploring " +
                    "the world of Gielinor. It's important to keep track!",
            )
        }

    // ── Border Guards ────────────────────────────────────

    private suspend fun ProtectedAccess.borderGuardDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(neutral, "You must pay a toll of 10 coins to pass through this gate.")
            chatPlayer(neutral, "What's on the other side?")
            chatNpc(
                neutral,
                "The great desert city of Al Kharid lies beyond this gate. " +
                    "A bustling place, but the heat takes some getting used to.",
            )
        }

    // ── Seth Groats ─────────────────────────────────────

    private suspend fun ProtectedAccess.sethDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Hello there! Keeping the cows in line?")
            chatPlayer(neutral, "I'll do my best.")
            chatNpc(happy, "That's the spirit! Farming's honest work.")
        }

    // ── Adventure Path Guides ───────────────────────────

    private suspend fun ProtectedAccess.apGuideDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Looking for adventure? You've come to the right place!")
            chatPlayer(happy, "What kind of adventure?")
            chatNpc(
                neutral,
                "There are many paths to choose from. You could become " +
                    "a mighty warrior, a master archer, or a powerful mage. " +
                    "Follow the path that calls to you!",
            )
        }

    // ── Imps ─────────────────────────────────────────────

    private suspend fun ProtectedAccess.impDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(angry, "Grr! Grrr grrrr!")
            chatPlayer(confused, "I'll take that as a no.")
        }
}
