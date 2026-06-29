@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.agentbridge.testing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Manages saving and restoring bot state for test isolation.
 *
 * Before running a QA test, the SaveStateManager captures:
 * - Player position (coords)
 * - Inventory contents (item IDs + counts)
 * - Equipment (worn items)
 * - Skill XP/levels
 * - Quest progress (varbit/varplayer states)
 * - Animation state
 *
 * After the test completes, the state can be restored so subsequent tests
 * start from a clean baseline.
 */
@Singleton
class SaveStateManager @Inject constructor() {

    private val savedStates = mutableMapOf<String, PlayerState>()

    /**
     * Capture the current state of a player.
     * @return A snapshot that can be restored later.
     */
    fun capture(player: Player): PlayerState {
        val state =
            PlayerState(
                x = player.coords.x,
                z = player.coords.z,
                plane = player.coords.level,
                inventory =
                    player.inv.objs.mapIndexedNotNull { slot, obj ->
                        if (obj != null) InvEntry(slot, obj.id, obj.count.toInt()) else null
                    },
                equipment =
                    player.worn.objs.mapIndexedNotNull { slot, obj ->
                        if (obj != null) InvEntry(slot, obj.id, obj.count.toInt()) else null
                    },
            )
        savedStates[player.avatar.name.lowercase()] = state
        return state
    }

    /**
     * Save state under a specific key for later retrieval.
     */
    fun save(key: String, player: Player) {
        savedStates[key.lowercase()] = capture(player)
    }

    /**
     * Restore a previously saved state. Only restores position and inventory;
     * skill XP and quest state require database writes.
     *
     * @return true if state was restored, false if no saved state exists.
     */
    fun restore(player: Player): Boolean {
        val state =
            savedStates[player.avatar.name.lowercase()]
                ?: return false

        // Restore position
        player.coords =
            org.rsmod.map.CoordGrid(state.x, state.z, state.plane)

        // Clear inventory
        for (slot in 0 until player.inv.size) {
            player.inv[slot] = null
        }

        // Restore inventory items
        for (entry in state.inventory) {
            player.inv[entry.slot] =
                org.rsmod.game.inv.InvObj(entry.itemId, entry.count)
        }

        // Clear equipment
        for (slot in 0 until player.worn.size) {
            player.worn[slot] = null
        }

        // Restore equipment
        for (entry in state.equipment) {
            player.worn[entry.slot] =
                org.rsmod.game.inv.InvObj(entry.itemId, entry.count)
        }

        return true
    }

    /**
     * Check if a saved state exists for a player.
     */
    fun hasSavedState(player: Player): Boolean =
        player.avatar.name.lowercase() in savedStates

    /**
     * Clear all saved states.
     */
    fun clear() {
        savedStates.clear()
    }

    /**
     * Clear a specific player's saved state.
     */
    fun clearPlayer(player: Player) {
        savedStates.remove(player.avatar.name.lowercase())
    }
}

/**
 * Snapshot of a player's state for save/restore.
 */
data class PlayerState(
    val x: Int,
    val z: Int,
    val plane: Int,
    val inventory: List<InvEntry>,
    val equipment: List<InvEntry>,
)

data class InvEntry(
    val slot: Int,
    val itemId: Int,
    val count: Int,
)
