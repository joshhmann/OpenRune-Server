package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Jewelry making implementation for Crafting skill.
 *
 * Mechanics:
 * 1. Use gold bar on furnace with appropriate mould → jewelry
 * 2. Gems can be added for gemmed variants
 * 3. Unstrung amulets can be strung with ball of wool
 *
 * XP rates:
 *   Gold ring:       5 Crafting, 15 XP
 *   Gold necklace:   6 Crafting, 20 XP
 *   Gold bracelet:   7 Crafting, 25 XP
 *   Gold amulet (u): 8 Crafting, 30 XP
 *   + gem variants at higher levels
 */
class JewelryEvents
@Inject
constructor(private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        // Gold bar on furnace with mould
        onOpLocU("loc.furnace", "obj.gold_bar") { smeltJewelry() }

        // Stringing unstrung amulets with ball of wool
        onOpHeldU("obj.ball_of_wool", "obj.unstrung_gold_amulet") {
            stringAmulet(AmuletStringRecipe.GOLD)
        }
        onOpHeldU("obj.ball_of_wool", "obj.unstrung_sapphire_amulet") {
            stringAmulet(AmuletStringRecipe.SAPPHIRE)
        }
        onOpHeldU("obj.ball_of_wool", "obj.unstrung_emerald_amulet") {
            stringAmulet(AmuletStringRecipe.EMERALD)
        }
        onOpHeldU("obj.ball_of_wool", "obj.unstrung_ruby_amulet") {
            stringAmulet(AmuletStringRecipe.RUBY)
        }
        onOpHeldU("obj.ball_of_wool", "obj.unstrung_diamond_amulet") {
            stringAmulet(AmuletStringRecipe.DIAMOND)
        }
        onOpHeldU("obj.ball_of_wool", "obj.unstrung_dragonstone_amulet") {
            stringAmulet(AmuletStringRecipe.DRAGONSTONE)
        }
    }

    // ── Smelting Jewelry ──

    private suspend fun ProtectedAccess.smeltJewelry() {
        if (!inv.contains("obj.gold_bar")) {
            mes("You need a gold bar to make jewellery.")
            return
        }

        val availableRecipes =
            JewelryRecipe.entries.filter { recipe ->
                inv.contains(recipe.mouldInternal) &&
                    (recipe.gemInternal == null || inv.contains(recipe.gemInternal)) &&
                    player.craftingLvl >= recipe.levelReq
            }

        if (availableRecipes.isEmpty()) {
            mes("You need a ring, necklace, amulet, or bracelet mould to make jewellery.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = availableRecipes.map { recipe ->
                    val materials =
                        buildList {
                            add(Material("obj.gold_bar"))
                            if (recipe.gemInternal != null) {
                                add(Material(recipe.gemInternal))
                            }
                            add(Material(recipe.mouldInternal))
                        }
                    SkillMultiEntry(recipe.productInternal, materials)
                },
                maxCountProvider = { inv, _ -> inv.count("obj.gold_bar") },
            )
        ) { selection ->
            val recipe =
                availableRecipes.firstOrNull { it.productInternal == selection.entry.internal }
                    ?: return@openSkillMulti

            val startCoords = coords
            repeat(selection.amount) {
                if (coords != startCoords) return@repeat

                if (!inv.contains("obj.gold_bar")) {
                    mes("You don't have any gold bars left.")
                    return@repeat
                }

                if (recipe.gemInternal != null && !inv.contains(recipe.gemInternal)) {
                    mes("You don't have any ${recipe.gemName} left.")
                    return@repeat
                }

                if (!inv.contains(recipe.mouldInternal)) {
                    mes("You need the mould to continue.")
                    return@repeat
                }

                val removedBar = invDel(inv, "obj.gold_bar", 1)
                if (!removedBar.success) return@repeat

                if (recipe.gemInternal != null) {
                    val removedGem = invDel(inv, recipe.gemInternal, 1)
                    if (!removedGem.success) {
                        invAdd(inv, "obj.gold_bar", 1)
                        mes("You don't have the required gem.")
                        return@repeat
                    }
                }

                anim("seq.human_smithing")
                delay(4)

                val xp = recipe.xp * xpMods.get(player, "stat.crafting")
                statAdvance("stat.crafting", xp)
                invAdd(inv, recipe.productInternal, 1)
                mes(recipe.makeMessage)
            }
        }
    }

    // ── Amulet Stringing ──

    private suspend fun ProtectedAccess.stringAmulet(recipe: AmuletStringRecipe) {
        if (player.craftingLvl < recipe.levelReq) {
            mes("You need a Crafting level of ${recipe.levelReq} to string this amulet.")
            return
        }

        if (!inv.contains(recipe.unstrungInternal)) {
            mes("You don't have any unstrung amulets to string.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "string",
                entries = listOf(
                    SkillMultiEntry(
                        recipe.strungInternal,
                        listOf(Material(recipe.unstrungInternal), Material("obj.ball_of_wool")),
                    )
                )
            )
        ) { selection ->
            val startCoords = coords
            repeat(selection.amount) {
                if (coords != startCoords) return@repeat

                val removedAmulet = invDel(inv, recipe.unstrungInternal, 1)
                val removedWool = invDel(inv, "obj.ball_of_wool", 1)
                if (!removedAmulet.success || !removedWool.success) {
                    mes("You don't have the required materials.")
                    return@repeat
                }

                anim("seq.human_smithing")
                delay(2)

                val xp = STRINGING_XP * xpMods.get(player, "stat.crafting")
                statAdvance("stat.crafting", xp)
                invAdd(inv, recipe.strungInternal, 1)
                mes("You string the amulet.")
            }
        }
    }

    // ── Data ──

    private enum class JewelryRecipe(
        val category: JewelryCategory,
        val mouldInternal: String,
        val gemInternal: String?,
        val gemName: String,
        val productInternal: String,
        val displayName: String,
        val levelReq: Int,
        val xp: Double,
        val makeMessage: String,
    ) {
        GOLD_RING(
            JewelryCategory.RING, "obj.ring_mould", null, "",
            "obj.gold_ring", "Gold ring", 5, 15.0,
            "You make a gold ring.",
        ),
        SAPPHIRE_RING(
            JewelryCategory.RING, "obj.ring_mould", "obj.sapphire", "sapphires",
            "obj.sapphire_ring", "Sapphire ring", 20, 40.0,
            "You set the sapphire into a ring.",
        ),
        EMERALD_RING(
            JewelryCategory.RING, "obj.ring_mould", "obj.emerald", "emeralds",
            "obj.emerald_ring", "Emerald ring", 27, 55.0,
            "You set the emerald into a ring.",
        ),
        RUBY_RING(
            JewelryCategory.RING, "obj.ring_mould", "obj.ruby", "rubies",
            "obj.ruby_ring", "Ruby ring", 34, 70.0,
            "You set the ruby into a ring.",
        ),
        DIAMOND_RING(
            JewelryCategory.RING, "obj.ring_mould", "obj.diamond", "diamonds",
            "obj.diamond_ring", "Diamond ring", 43, 85.0,
            "You set the diamond into a ring.",
        ),
        DRAGONSTONE_RING(
            JewelryCategory.RING, "obj.ring_mould", "obj.dragonstone", "dragonstones",
            "obj.dragonstone_ring", "Dragonstone ring", 55, 100.0,
            "You set the dragonstone into a ring.",
        ),
        GOLD_NECKLACE(
            JewelryCategory.NECKLACE, "obj.necklace_mould", null, "",
            "obj.gold_necklace", "Gold necklace", 6, 20.0,
            "You make a gold necklace.",
        ),
        SAPPHIRE_NECKLACE(
            JewelryCategory.NECKLACE, "obj.necklace_mould", "obj.sapphire", "sapphires",
            "obj.sapphire_necklace", "Sapphire necklace", 22, 55.0,
            "You set the sapphire into a necklace.",
        ),
        EMERALD_NECKLACE(
            JewelryCategory.NECKLACE, "obj.necklace_mould", "obj.emerald", "emeralds",
            "obj.emerald_necklace", "Emerald necklace", 29, 60.0,
            "You set the emerald into a necklace.",
        ),
        RUBY_NECKLACE(
            JewelryCategory.NECKLACE, "obj.necklace_mould", "obj.ruby", "rubies",
            "obj.ruby_necklace", "Ruby necklace", 40, 75.0,
            "You set the ruby into a necklace.",
        ),
        DIAMOND_NECKLACE(
            JewelryCategory.NECKLACE, "obj.necklace_mould", "obj.diamond", "diamonds",
            "obj.diamond_necklace", "Diamond necklace", 56, 90.0,
            "You set the diamond into a necklace.",
        ),
        DRAGONSTONE_NECKLACE(
            JewelryCategory.NECKLACE, "obj.necklace_mould", "obj.dragonstone", "dragonstones",
            "obj.dragonstone_necklace", "Dragonstone necklace", 72, 105.0,
            "You set the dragonstone into a necklace.",
        ),
        GOLD_AMULET(
            JewelryCategory.AMULET, "obj.amulet_mould", null, "",
            "obj.unstrung_gold_amulet", "Gold amulet (u)", 8, 30.0,
            "You make an unstrung gold amulet.",
        ),
        SAPPHIRE_AMULET(
            JewelryCategory.AMULET, "obj.amulet_mould", "obj.sapphire", "sapphires",
            "obj.unstrung_sapphire_amulet", "Sapphire amulet (u)", 24, 65.0,
            "You make an unstrung sapphire amulet.",
        ),
        EMERALD_AMULET(
            JewelryCategory.AMULET, "obj.amulet_mould", "obj.emerald", "emeralds",
            "obj.unstrung_emerald_amulet", "Emerald amulet (u)", 31, 70.0,
            "You make an unstrung emerald amulet.",
        ),
        RUBY_AMULET(
            JewelryCategory.AMULET, "obj.amulet_mould", "obj.ruby", "rubies",
            "obj.unstrung_ruby_amulet", "Ruby amulet (u)", 50, 85.0,
            "You make an unstrung ruby amulet.",
        ),
        DIAMOND_AMULET(
            JewelryCategory.AMULET, "obj.amulet_mould", "obj.diamond", "diamonds",
            "obj.unstrung_diamond_amulet", "Diamond amulet (u)", 70, 100.0,
            "You make an unstrung diamond amulet.",
        ),
        DRAGONSTONE_AMULET(
            JewelryCategory.AMULET, "obj.amulet_mould", "obj.dragonstone", "dragonstones",
            "obj.unstrung_dragonstone_amulet", "Dragonstone amulet (u)", 80, 150.0,
            "You make an unstrung dragonstone amulet.",
        ),
        GOLD_BRACELET(
            JewelryCategory.BRACELET, "obj.bracelet_mould", null, "",
            "obj.gold_bracelet", "Gold bracelet", 7, 25.0,
            "You make a gold bracelet.",
        ),
        SAPPHIRE_BRACELET(
            JewelryCategory.BRACELET, "obj.bracelet_mould", "obj.sapphire", "sapphires",
            "obj.sapphire_bracelet", "Sapphire bracelet", 23, 60.0,
            "You set the sapphire into a bracelet.",
        ),
        EMERALD_BRACELET(
            JewelryCategory.BRACELET, "obj.bracelet_mould", "obj.emerald", "emeralds",
            "obj.emerald_bracelet", "Emerald bracelet", 30, 65.0,
            "You set the emerald into a bracelet.",
        ),
        RUBY_BRACELET(
            JewelryCategory.BRACELET, "obj.bracelet_mould", "obj.ruby", "rubies",
            "obj.ruby_bracelet", "Ruby bracelet", 42, 80.0,
            "You set the ruby into a bracelet.",
        ),
        DIAMOND_BRACELET(
            JewelryCategory.BRACELET, "obj.bracelet_mould", "obj.diamond", "diamonds",
            "obj.diamond_bracelet", "Diamond bracelet", 58, 95.0,
            "You set the diamond into a bracelet.",
        ),
        DRAGONSTONE_BRACELET(
            JewelryCategory.BRACELET, "obj.bracelet_mould", "obj.dragonstone", "dragonstones",
            "obj.dragonstone_bracelet", "Dragonstone bracelet", 74, 110.0,
            "You set the dragonstone into a bracelet.",
        ),
    }

    private enum class JewelryCategory(val mouldInternal: String, val displayName: String) {
        RING("obj.ring_mould", "Ring"),
        NECKLACE("obj.necklace_mould", "Necklace"),
        AMULET("obj.amulet_mould", "Amulet"),
        BRACELET("obj.bracelet_mould", "Bracelet"),
    }

    private enum class AmuletStringRecipe(
        val unstrungInternal: String,
        val strungInternal: String,
        val levelReq: Int,
    ) {
        GOLD("obj.unstrung_gold_amulet", "obj.strung_gold_amulet", 8),
        SAPPHIRE("obj.unstrung_sapphire_amulet", "obj.strung_sapphire_amulet", 24),
        EMERALD("obj.unstrung_emerald_amulet", "obj.strung_emerald_amulet", 31),
        RUBY("obj.unstrung_ruby_amulet", "obj.strung_ruby_amulet", 50),
        DIAMOND("obj.unstrung_diamond_amulet", "obj.strung_diamond_amulet", 70),
        DRAGONSTONE("obj.unstrung_dragonstone_amulet", "obj.strung_dragonstone_amulet", 80),
    }

    companion object {
        private const val STRINGING_XP = 4.0
    }
}
