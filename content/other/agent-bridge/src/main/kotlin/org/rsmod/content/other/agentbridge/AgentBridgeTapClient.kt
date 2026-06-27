package org.rsmod.content.other.agentbridge

import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

/**
 * Lightweight client wrapper that mirrors all traffic to [delegate] and taps selected outgoing
 * packets for AgentBridge state snapshots.
 */
internal class AgentBridgeTapClient(
    private val delegate: Client<Any, Any>,
    private val sink: PacketTapSink,
) : Client<Any, Any> {

    override fun close() {
        delegate.close()
    }

    override fun write(message: Any) {
        when (message.javaClass.simpleName) {
            "MessageGame" -> {
                val type = readInt(message, "type") ?: 0
                val text = readString(message, "message")
                if (text != null) {
                    sink.onGameMessage(type, text)
                }
            }
            "IfSetText" -> {
                val interfaceId = readInt(message, "interfaceId") ?: return
                val componentId = readInt(message, "componentId") ?: return
                val text = readString(message, "text") ?: return
                sink.onInterfaceText(interfaceId, componentId, text)
            }
        }
        delegate.write(message)
    }

    override fun read(player: Player) {
        delegate.read(player)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun flushHighPriority() {
        delegate.flushHighPriority()
    }

    override fun unregister(service: Any, player: Player) {
        delegate.unregister(service, player)
    }

    private fun readInt(message: Any, property: String): Int? {
        // HYGIENE: "get" is a JavaBeans reflection prefix, not an ObjType reference
        val getter = "get" + property.replaceFirstChar { it.uppercaseChar() }
        val method = runCatching { message.javaClass.getMethod(getter) }.getOrNull() ?: return null
        return (runCatching { method.invoke(message) }.getOrNull() as? Number)?.toInt()
    }

    private fun readString(message: Any, property: String): String? {
        // HYGIENE: "get" is a JavaBeans reflection prefix, not an ObjType reference
        val getter = "get" + property.replaceFirstChar { it.uppercaseChar() }
        val method = runCatching { message.javaClass.getMethod(getter) }.getOrNull() ?: return null
        return runCatching { method.invoke(message) }.getOrNull() as? String
    }
}

internal interface PacketTapSink {
    fun onGameMessage(type: Int, text: String)

    fun onInterfaceText(interfaceId: Int, componentId: Int, text: String)
}
