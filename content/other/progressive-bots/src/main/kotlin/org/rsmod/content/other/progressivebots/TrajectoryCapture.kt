package org.rsmod.content.other.progressivebots

import org.rsmod.api.player.isInCombat
import org.rsmod.game.entity.Player
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Captures bot trajectory data (state + decision + outcome) as JSONL for LLM fine-tuning.
 *
 * Schema follows rs-sdk trajectory format conventions.
 * Uses manual JSON construction (no Gson/Jackson dependency needed).
 *
 * Output: trajectories/progressive/{YYYY-MM-DD}/trajectory.jsonl
 *
 * Each line is a valid JSON object with fields:
 *   tick, bot_name, personality, state (object), decision (object), outcome (object|null)
 */
class TrajectoryCapture {

    private var currentFile: File? = null
    private var currentDate: LocalDate = LocalDate.MIN

    fun capture(
        tick: Int,
        state: TrajectoryState,
        personality: BotPlanner,
        action: String,
    ) {
        val entry = buildDecisionLine(tick, state, personality, action)
        appendEntry(entry)
    }

    fun captureOutcome(
        tick: Int,
        botName: String,
        x: Int,
        z: Int,
        status: String = "moved",
    ) {
        val entry =
            """{"tick":$tick,"bot_name":"${escape(botName)}","outcome":{"status":"${escape(status)}","new_x":$x,"new_z":$z}}"""
        appendEntry(entry)
    }

    private fun buildDecisionLine(
        tick: Int,
        state: TrajectoryState,
        personality: BotPlanner,
        action: String,
    ): String {
        val sb = StringBuilder()
        sb.append("{\"tick\":$tick,")
        sb.append("\"bot_name\":\"${escape(state.botName)}\",")
        sb.append("\"personality\":\"${escape(personality.name)}\",")

        // State object
        sb.append("\"state\":{")
        sb.append("\"bot_name\":\"${escape(state.botName)}\",")
        sb.append("\"position\":{\"x\":${state.x},\"z\":${state.z},\"plane\":${state.plane}},")
        sb.append("\"in_combat\":${state.inCombat},")
        sb.append("\"animating\":${state.animating},")
        sb.append("\"personality_class\":\"${escape(state.personalityClass)}\",")
        sb.append("\"inventory\":[")
        var firstInv = true
        for (item in state.inventory) {
            if (!firstInv) sb.append(",")
            firstInv = false
            sb.append("{\"id\":${item.id},\"amount\":${item.amount}}")
        }
        sb.append("],")
        sb.append("\"skills\":{")
        var first = true
        for ((name, skill) in state.skills) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"${escape(name)}\":{\"level\":${skill.level},\"base_level\":${skill.baseLevel},\"xp\":${skill.xp}}")
        }
        sb.append("}},")

        // Decision object
        sb.append("\"decision\":{\"action\":\"${escape(action)}\"},")

        // No outcome yet at decision time
        sb.append("\"outcome\":null")
        sb.append("}")
        return sb.toString()
    }

    private fun appendEntry(line: String) {
        if (paused) return
        val file = getOrCreateFile()
        file.appendText(line + "\n")
        if (targetCount > 0 && file.readLines().size >= targetCount) {
            paused = true
            println("[TrajectoryCapture] Auto-paused: reached target of $targetCount entries.")
        }
    }

    private fun getOrCreateFile(): File {
        val today = LocalDate.now()
        if (today != currentDate) {
            val dir = File(TRAJECTORY_DIR, today.format(DateTimeFormatter.ISO_LOCAL_DATE))
            dir.mkdirs()
            currentFile = File(dir, "trajectory.jsonl")
            currentDate = today
        }
        return currentFile!!
    }

    companion object {
        private const val TRAJECTORY_DIR = "trajectories/progressive"

        /**
         * Runtime toggle: when paused, no new entries are written.
         * Set via ::trajectory pause/resume in-game.
         */
        @JvmStatic
        var paused: Boolean = false

        /**
         * Target episode count. 0 = unlimited.
         * When the file reaches this many lines, auto-pauses.
         */
        @JvmStatic
        var targetCount: Int = 0

        private fun escape(s: String): String =
            s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

        @OptIn(org.rsmod.annotations.InternalApi::class)
        fun snapshot(player: Player, personality: BotPersonality): TrajectoryState {
            val coords = player.coords
            val stats =
                STAT_NAMES.associate { name ->
                    name.removePrefix("stat.") to
                        SkillLevel(
                            level = player.statMap.getCurrentLevel(name).toInt(),
                            baseLevel = player.statMap.getBaseLevel(name).toInt(),
                            xp = player.statMap.getXP(name),
                        )
                }
            return TrajectoryState(
                botName = player.avatar.name.lowercase(),
                x = coords.x,
                z = coords.z,
                plane = coords.level,
                skills = stats,
                inCombat = player.isInCombat(),
                animating = player.pendingSequence.id != -1,
                personalityClass = personality::class.simpleName ?: "Unknown",
                inventory = player.inv.mapNotNull { if (it != null) TrajectoryItem(it.id, it.count) else null }
            )
        }

        val STAT_NAMES: List<String> =
            listOf(
                "stat.attack", "stat.defence", "stat.strength", "stat.hitpoints",
                "stat.ranged", "stat.prayer", "stat.magic", "stat.cooking",
                "stat.woodcutting", "stat.fletching", "stat.fishing", "stat.firemaking",
                "stat.crafting", "stat.smithing", "stat.mining", "stat.herblore",
                "stat.agility", "stat.thieving", "stat.slayer", "stat.farming",
                "stat.runecrafting", "stat.hunter", "stat.construction",
            )
    }
}

data class TrajectoryState(
    val botName: String,
    val x: Int,
    val z: Int,
    val plane: Int,
    val skills: Map<String, SkillLevel>,
    val inCombat: Boolean,
    val animating: Boolean,
    val personalityClass: String,
    val inventory: List<TrajectoryItem>,
)

data class TrajectoryItem(
    val id: Int,
    val amount: Int
)

data class SkillLevel(
    val level: Int,
    val baseLevel: Int,
    val xp: Int,
)
