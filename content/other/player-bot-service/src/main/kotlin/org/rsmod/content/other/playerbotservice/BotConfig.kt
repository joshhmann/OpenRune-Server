package org.rsmod.content.other.playerbotservice

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * Bot config hierarchy:
 * 1. Default values (all disabled)
 * 2. `bots.yml` at repo root (if present)
 * 3. `bots:` section in `game.yml` at repo root (if present)
 *
 * A bot module checks its flag in `startup()` and skips initialization when disabled. This makes
 * every bot module truly pluggable — compile always, run only when enabled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public data class BotConfigYaml(
    @JsonProperty("enabled") val enabled: Boolean = true,
    @JsonProperty("progressive") val progressive: Boolean = true,
    @JsonProperty("agent-bridge") val agentBridge: Boolean = false,
    @JsonProperty("agent-bridge-port") val agentBridgePort: Int = 43595,
    @JsonProperty("llm-enabled") val llmEnabled: Boolean = false,
    @JsonProperty("llm-url") val llmUrl: String = "http://localhost:11434/api/generate",
    @JsonProperty("llm-model") val llmModel: String = "llama3",
)

public object BotConfig {
    private val logger = com.github.michaelbull.logging.InlineLogger()
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    private const val BOTS_YML = "bots.yml"
    private const val GAME_YML = "game.yml"

    public val config: BotConfigYaml by lazy { loadConfig() }

    private fun loadConfig(): BotConfigYaml {
        // Try standalone bots.yml first
        val botsFile = File(BOTS_YML)
        if (botsFile.exists()) {
            return try {
                mapper.readValue(botsFile, BotConfigYaml::class.java).also {
                    logger.info { "[BotConfig] Loaded from $BOTS_YML" }
                }
            } catch (e: Exception) {
                logger.warn(e) { "[BotConfig] Failed to parse $BOTS_YML, falling back" }
                BotConfigYaml()
            }
        }

        // Fallback: read bots: section from game.yml
        val gameFile = File(GAME_YML)
        if (gameFile.exists()) {
            return try {
                val tree = mapper.readTree(gameFile)
                val botsNode = tree.get("bots") ?: return BotConfigYaml()
                mapper.treeToValue(botsNode, BotConfigYaml::class.java).also {
                    logger.info { "[BotConfig] Loaded from $GAME_YML(bots:)" }
                }
            } catch (e: Exception) {
                logger.warn(e) {
                    "[BotConfig] Failed to parse bots: from $GAME_YML, using defaults"
                }
                BotConfigYaml()
            }
        }

        return BotConfigYaml()
    }
}
