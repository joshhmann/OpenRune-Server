package org.rsmod.content.other.agentbridge.testing

/**
 * Learning Documentation bootstrap.
 *
 * Captures knowledge gained from QA testing sessions — what works, what doesn't,
 * which NPC dialogs were verified, which locations were buggy.
 *
 * This object serves as the runtime bootstrap for a persistent learning doc
 * that can be queried by future test sessions to skip already-verified content
 * and focus on new or broken areas.
 */
object LearningDocs {
    // Known working interactions (verified by QA tests)
    private val verifiedInteractions = mutableMapOf<String, MutableSet<String>>()

    // Known failing interactions (bug reports)
    private val failingInteractions = mutableMapOf<String, MutableSet<String>>()

    // Known content gaps (not yet implemented)
    private val contentGaps = mutableSetOf<String>()

    /**
     * Initialize default known state from historical QA findings.
     * These are bootstrapped from the HYX-161 Lumbridge Layer 10 findings.
     */
    fun initializeDefaults() {
        // From HYX-161 findings:
        markContentGap("Windmill hopper — not implemented")
        markContentGap("Flour bin — not implemented")
        markContentGap("Fishing spots — not functional")
        markContentGap("Wizards' Tower door — unresponsive")
        markContentGap("Castle door (north) — unresponsive")
        markContentGap("Castle door (south) — unresponsive")
        markContentGap("Windmill door — unresponsive")

        // Previously fixed:
        markVerified("Cook's Assistant", "Quest journal no longer crashes client (fixed)")
    }

    /** Record that an interaction was verified working. */
    fun markVerified(category: String, detail: String) {
        verifiedInteractions
            .getOrPut(category.lowercase()) { mutableSetOf() }
            .add(detail)
    }

    /** Record that an interaction failed. */
    fun markFailing(category: String, detail: String) {
        failingInteractions
            .getOrPut(category.lowercase()) { mutableSetOf() }
            .add(detail)
    }

    /** Record a known content gap. */
    fun markContentGap(description: String) {
        contentGaps.add(description)
    }

    /** Check if an interaction has been verified. */
    fun isVerified(category: String, detail: String): Boolean {
        return verifiedInteractions[category.lowercase()]?.contains(detail) == true
    }

    /** Check if an interaction is known to fail. */
    fun isKnownFailing(category: String, detail: String): Boolean {
        return failingInteractions[category.lowercase()]?.contains(detail) == true
    }

    /** Check if something is a known content gap. */
    fun isContentGap(description: String): Boolean {
        return description.lowercase() in contentGaps.map { it.lowercase() }
    }

    /** Get all verified interactions. */
    fun getVerifiedInteractions(): Map<String, Set<String>> = verifiedInteractions.mapValues { it.value.toSet() }

    /** Get all failing interactions. */
    fun getFailingInteractions(): Map<String, Set<String>> = failingInteractions.mapValues { it.value.toSet() }

    /** Get all content gaps. */
    fun getContentGaps(): Set<String> = contentGaps.toSet()

    /** Get a summary string for display/reporting. */
    fun getSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Learning Docs Summary ===")
        sb.appendLine("Verified: ${verifiedInteractions.values.sumOf { it.size }} interactions")
        sb.appendLine("Known Failing: ${failingInteractions.values.sumOf { it.size }} interactions")
        sb.appendLine("Content Gaps: ${contentGaps.size}")
        sb.appendLine()
        if (contentGaps.isNotEmpty()) {
            sb.appendLine("--- Content Gaps ---")
            contentGaps.sorted().forEach { sb.appendLine("  • $it") }
        }
        return sb.toString()
    }
}
