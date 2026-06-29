package org.rsmod.content.other.agentbridge.testing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Test result writer. Collects results from QA test execution and writes them to disk as JSON.
 *
 * Results go to `qa-test-results/{timestamp}-{test-name}.json` in the server root directory.
 * A `latest.json` symlink (or copy) is maintained for easy access.
 */
@Singleton
class TestResultReporter @Inject constructor() {

    private val resultsDir = File("qa-test-results")
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("UTC"))

    init {
        resultsDir.mkdirs()
    }

    /**
     * Write a test report to disk.
     * @return The report file path, or null on failure.
     */
    fun writeReport(report: TestReport): File? {
        return try {
            val timestamp = formatter.format(report.startedAt)
            val filename = "$timestamp-${report.testName.replace(" ", "_")}.json"
            val file = File(resultsDir, filename)
            file.writeText(report.toJson())
            // Update latest.json copy
            val latest = File(resultsDir, "latest.json")
            latest.writeText(report.toJson())
            logger.info { "[TestResultReporter] Written: ${file.absolutePath}" }
            file
        } catch (e: Exception) {
            logger.error(e) { "[TestResultReporter] Failed to write report" }
            null
        }
    }

    /** List all available test reports, newest first. */
    fun listReports(): List<File> =
        resultsDir
            .listFiles()
            .orEmpty()
            .filter { it.name.endsWith(".json") && it.name != "latest.json" }
            .sortedByDescending { it.lastModified() }

    /** Read the latest test report. */
    fun readLatest(): TestReport? {
        val latest = File(resultsDir, "latest.json")
        if (!latest.exists()) return null
        return try {
            TestReport.fromJson(latest.readText())
        } catch (e: Exception) {
            logger.error(e) { "[TestResultReporter] Failed to parse latest report" }
            null
        }
    }

    companion object {
        private val logger = com.github.michaelbull.logging.InlineLogger()
    }
}

/**
 * Data class representing a single test report.
 */
data class TestReport(
    val testName: String,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val status: TestStatus = TestStatus.RUNNING,
    val steps: List<TestStep> = emptyList(),
    val bugs: List<BugReport> = emptyList(),
    val errors: List<String> = emptyList(),
    val botName: String = "",
    val xpGained: Map<String, Int> = emptyMap(),
    val summary: String = "",
) {
    fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"test\": ${jsonStr(testName)},")
        sb.appendLine("  \"startedAt\": ${jsonStr(startedAt.toString())},")
        sb.appendLine("  \"completedAt\": ${jsonStr(completedAt?.toString() ?: "")},")
        sb.appendLine("  \"status\": ${jsonStr(status.name)},")
        sb.appendLine("  \"botName\": ${jsonStr(botName)},")
        sb.appendLine("  \"summary\": ${jsonStr(summary)},")
        sb.appendLine("  \"steps\": [")
        steps.forEachIndexed { i, step ->
            sb.append("    ${step.toJson()}")
            if (i < steps.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  ],")
        sb.appendLine("  \"bugs\": [")
        bugs.forEachIndexed { i, bug ->
            sb.append("    ${bug.toJson()}")
            if (i < bugs.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  ],")
        sb.appendLine("  \"errors\": [")
        errors.forEachIndexed { i, err ->
            sb.append("    ${jsonStr(err)}")
            if (i < errors.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  ],")
        sb.appendLine("  \"xpGained\": {")
        xpGained.entries.forEachIndexed { i, (skill, xp) ->
            sb.append("    ${jsonStr(skill)}: $xp")
            if (i < xpGained.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  }")
        sb.appendLine("}")
        return sb.toString()
    }

    companion object {
        fun fromJson(json: String): TestReport {
            // Simple JSON parser for reading back reports
            // For production, use Jackson; for now, basic regex extraction works
            val testName = extractJsonStr(json, "test")
            val startedAt = Instant.parse(extractJsonStr(json, "startedAt"))
            val completedAt =
                try {
                    val s = extractJsonStr(json, "completedAt")
                    if (s.isNotBlank()) Instant.parse(s) else null
                } catch (_: Exception) { null }
            val status =
                try {
                    TestStatus.valueOf(extractJsonStr(json, "status"))
                } catch (_: Exception) { TestStatus.RUNNING }
            val botName = extractJsonStr(json, "botName")
            val summary = extractJsonStr(json, "summary")
            return TestReport(
                testName = testName,
                startedAt = startedAt,
                completedAt = completedAt,
                status = status,
                botName = botName,
                summary = summary,
            )
        }

        private fun extractJsonStr(json: String, key: String): String {
            val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
        }
    }
}

enum class TestStatus {
    RUNNING,
    PASS,
    FAIL,
    ERROR,
    SKIPPED,
}

data class TestStep(
    val action: String,
    val result: String,
    val target: String = "",
    val ticks: Int = 0,
    val details: String = "",
    val xpGained: Map<String, Int> = emptyMap(),
) {
    fun toJson(): String {
        val parts =
            mutableListOf(
                "\"action\": ${jsonStr(action)}",
                "\"result\": ${jsonStr(result)}",
            )
        if (target.isNotBlank()) parts.add("\"target\": ${jsonStr(target)}")
        if (ticks > 0) parts.add("\"ticks\": $ticks")
        if (details.isNotBlank()) parts.add("\"details\": ${jsonStr(details)}")
        if (xpGained.isNotEmpty()) {
            val xpStr =
                xpGained.entries.joinToString(", ") { (k, v) -> "${jsonStr(k)}: $v" }
            parts.add("\"xpGained\": { $xpStr }")
        }
        return "{ ${parts.joinToString(", ")} }"
    }
}

data class BugReport(
    val severity: String,
    val description: String,
    val expected: String = "",
    val actual: String = "",
    val x: Int = 0,
    val z: Int = 0,
    val npc: String = "",
    val loc: String = "",
    val screenshot: String = "",
) {
    fun toJson(): String {
        val parts =
            mutableListOf(
                "\"severity\": ${jsonStr(severity)}",
                "\"description\": ${jsonStr(description)}",
            )
        if (expected.isNotBlank()) parts.add("\"expected\": ${jsonStr(expected)}")
        if (actual.isNotBlank()) parts.add("\"actual\": ${jsonStr(actual)}")
        if (x != 0 || z != 0) parts.add("\"loc\": [$x, $z]")
        if (npc.isNotBlank()) parts.add("\"npc\": ${jsonStr(npc)}")
        if (loc.isNotBlank()) parts.add("\"locName\": ${jsonStr(loc)}")
        return "{ ${parts.joinToString(", ")} }"
    }
}

private fun jsonStr(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
