package org.rsmod.content.quest.manager

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseModals
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import toRs

enum class JournalState {
    OVERVIEW,
    LOG,
}

enum class QuestProgressState(val varp: Int) {
    NOT_STARTED(0),
    IN_PROGRESS(1),
    FINISHED(2),
}

@DslMarker annotation class QuestJournalDsl

data class QuestReward(
    val xp: Map<String, Double> = emptyMap(),
    val items: List<Pair<String, Int>> = emptyList(),
    val extraText: String? = null,
)

@QuestJournalDsl
fun rewards(builder: QuestRewardBuilder.() -> Unit): QuestReward {
    return QuestRewardBuilder().apply(builder).build()
}

@QuestJournalDsl
class QuestRewardBuilder {
    private val _xp = mutableMapOf<String, Double>()
    private val _items = mutableListOf<Pair<String, Int>>()
    private var _extraText: String? = null

    fun xp(skill: String, amount: Double) {
        _xp[skill] = amount
    }

    fun item(id: String, amount: Int = 1) {
        _items.add(id to amount)
    }

    fun extra(text: String) {
        _extraText = text
    }

    fun build(): QuestReward = QuestReward(_xp, _items, _extraText)
}

abstract class QuestScript(
    val questKey: String,
    val questVarp: String,
    val rewards: QuestReward,
    val completedQuestItemDisplay: ItemRewardDisplay,
) : PluginScript() {

    companion object {
        private val instances = mutableMapOf<String, QuestScript>()
        private var sharedHandlersRegistered = false

        /** Tracks which quest journal the player currently has open. */
        private val ACTIVE_JOURNAL_QUEST = AttributeKey<String>("quest_active_journal")

        fun get(key: String): QuestScript? = instances[key.normalizedQuestKey()]

        fun all(): Collection<QuestScript> = instances.values
    }

    private var Player.questState by intVarp(questVarp)

    val quest = Quest.register(questKey, questVarp, completedQuestItemDisplay, rewards)

    init {
        instances[questKey.normalizedQuestKey()] = this
    }

    abstract fun subTitle(): String

    abstract fun questLog(player: ProtectedAccess): String

    abstract fun completedLog(player: ProtectedAccess): String

    abstract fun ScriptContext.init()

    override fun ScriptContext.startup() {
        RSCM.requireRSCM(RSCMType.DBROW, "dbrow.${questKey}")

        onPlayerLogin {
            val state = quest.getQuestStage(player)
            val prog =
                when (state) {
                    0 -> QuestProgressState.NOT_STARTED
                    quest.maxSteps -> QuestProgressState.FINISHED
                    else -> QuestProgressState.IN_PROGRESS
                }
            player.questState = prog.varp
        }

        // UI handlers (overlay + modal buttons) register exactly once
        // then dispatch to the correct QuestScript dynamically
        if (!sharedHandlersRegistered) {
            sharedHandlersRegistered = true
            registerSharedUIHandlers()
        }

        this.init()
    }

    @Suppress("UnusedReceiverParameter")
    private fun ScriptContext.registerSharedUIHandlers() {
        onIfOverlayButton("component.questlist:list") { evt ->
            val clickedQuest = Quest.all().find { it.id == evt.comsub } ?: return@onIfOverlayButton
            val script = get(clickedQuest.key) ?: return@onIfOverlayButton
            val state = clickedQuest.questState(player)
            val journalState =
                if (state == QuestProgressState.NOT_STARTED) {
                    JournalState.OVERVIEW
                } else {
                    JournalState.LOG
                }
            script.openJournal(this, journalState)
        }

        onIfModalButton("component.questjournal_overview:close") {
            player.ifCloseModals(eventBus)
        }

        onIfModalButton("component.questjournal:close") {
            player.ifCloseModals(eventBus)
        }

        onIfModalButton("component.questjournal_overview:content_inner") {
            player.ifOpenOverlay("interface.worldmap", eventBus)
            player.ifSetEvents(
                "component.worldmap:close",
                0..1,
                IfEvent.Op1,
                IfEvent.Op2,
                IfEvent.Op3,
                IfEvent.Op4,
            )
            val activeQuestKey = player.attr[ACTIVE_JOURNAL_QUEST]
            if (activeQuestKey != null) {
                val activeQuest = Quest.get(activeQuestKey)
                if (activeQuest != null) {
                    activeQuest.startCoord?.let { coord ->
                        activeQuest.mapElement?.let { element ->
                            player.runClientScript(
                                3331,
                                RSCM.getRSCM("component.worldmap:map_noclick"),
                                coord.packed,
                                element,
                            )
                        }
                    }
                }
            }
        }

        onIfModalButton("component.questjournal:switch") {
            val activeQuestKey = player.attr[ACTIVE_JOURNAL_QUEST]
            if (activeQuestKey != null) {
                val script = get(activeQuestKey)
                script?.openJournal(this, JournalState.OVERVIEW)
            }
        }

        onIfModalButton("component.questjournal_overview:switch") {
            val activeQuestKey = player.attr[ACTIVE_JOURNAL_QUEST]
            if (activeQuestKey != null) {
                val script = get(activeQuestKey)
                script?.openJournal(this, JournalState.LOG)
            }
        }
    }

    fun openJournal(access: ProtectedAccess, type: JournalState) {
        access.player.attr[ACTIVE_JOURNAL_QUEST] = quest.key
        when (type) {
            JournalState.OVERVIEW -> openJournalOverview(access)
            JournalState.LOG -> openQuestLog(access)
        }
    }

    private fun openJournalOverview(access: ProtectedAccess) {
        access.ifOpenMain("interface.questjournal_overview")
        access.ifSetText(
            "component.questjournal_overview:title",
            "<col=7f0000>${quest.displayName}</col>",
        )

        access.runClientScript(
            6821,
            quest.rowID,
            subTitle(),
            RSCM.getRSCM("component.questjournal_overview:universe"),
            RSCM.getRSCM("component.questjournal_overview:content_inner"),
            RSCM.getRSCM("component.questjournal_overview:content_outer"),
            RSCM.getRSCM("component.questjournal_overview:scrollbar"),
            RSCM.getRSCM("component.questjournal_overview:inner"),
            RSCM.getRSCM("component.questjournal_overview:container"),
            RSCM.getRSCM("component.questjournal_overview:scroll"),
            access.player.combatLevel,
        )
    }

    private fun openQuestLog(access: ProtectedAccess) {
        val lines =
            (if (quest.isQuestCompleted(access.player)) completedLog(access) else questLog(access))
                .lines()
                .flatMap { it.toRs(inheritPreviousTags = true, wrapAt = 64).split("<br>") }

        access.ifOpenMain("interface.questjournal")
        access.runClientScript(5240)
        access.ifSetText(
            "component.questjournal:title",
            "<col=7f0000>${quest.displayName}</col>",
        )

        lines.forEachIndexed { index, line ->
            access.ifSetText("component.questjournal:qj${index + 1}", line)
        }
    }

    protected fun questJournal(
        player: ProtectedAccess,
        builder: QuestJournalBuilder.() -> Unit,
    ): String = buildQuestJournal(player, quest, builder)

    protected fun completionJournal(
        player: ProtectedAccess,
        builder: QuestJournalBuilder.() -> Unit,
    ): String = buildCompletionJournal(player, quest, builder)
}
