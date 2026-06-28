package org.rsmod.content.other.progressivebots

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/** Configuration for a single progressive bot. */
data class BotDef(
    val username: String,
    val planner: BotPlanner,
    val spawnX: Int,
    val spawnZ: Int,
    val spawnPlane: Int = 0,
    val male: Boolean = true,
    val head: Int = 0,
    val body: Int = 18,
    val legs: Int = 26,
    val skinColor: Int = 0,
)

/** Reads bot definitions from an embedded list or a yaml file. */
object BotConfig {
    private val logger = com.github.michaelbull.logging.InlineLogger()
    private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    val bots: List<BotDef> by lazy { loadBots() }

    private fun loadBots(): List<BotDef> {
        val file = File("progressive_bots.yml")
        if (file.exists()) {
            try {
                val loaded: List<BotDef> = mapper.readValue(file)
                logger.info {
                    "[ProgressiveBots] Loaded ${loaded.size} bots from progressive_bots.yml"
                }
                return loaded
            } catch (e: Exception) {
                logger.warn(e) {
                    "[ProgressiveBots] Failed to parse progressive_bots.yml, falling back to default"
                }
            }
        }

        return listOf(
            // — SKILLERS (25) —
            BotDef("fallenhero11", BotPlanner.Skiller, 3222, 3222),
            BotDef("steelchief29", BotPlanner.Skiller, 3230, 3215),
            BotDef("ancientcrusa", BotPlanner.Skiller, 3225, 3228),
            BotDef("warpeddragon", BotPlanner.Skiller, 3240, 3210),
            BotDef("goldvagabond", BotPlanner.Skiller, 3215, 3235),
            BotDef("divineknight", BotPlanner.Skiller, 3235, 3225),
            BotDef("fierceknight", BotPlanner.Skiller, 3200, 3240),
            BotDef("swiftpaladin", BotPlanner.Skiller, 3245, 3205),
            BotDef("savageranger", BotPlanner.Skiller, 3210, 3245),
            BotDef("savagemonk", BotPlanner.Skiller, 3232, 3218),
            BotDef("evilnomad", BotPlanner.Skiller, 3228, 3220),
            BotDef("steelsamurai", BotPlanner.Skiller, 3218, 3230),
            BotDef("ragingshaman", BotPlanner.Skiller, 3220, 3210),
            BotDef("toxiclord", BotPlanner.Skiller, 3242, 3222),
            BotDef("goldcrusade1", BotPlanner.Skiller, 3205, 3238),
            BotDef("swiftvindica", BotPlanner.Skiller, 3238, 3212),
            BotDef("phantomshama", BotPlanner.Skiller, 3212, 3242),
            BotDef("wiseninja263", BotPlanner.Skiller, 3248, 3208),
            BotDef("noblemage", BotPlanner.Skiller, 3208, 3248),
            BotDef("crazedangel", BotPlanner.Skiller, 3226, 3226),
            BotDef("lonemerc950", BotPlanner.Skiller, 3230, 3230),
            BotDef("fallensentin", BotPlanner.Skiller, 3215, 3215),
            BotDef("grimglad364", BotPlanner.Skiller, 3240, 3240),
            BotDef("ragingoutlaw", BotPlanner.Skiller, 3200, 3220),
            BotDef("zensentinel6", BotPlanner.Skiller, 3220, 3200),

            // — FIGHTERS (20) —
            BotDef("arcaneraider", BotPlanner.Fighter, 3222, 3222),
            BotDef("brokenrogue", BotPlanner.Fighter, 3230, 3215),
            BotDef("bloodraider", BotPlanner.Fighter, 3240, 3220),
            BotDef("darkhero", BotPlanner.Fighter, 3210, 3230),
            BotDef("ironhunter", BotPlanner.Fighter, 3225, 3218),
            BotDef("royalwarrior", BotPlanner.Fighter, 3235, 3228),
            BotDef("wildranger93", BotPlanner.Fighter, 3205, 3240),
            BotDef("chaossage340", BotPlanner.Fighter, 3242, 3210),
            BotDef("savagevagabo", BotPlanner.Fighter, 3218, 3235),
            BotDef("swiftpirate8", BotPlanner.Fighter, 3228, 3225),
            BotDef("stormdruid", BotPlanner.Fighter, 3200, 3215),
            BotDef("grimking", BotPlanner.Fighter, 3245, 3220),
            BotDef("mythicwarrio", BotPlanner.Fighter, 3212, 3240),
            BotDef("stormviking", BotPlanner.Fighter, 3238, 3205),
            BotDef("darkguard", BotPlanner.Fighter, 3220, 3235),
            BotDef("blazingsenti", BotPlanner.Fighter, 3230, 3212),
            BotDef("deadlyscout", BotPlanner.Fighter, 3208, 3245),
            BotDef("burninghawk2", BotPlanner.Fighter, 3248, 3218),
            BotDef("fierceduke", BotPlanner.Fighter, 3215, 3228),
            BotDef("bravehero", BotPlanner.Fighter, 3232, 3220),

            // — BALANCED (20) —
            BotDef("fallenprophe", BotPlanner.Balanced, 3222, 3222),
            BotDef("bravereaper5", BotPlanner.Balanced, 3235, 3210),
            BotDef("wisecrusade", BotPlanner.Balanced, 3210, 3235),
            BotDef("divinebaron", BotPlanner.Balanced, 3240, 3225),
            BotDef("cursedslayer", BotPlanner.Balanced, 3225, 3240),
            BotDef("lonedruid", BotPlanner.Balanced, 3205, 3220),
            BotDef("nobleoutlaw", BotPlanner.Balanced, 3238, 3215),
            BotDef("feralrogue", BotPlanner.Balanced, 3218, 3238),
            BotDef("steelviking", BotPlanner.Balanced, 3245, 3200),
            BotDef("noblehero", BotPlanner.Balanced, 3200, 3245),
            BotDef("crystalwizar", BotPlanner.Balanced, 3233, 3213),
            BotDef("shadowhunter", BotPlanner.Balanced, 3213, 3233),
            BotDef("coldwarlock", BotPlanner.Balanced, 3243, 3218),
            BotDef("darkjuggerna", BotPlanner.Balanced, 3208, 3243),
            BotDef("freetitan", BotPlanner.Balanced, 3235, 3225),
            BotDef("wildavenger", BotPlanner.Balanced, 3215, 3215),
            BotDef("shadowfist", BotPlanner.Balanced, 3248, 3230),
            BotDef("silentreaver", BotPlanner.Balanced, 3202, 3238),
            BotDef("frostraider", BotPlanner.Balanced, 3242, 3202),
            BotDef("dreadknight5", BotPlanner.Balanced, 3212, 3248),

            // — SOCIAL (15) —
            BotDef("sweetdreams", BotPlanner.Social, 3222, 3222),
            BotDef("quietwhisper", BotPlanner.Social, 3225, 3220),
            BotDef("blueskies", BotPlanner.Social, 3218, 3225),
            BotDef("daisychain", BotPlanner.Social, 3230, 3218),
            BotDef("nightbreeze", BotPlanner.Social, 3220, 3230),
            BotDef("goldenstar", BotPlanner.Social, 3215, 3228),
            BotDef("softrain", BotPlanner.Social, 3235, 3215),
            BotDef("moonlight", BotPlanner.Social, 3212, 3235),
            BotDef("rosethorn", BotPlanner.Social, 3240, 3210),
            BotDef("silvermist", BotPlanner.Social, 3208, 3240),
            BotDef("velvetsky", BotPlanner.Social, 3245, 3225),
            BotDef("embergleam", BotPlanner.Social, 3200, 3225),
            BotDef("shimmeringd", BotPlanner.Social, 3238, 3232),
            BotDef("zephyrwind", BotPlanner.Social, 3202, 3212),
            BotDef("crystaldew", BotPlanner.Social, 3248, 3235),

            // — VENDORS (15) —
            BotDef("copperforge", BotPlanner.Vendor, 3215, 3245),
            BotDef("irontrade", BotPlanner.Vendor, 3210, 3240),
            BotDef("goldbargain", BotPlanner.Vendor, 3220, 3250),
            BotDef("silvermerch", BotPlanner.Vendor, 3205, 3235),
            BotDef("bronzestall", BotPlanner.Vendor, 3225, 3255),
            BotDef("steelmarket", BotPlanner.Vendor, 3212, 3242),
            BotDef("mithrilbazz", BotPlanner.Vendor, 3228, 3248),
            BotDef("addycart", BotPlanner.Vendor, 3218, 3238),
            BotDef("runeexchange", BotPlanner.Vendor, 3230, 3252),
            BotDef("drakeshop", BotPlanner.Vendor, 3200, 3230),
            BotDef("dwarfbuyer", BotPlanner.Vendor, 3235, 3250),
            BotDef("gnomegoods", BotPlanner.Vendor, 3208, 3248),
            BotDef("wisetrade", BotPlanner.Vendor, 3240, 3240),
            BotDef("fairprice", BotPlanner.Vendor, 3215, 3232),
            BotDef("elvenbazaar", BotPlanner.Vendor, 3202, 3242),

            // — PKERS (10) —
            BotDef("warriorboss", BotPlanner.PKer, 3222, 3222),
            BotDef("wildraider9", BotPlanner.PKer, 3100, 3250),
            BotDef("maceman", BotPlanner.PKer, 3140, 3300),
            BotDef("hammerhead", BotPlanner.PKer, 3165, 3235),
            BotDef("skullsmasher", BotPlanner.PKer, 3175, 3260),
            BotDef("darkreaver", BotPlanner.PKer, 3130, 3280),
            BotDef("bloodaxe", BotPlanner.PKer, 3180, 3240),
            BotDef("deathstrike", BotPlanner.PKer, 3150, 3265),
            BotDef("ravager", BotPlanner.PKer, 3110, 3270),
            BotDef("doombringer9", BotPlanner.PKer, 3190, 3225),

            // — ROMANCE SCAMMERS (5) —
            BotDef("sweetkiss", BotPlanner.Social, 3222, 3222),
            BotDef("lovepotion", BotPlanner.Social, 3225, 3220),
            BotDef("trueheart", BotPlanner.Social, 3218, 3228),
            BotDef("velvettouch", BotPlanner.Social, 3230, 3218),
            BotDef("forevermore", BotPlanner.Social, 3220, 3230),
        )
    }
}
