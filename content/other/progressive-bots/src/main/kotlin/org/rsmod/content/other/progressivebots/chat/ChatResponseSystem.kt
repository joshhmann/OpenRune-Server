package org.rsmod.content.other.progressivebots.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.random.Random
import org.rsmod.api.player.stat.stat
import org.rsmod.game.entity.Player

@JsonIgnoreProperties(ignoreUnknown = true)
data class Reply(val text: String, val weight: Int = 1, val mood: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Intent(
    val id: String? = null,
    val patterns: List<String> = emptyList(),
    val replies: List<Reply> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatConfig(
    val greetings: Intent? = null,
    val help: Intent? = null,
    val intents: List<Intent> = emptyList(),
    val fallback: List<Reply> = emptyList(),
    val greetingReplies: List<Reply> = emptyList(),
)

object ChatResponseSystem {
    private val logger = com.github.michaelbull.logging.InlineLogger()
    private val mapper = ObjectMapper().registerKotlinModule()
    private var config: ChatConfig? = null

    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        val file =
            File(
                "/root/Runescape/open_rune/OpenRune-Server/lost-city-progressive-clone/engine/data/bot/chat_responses.json"
            )
        if (file.exists()) {
            try {
                config = mapper.readValue(file, ChatConfig::class.java)
                logger.info { "[ChatResponseSystem] Loaded bot chat responses successfully." }
            } catch (e: Exception) {
                logger.warn(e) { "[ChatResponseSystem] Failed to parse chat_responses.json" }
            }
        } else {
            logger.warn {
                "[ChatResponseSystem] chat_responses.json not found at ${file.absolutePath}"
            }
        }
    }

    fun handleIncomingChat(
        sender: Player,
        bot: Player,
        message: String,
    ): CompletableFuture<String?> {
        val botCfg = org.rsmod.content.other.playerbotservice.BotConfig.config
        if (!botCfg.llmEnabled) {
            return CompletableFuture.completedFuture(getPatternResponse(sender, bot, message))
        }

        return queryLLM(sender, bot, message)
            .thenApply { llmResponse ->
                if (llmResponse != null && llmResponse.isNotBlank()) {
                    llmResponse
                } else {
                    getPatternResponse(sender, bot, message)
                }
            }
            .exceptionally { e ->
                logger.debug { "Exception in handleIncomingChat: ${e.message}" }
                getPatternResponse(sender, bot, message)
            }
    }

    private fun queryLLM(sender: Player, bot: Player, message: String): CompletableFuture<String?> {
        val botCfg = org.rsmod.content.other.playerbotservice.BotConfig.config
        val url = botCfg.llmUrl
        val model = botCfg.llmModel

        val prompt =
            "You are a RuneScape player bot named ${bot.avatar.name}. " +
                "Your woodcutting level is ${bot.stat("woodcutting")}, mining is ${bot.stat("mining")}. " +
                "You are talking to player ${sender.avatar.name}. They said: \"$message\". " +
                "Reply very briefly (max 80 chars) in typical OSRS player style (casual, lowercase, short slang like wc, ty, lol, no capitalization)."

        val payload = mapOf("model" to model, "prompt" to prompt, "stream" to false)

        return try {
            val jsonString = mapper.writeValueAsString(payload)
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonString))
                    .build()

            httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply { response ->
                    if (response.statusCode() == 200) {
                        val node = mapper.readTree(response.body())
                        node.get("response")?.asText()?.trim()
                    } else {
                        logger.debug { "LLM returned non-200 code: ${response.statusCode()}" }
                        null
                    }
                }
                .exceptionally { e ->
                    logger.debug { "LLM request threw exception: ${e.message}" }
                    null
                }
        } catch (e: Exception) {
            logger.debug { "Failed to serialize LLM payload: ${e.message}" }
            CompletableFuture.completedFuture(null)
        }
    }

    private fun getPatternResponse(sender: Player, bot: Player, message: String): String? {
        val cfg = config ?: return null
        val cleanMsg = message.lowercase().trim().replace(Regex("[^a-z0-9\\s]"), "")

        // 1. Check custom intents
        for (intent in cfg.intents) {
            if (intent.patterns.any { cleanMsg.contains(it) }) {
                return formatResponse(sender, bot, intent.replies)
            }
        }

        // 2. Check greetings
        val greetings = cfg.greetings
        if (greetings != null && greetings.patterns.any { cleanMsg.contains(it) }) {
            return formatResponse(sender, bot, greetings.replies)
        }

        // 3. Check help
        val help = cfg.help
        if (help != null && help.patterns.any { cleanMsg.contains(it) }) {
            return formatResponse(sender, bot, help.replies)
        }

        // 4. Optionally fallback with a small probability
        if (Random.nextInt(100) < 20) {
            return formatResponse(sender, bot, cfg.fallback)
        }

        return null
    }

    private fun formatResponse(sender: Player, bot: Player, replies: List<Reply>): String? {
        if (replies.isEmpty()) return null

        val totalWeight = replies.sumOf { it.weight }
        var roll = Random.nextInt(totalWeight)
        var selectedReply = replies.first()

        for (reply in replies) {
            roll -= reply.weight
            if (roll < 0) {
                selectedReply = reply
                break
            }
        }

        return formatReply(selectedReply.text, sender, bot)
    }

    private fun formatReply(template: String, sender: Player, bot: Player): String {
        var result =
            template.replace("{name}", sender.avatar.name).replace("{myname}", bot.avatar.name)

        val statNames =
            listOf(
                "attack",
                "defence",
                "strength",
                "hitpoints",
                "ranged",
                "prayer",
                "magic",
                "cooking",
                "woodcutting",
                "fletching",
                "fishing",
                "firemaking",
                "crafting",
                "smithing",
                "mining",
                "herblore",
                "agility",
                "thieving",
                "runecraft",
            )

        for (statName in statNames) {
            val placeholder = "{$statName}"
            if (result.contains(placeholder)) {
                val level = bot.stat(statName)
                result = result.replace(placeholder, level.toString())
            }
        }

        if (result.contains("{total}")) {
            val total = statNames.sumOf { bot.stat(it) }
            result = result.replace("{total}", total.toString())
        }

        return result
    }
}
