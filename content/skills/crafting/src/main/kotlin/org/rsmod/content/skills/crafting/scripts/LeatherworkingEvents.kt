package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Leatherworking implementation for Crafting skill.
 *
 * Mechanics:
 * 1. Tan cowhide to leather (normal or hard) — via NPC (Ellis in Al Kharid)
 * 2. Use needle + thread + leather to craft leather items
 * 3. Hard leather body requires hard leather specifically
 * 4. Needle + leather body/chaps + studs → studded items
 */
class LeatherworkingEvents
@Inject
constructor(private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        // Ellis the Tanner (Al Kharid)
        runCatching { onOpNpc1("npc.ellis") { openTanningInterface() } }

        // Needle on leather → leather item menu
        onOpHeldU("obj.needle", "obj.leather") { openLeatherCrafting() }

        // Needle on hard leather → hard leather body only
        onOpHeldU("obj.needle", "obj.hard_leather") { craftHardLeatherBody() }

        // Needle on leather armour body/chaps + studs → studded items
        onOpHeldU("obj.needle", "obj.leather_armour") { craftStuddedItem(StuddedItem.BODY) }
        onOpHeldU("obj.needle", "obj.leather_chaps") { craftStuddedItem(StuddedItem.CHAPS) }
    }

    // ── Tanning ──

    private suspend fun ProtectedAccess.openTanningInterface() {
        val choice =
            choice3(
                "Leather (1gp per hide)",
                1,
                "Hard leather (3gp per hide)",
                2,
                "Never mind",
                3,
                title = "What would you like to tan?",
            )

        when (choice) {
            1 -> tanHides(tanToHard = false)
            2 -> tanHides(tanToHard = true)
        }
    }

    private suspend fun ProtectedAccess.tanHides(tanToHard: Boolean) {
        if (!inv.contains("obj.cow_hide")) {
            mes("You don't have any cow hides to tan.")
            return
        }

        val costPerHide = if (tanToHard) 3 else 1
        val product = if (tanToHard) "obj.hard_leather" else "obj.leather"
        val productName = if (tanToHard) "hard leather" else "leather"

        val hideCount = inv.count("obj.cow_hide")
        val totalCost = hideCount * costPerHide
        val playerCoins = inv.count("obj.coins")

        if (playerCoins < totalCost) {
            mes("You need $totalCost coins to tan $hideCount hides.")
            return
        }

        invDel(inv, "obj.cow_hide", hideCount)
        invDel(inv, "obj.coins", totalCost)
        invAdd(inv, product, hideCount)

        mes("Ellis tans your cow hides into $productName.")
    }

    // ── Leather Crafting ──

    private suspend fun ProtectedAccess.openLeatherCrafting() {
        if (!inv.contains("obj.needle")) {
            mes("You need a needle to craft leather.")
            return
        }
        if (!inv.contains("obj.thread")) {
            mes("You need thread to craft leather.")
            return
        }
        if (!inv.contains("obj.leather")) {
            mes("You need leather to craft this item.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = LeatherItem.entries.map { item ->
                    SkillMultiEntry(
                        item.productInternal,
                        listOf(Material("obj.leather"), Material("obj.thread")),
                    )
                }
            )
        ) { selection ->
            val item =
                LeatherItem.entries.firstOrNull { it.productInternal == selection.entry.internal }
                    ?: return@openSkillMulti

            craftLeatherItem(item)
        }
    }

    private suspend fun ProtectedAccess.craftHardLeatherBody() {
        if (!inv.contains("obj.needle")) {
            mes("You need a needle to craft leather.")
            return
        }
        if (!inv.contains("obj.thread")) {
            mes("You need thread to craft leather.")
            return
        }
        if (!inv.contains("obj.hard_leather")) {
            mes("You need hard leather to craft this item.")
            return
        }

        val item = LeatherItem.HARD_BODY
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = listOf(
                    SkillMultiEntry(
                        item.productInternal,
                        listOf(Material("obj.hard_leather"), Material("obj.thread")),
                    )
                )
            )
        ) { selection ->
            craftLeatherItem(item)
        }
    }

    private suspend fun ProtectedAccess.craftLeatherItem(item: LeatherItem) {
        if (player.craftingLvl < item.levelReq) {
            mes("You need a Crafting level of ${item.levelReq} to craft ${item.displayName}.")
            return
        }

        val startCoords = coords
        repeat(CRAFT_COUNT) {
            if (coords != startCoords) return@repeat

            if (!inv.contains(item.leatherInternal) || !inv.contains("obj.thread")) {
                mes("You don't have enough materials.")
                return@repeat
            }

            val removedLeather = invDel(inv, item.leatherInternal, 1)
            val removedThread = invDel(inv, "obj.thread", 1)
            if (!removedLeather.success || !removedThread.success) {
                return@repeat
            }

            anim("seq.human_leather_crafting")
            delay(3)

            val xp = item.xp * xpMods.get(player, "stat.crafting")
            statAdvance("stat.crafting", xp)
            invAdd(inv, item.productInternal, 1)
            mes("You craft a ${item.displayName.lowercase()}.")
        }
    }

    // ── Studded Items ──

    private suspend fun ProtectedAccess.craftStuddedItem(item: StuddedItem) {
        if (!inv.contains("obj.needle")) {
            mes("You need a needle to craft studded leather.")
            return
        }
        if (!inv.contains("obj.thread")) {
            mes("You need thread to craft studded leather.")
            return
        }
        if (!inv.contains("obj.studs")) {
            mes("You need steel studs to make ${item.displayName.lowercase()}.")
            return
        }
        if (!inv.contains(item.baseInternal)) {
            mes("You need ${item.baseName} to make ${item.displayName.lowercase()}.")
            return
        }
        if (player.craftingLvl < item.levelReq) {
            mes("You need a Crafting level of ${item.levelReq} to make ${item.displayName}.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = listOf(
                    SkillMultiEntry(
                        item.productInternal,
                        listOf(
                            Material(item.baseInternal),
                            Material("obj.studs"),
                            Material("obj.thread"),
                        ),
                    )
                )
            )
        ) { selection ->
            val startCoords = coords
            repeat(STUDDED_CRAFT_COUNT) {
                if (coords != startCoords) return@repeat

                if (
                    !inv.contains(item.baseInternal) ||
                        !inv.contains("obj.studs") ||
                        !inv.contains("obj.thread")
                ) {
                    mes("You don't have enough materials.")
                    return@repeat
                }

                val removedBase = invDel(inv, item.baseInternal, 1)
                val removedStuds = invDel(inv, "obj.studs", 1)
                val removedThread = invDel(inv, "obj.thread", 1)
                if (!removedBase.success || !removedStuds.success || !removedThread.success) {
                    return@repeat
                }

                anim("seq.human_leather_crafting")
                delay(3)

                val xp = item.xp * xpMods.get(player, "stat.crafting")
                statAdvance("stat.crafting", xp)
                invAdd(inv, item.productInternal, 1)
                mes("You craft ${item.displayName.lowercase()}.")
            }
        }
    }

    // ── Data ──

    private enum class LeatherItem(
        val productInternal: String,
        val leatherInternal: String,
        val displayName: String,
        val levelReq: Int,
        val xp: Double,
    ) {
        GLOVES("obj.leather_gloves", "obj.leather", "Leather gloves", 1, 13.8),
        BOOTS("obj.leather_boots", "obj.leather", "Leather boots", 7, 16.25),
        COWL("obj.leather_cowl", "obj.leather", "Leather cowl", 9, 18.5),
        VAMBRACES("obj.leather_vambraces", "obj.leather", "Leather vambraces", 11, 22.0),
        BODY("obj.leather_armour", "obj.leather", "Leather body", 14, 25.0),
        CHAPS("obj.leather_chaps", "obj.leather", "Leather chaps", 18, 27.0),
        HARD_BODY("obj.hardleather_body", "obj.hard_leather", "Hard leather body", 28, 35.0),
    }

    private enum class StuddedItem(
        val baseInternal: String,
        val baseName: String,
        val productInternal: String,
        val displayName: String,
        val levelReq: Int,
        val xp: Double,
    ) {
        BODY(
            baseInternal = "obj.leather_armour",
            baseName = "a leather body",
            productInternal = "obj.studded_body",
            displayName = "Studded body",
            levelReq = 41,
            xp = 86.0,
        ),
        CHAPS(
            baseInternal = "obj.leather_chaps",
            baseName = "leather chaps",
            productInternal = "obj.studded_chaps",
            displayName = "Studded chaps",
            levelReq = 44,
            xp = 87.0,
        ),
    }

    companion object {
        private const val CRAFT_COUNT = 28
        private const val STUDDED_CRAFT_COUNT = 28
    }
}
