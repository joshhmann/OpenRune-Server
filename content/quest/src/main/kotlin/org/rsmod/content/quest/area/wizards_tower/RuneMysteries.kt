package org.rsmod.content.quest.area.wizards_tower

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

class RuneMysteries :
    QuestScript(
        "quest_runemysteries",
        "varp.runemysteries",
        rewards {
            xp("stat.runecraft", 0.0) // No XP reward — just mine access
        },
        ItemRewardDisplay("obj.air_talisman"),
    ) {

    private val GOT_TALISMAN = quest.attribute(name = "GOT_TALISMAN", default = false)
    private val GOT_PACKAGE = quest.attribute(name = "GOT_PACKAGE", default = false)
    private val GOT_NOTES = quest.attribute(name = "GOT_NOTES", default = false)

    override fun ScriptContext.init() {
        // Archmage Sedridor — Wizards' Tower basement
        onOpNpc1("npc.head_wizard") { startSedridorDialogue(it.npc) }
    }

    override fun ScriptContext.startup() {
        // Login handler for quest state sync (skip overlay/modal buttons to avoid
        // duplicate registration with other quest scripts)
        onPlayerLogin {
            val state = quest.getQuestStage(player)
            val prog = when (state) {
                0 -> QuestProgressState.NOT_STARTED
                quest.maxSteps -> QuestProgressState.FINISHED
                else -> QuestProgressState.IN_PROGRESS
            }
            player.questState = prog.varp
        }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Duke</col> in <col=800000>Lumbridge Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            description(
                "The incantation to the <red>rune essence mine</red> has been lost " +
                    "since the first <red>Wizards' Tower</red> burned down. However, after " +
                    "much research by the <red>Order of Wizards</red>, it is on the verge " +
                    "of rediscovery..."
            )

            objective("Talk to the <red>Duke of Lumbridge</red> about a quest.") {
                attribute(GOT_TALISMAN, "I spoke to the Duke and he gave me an <red>air talisman</red>.").strike()
                hasItem("obj.air_talisman", "")
            }

            objective(
                "Take the <red>air talisman</red> to <red>Archmage Sedridor</red> at the " +
                    "<red>Wizards' Tower</red> (south of Draynor Village, in the basement)."
            ) {
                attribute(GOT_PACKAGE, "Archmage Sedridor gave me a <red>research package</red> to deliver.").strike()
                hasItem("obj.research_package", "")
            }

            objective(
                "Take the <red>research package</red> to <red>Aubury</red> at his rune shop in <red>Varrock</red> " +
                    "(south of the east bank)."
            ) {
                attribute(GOT_NOTES, "Aubury gave me the <red>research notes</red> to return to Sedridor.").strike()
                hasItem("obj.research_notes", "")
            }

            objective(
                "Return the <red>research notes</red> to <red>Archmage Sedridor</red> " +
                    "at the <red>Wizards' Tower</red>."
            ) { }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I helped the Order of Wizards rediscover the incantation to the " +
                    "rune essence mine, allowing runecrafting to be performed."
            )
            line(
                "I can now access the rune essence mine via the Wizards' Tower " +
                    "or Aubury's rune shop in Varrock."
            )
        }

    // ── Sedridor Dialogue ─────────────────────────────────────

    private suspend fun ProtectedAccess.startSedridorDialogue(npc: Npc) =
        startDialogue(npc) { sedridorMainDialogue(npc) }

    private suspend fun Dialogue.sedridorMainDialogue(npc: Npc) {
        when {
            quest.isQuestCompleted(player) -> sedridorPostQuest()
            quest.questState(player) == QuestProgressState.NOT_STARTED -> sedridorNoQuest()
            quest.questState(player) == QuestProgressState.IN_PROGRESS -> sedridorDuringQuest(npc)
            else -> sedridorNoQuest()
        }
    }

    private suspend fun Dialogue.sedridorNoQuest() {
        chatNpc(
            neutral,
            "I am Archmage Sedridor, head of the Order of Wizards. " +
                "Is there something I can help you with?",
        )
        chatPlayer(neutral, "Not right now, thank you.")
        chatNpc(neutral, "Very well. Feel free to browse the tower's library.")
    }

    private suspend fun Dialogue.sedridorDuringQuest(npc: Npc) {
        if (GOT_NOTES.get(player)) {
            // Has both package AND notes? Deliver notes
            if (access.inv.count("obj.research_notes") > 0) {
                deliverSedridorNotes()
            } else {
                sedridorLostNotes()
            }
        } else if (GOT_PACKAGE.get(player)) {
            // Has package already, go to Aubury
            sedridorRemindAubury()
        } else if (GOT_TALISMAN.get(player)) {
            // Has talisman — give it to Sedridor
            if (access.inv.count("obj.air_talisman") > 0) {
                giveSedridorTalisman()
            } else {
                sedridorLostTalisman()
            }
        } else {
            // Shouldn't reach here — Duke gives talisman first
            sedridorNoQuest()
        }
    }

    private suspend fun Dialogue.giveSedridorTalisman() {
        chatNpc(happy, "Ah, I see you have an air talisman! May I see it?")
        chatPlayer(happy, "Of course, take a look.")
        access.invDel(access.inv, "obj.air_talisman")
        GOT_PACKAGE.set(player, true)
        access.invAdd(access.inv, "obj.research_package")
        quest.advanceQuestStage(access)
        chatNpc(
            neutral,
            "Fascinating... this talisman holds more power than I expected. " +
                "Please, take this research package to Aubury in Varrock. " +
                "His expertise in rune-related matters will be invaluable.",
        )
        chatPlayer(neutral, "I'll take it to him right away.")
    }

    private suspend fun Dialogue.sedridorLostTalisman() {
        chatNpc(
            neutral,
            "You seem to have lost the talisman the Duke gave you. " +
                "I'm afraid I can't help without it.",
        )
        chatPlayer(sad, "I'll try to find another one.")
        chatNpc(neutral, "Speak with the Duke about getting another.")
    }

    private suspend fun Dialogue.sedridorRemindAubury() {
        chatNpc(
            neutral,
            "You still have the research package? Take it to Aubury " +
                "in Varrock, his shop is south of the east bank.",
        )
        chatPlayer(happy, "Right, I'll head there now.")
    }

    private suspend fun Dialogue.deliverSedridorNotes() {
        access.invDel(access.inv, "obj.research_notes")
        quest.advanceQuestStage(access)
        chatNpc(
            happy,
            "Excellent! This is exactly what we needed. With Aubury's notes, " +
                "we have finally reconstructed the incantation!",
        )
        chatNpc(
            happy,
            "The rune essence mine is accessible once more. Please, take " +
                "this talisman back — it belongs with someone who will put it to use.",
        )
        access.invAdd(access.inv, "obj.air_talisman")
        chatPlayer(happy, "Thank you, Archmage!")
    }

    private suspend fun Dialogue.sedridorLostNotes() {
        chatNpc(
            worried,
            "You don't seem to have the notes anymore. They're vital to " +
                "our research! Speak with Aubury again.",
        )
    }

    private suspend fun Dialogue.sedridorPostQuest() {
        chatNpc(happy, "Welcome back! The rune essence mine has been a great boon.")
        chatPlayer(happy, "I'm glad I could help.")
    }

    // ── Duke Horacio Dialogue (quest start) ──────────────────
    // Called from DukeHoracio.kt when quest not started

    suspend fun Dialogue.dukeQuestStart() {
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
        GOT_TALISMAN.set(player, true)
        access.invAdd(access.inv, "obj.air_talisman")
        quest.advanceQuestStage(access)
        chatPlayer(happy, "I'll take it to him right away!")
        chatNpc(happy, "Thank you, adventurer. I'm sure he'll be most grateful.")
    }

    suspend fun Dialogue.dukeQuestInProgress() {
        chatPlayer(quiz, "Do you have any quests for me?")
        chatNpc(
            neutral,
            "The only job I had was the delivery of that talisman, " +
                "so I'm afraid not.",
        )
    }
}
