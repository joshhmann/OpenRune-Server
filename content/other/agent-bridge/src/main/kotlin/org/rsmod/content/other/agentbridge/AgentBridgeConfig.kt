package org.rsmod.content.other.agentbridge

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class AgentBridgeConfigYaml(
    @JsonProperty("enabled") val enabled: Boolean = false,
    @JsonProperty("port") val port: Int = 43595,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class LegacyBotsConfigYaml(
    @JsonProperty("enabled") val enabled: Boolean = false,
    @JsonProperty("agent-bridge") val agentBridge: Boolean = false,
    @JsonProperty("agent-bridge-port") val agentBridgePort: Int = 43595,
)

internal object AgentBridgeConfig {
    private val logger = com.github.michaelbull.logging.InlineLogger()
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    private const val AGENT_BRIDGE_YML = "agent-bridge.yml"
    private const val GAME_YML = "game.yml"

    val config: AgentBridgeConfigYaml by lazy { loadConfig() }

    private fun loadConfig(): AgentBridgeConfigYaml {
        val agentBridgeFile = File(AGENT_BRIDGE_YML)
        if (agentBridgeFile.exists()) {
            return try {
                mapper.readValue(agentBridgeFile, AgentBridgeConfigYaml::class.java).also {
                    logger.info { "[AgentBridge] Loaded config from $AGENT_BRIDGE_YML" }
                }
            } catch (e: Exception) {
                logger.warn(e) { "[AgentBridge] Failed to parse $AGENT_BRIDGE_YML, using defaults" }
                AgentBridgeConfigYaml()
            }
        }

        val gameFile = File(GAME_YML)
        if (gameFile.exists()) {
            return try {
                val tree = mapper.readTree(gameFile)
                val agentBridgeNode = tree.get("agent-bridge")
                if (agentBridgeNode != null) {
                    return mapper
                        .treeToValue(agentBridgeNode, AgentBridgeConfigYaml::class.java)
                        .also {
                            logger.info { "[AgentBridge] Loaded from $GAME_YML(agent-bridge:)" }
                        }
                }

                val botsNode = tree.get("bots") ?: return AgentBridgeConfigYaml()
                val legacy = mapper.treeToValue(botsNode, LegacyBotsConfigYaml::class.java)
                AgentBridgeConfigYaml(
                        enabled = legacy.enabled && legacy.agentBridge,
                        port = legacy.agentBridgePort,
                    )
                    .also {
                        logger.info { "[AgentBridge] Loaded legacy flag from $GAME_YML(bots:)" }
                    }
            } catch (e: Exception) {
                logger.warn(e) { "[AgentBridge] Failed to parse config, using defaults" }
                AgentBridgeConfigYaml()
            }
        }

        return AgentBridgeConfigYaml()
    }
}
