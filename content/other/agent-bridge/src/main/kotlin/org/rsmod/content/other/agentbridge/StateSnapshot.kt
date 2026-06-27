package org.rsmod.content.other.agentbridge

/**
 * Per-tick state snapshot broadcast to LLM testing agents over WebSocket (port 43595). Schema
 * matches the osrs-llm-testing-methodology.docx specification.
 */
data class StateSnapshot(
    val tick: Int,
    val player: PlayerSnapshot,
    val dialog: DialogSnapshot,
    val gameMessages: List<GameMessageSnapshot>,
    /** Event notifications that occurred since last tick (animations, XP gains, etc.) */
    val events: List<PlayerEvent> = emptyList(),
)

data class PlayerSnapshot(
    val name: String,
    val position: PositionSnapshot,
    val skills: Map<String, SkillSnapshot>,
    val inventory: List<InvItemSnapshot>,
    val equipment: List<InvItemSnapshot>,
    /** Current animation sequence ID (0 = idle). */
    val animation: Int,
    /** NPCs within ~16 tiles of the player. Use `index` for interact_npc commands. */
    val nearbyNpcs: List<NearbyNpcSnapshot>,
    /**
     * Game objects (locs) within ~16 tiles of the player. Use `id + x + z` for interact_loc
     * commands.
     */
    val nearbyLocs: List<NearbyLocSnapshot>,
    /** True when a dialogue/chat modal is currently open. */
    val dialog: DialogSnapshot,
    /** Recent game messages sent to the player (oldest -> newest). */
    val gameMessages: List<GameMessageSnapshot>,
    /** Current combat state for the player. */
    val combat: CombatStateSnapshot,
)

data class PositionSnapshot(val x: Int, val z: Int, val plane: Int)

data class SkillSnapshot(
    /** Base level derived from XP — the "real" level, not boosted/drained. */
    val level: Int,
    /** Total XP (fine XP / 10). */
    val xp: Int,
)

data class InvItemSnapshot(
    val slot: Int,
    val id: Int,
    val qty: Int,
    /** Item name for display purposes. */
    val name: String? = null,
)

data class NearbyNpcSnapshot(
    /** NPC type ID. */
    val id: Int,
    /** Server list index — pass to `interact_npc.index`. */
    val index: Int,
    val name: String,
    val x: Int,
    val z: Int,
    /** Distance in tiles from player. */
    val distance: Int,
    /** Current animation sequence ID (0 = idle). */
    val animation: Int,
    /** Current hitpoints (approximation from server state). */
    val hp: Int,
    /** Maximum hitpoints from NPC type. */
    val maxHp: Int,
    /** Current HP percentage (0-100) when max hp is known. */
    val healthPercent: Int?,
    /** True when npc is currently in combat. */
    val inCombat: Boolean,
    /** Last combat cycle timestamp from server state. */
    val combatCycle: Int,
    /** Current combat target index (-1 if none). */
    val targetIndex: Int,
)

data class CombatStateSnapshot(
    val inCombat: Boolean,
    val targetIndex: Int,
    val lastDamageTick: Int,
)

data class NearbyLocSnapshot(
    /** Loc type ID — pass to `interact_loc.id`. */
    val id: Int,
    val name: String,
    val x: Int,
    val z: Int,
    /** Distance in tiles from player. */
    val distance: Int,
)

data class DialogSnapshot(
    val isOpen: Boolean,
    /** Interface ids currently occupying modal slots. */
    val modalInterfaceIds: List<Int>,
    /**
     * Latest interface text updates observed for active modal interfaces.
     *
     * These are best-effort captures from outgoing IfSetText packets and are primarily meant for
     * quest/mechanics test assertions.
     */
    val lines: List<String>,
)

data class GameMessageSnapshot(val type: Int, val text: String)

// ---------------------------------------------------------------------------
// Event types for push-based notifications (replacing polling)
// ---------------------------------------------------------------------------

/** Events that can occur during gameplay, sent as push notifications. */
sealed class PlayerEvent {
    abstract val tick: Int

    /** Animation started playing. */
    data class AnimationStart(override val tick: Int, val animationId: Int) : PlayerEvent()

    /** Animation ended/finished. */
    data class AnimationEnd(override val tick: Int, val animationId: Int) : PlayerEvent()

    /** XP was gained in a skill. */
    data class XpGain(
        override val tick: Int,
        val skill: String,
        val amount: Int,
        val newTotal: Int,
    ) : PlayerEvent()

    /** Item was added to inventory. */
    data class ItemAdded(
        override val tick: Int,
        val itemId: Int,
        val name: String,
        val qty: Int,
        val slot: Int,
    ) : PlayerEvent()

    /** Item was removed from inventory. */
    data class ItemRemoved(
        override val tick: Int,
        val itemId: Int,
        val name: String,
        val qty: Int,
        val slot: Int,
    ) : PlayerEvent()

    /** Player moved to a new position. */
    data class PositionChanged(override val tick: Int, val x: Int, val z: Int, val plane: Int) :
        PlayerEvent()

    /** Door was opened. */
    data class DoorOpened(
        override val tick: Int,
        val x: Int,
        val z: Int,
        val plane: Int,
        val name: String,
    ) : PlayerEvent()

    /** Door could not be opened (locked or other failure). */
    data class DoorLocked(
        override val tick: Int,
        val x: Int,
        val z: Int,
        val plane: Int,
        val name: String,
        val reason: String,
    ) : PlayerEvent()

    /** Bank interface opened. */
    data class BankOpened(override val tick: Int, val itemCount: Int) : PlayerEvent()

    /** Bank interface closed. */
    data class BankClosed(override val tick: Int) : PlayerEvent()

    /** Item deposited to bank. */
    data class ItemDeposited(
        override val tick: Int,
        val slot: Int,
        val id: Int,
        val name: String,
        val count: Int,
    ) : PlayerEvent()

    /** Item withdrawn from bank. */
    data class ItemWithdrawn(
        override val tick: Int,
        val slot: Int,
        val id: Int,
        val name: String,
        val count: Int,
    ) : PlayerEvent()

    /** Shop interface opened. */
    data class ShopOpened(override val tick: Int, val shopTitle: String, val itemCount: Int) :
        PlayerEvent()

    /** Shop interface closed. */
    data class ShopClosed(override val tick: Int) : PlayerEvent()

    /** Item bought from shop. */
    data class ItemBought(
        override val tick: Int,
        val itemId: Int,
        val name: String,
        val count: Int,
        val totalCost: Int,
    ) : PlayerEvent()

    /** Item sold to shop. */
    data class ItemSold(
        override val tick: Int,
        val itemId: Int,
        val name: String,
        val count: Int,
        val totalValue: Int,
    ) : PlayerEvent()

    /** Shop restocked (items regenerated). */
    data class ShopRestocked(override val tick: Int, val shopTitle: String) : PlayerEvent()

    /** Combat started for player. */
    data class CombatStarted(override val tick: Int, val targetIndex: Int) : PlayerEvent()

    /** Combat ended for player. */
    data class CombatEnded(override val tick: Int) : PlayerEvent()

    /** Player took damage. */
    data class DamageTaken(
        override val tick: Int,
        val damage: Int,
        val sourceType: String,
        val sourceIndex: Int,
    ) : PlayerEvent()

    /** Player dealt damage. */
    data class DamageDealt(
        override val tick: Int,
        val damage: Int,
        val targetType: String,
        val targetIndex: Int,
    ) : PlayerEvent()

    /** Player killed a target. */
    data class Kill(override val tick: Int, val targetType: String, val targetIndex: Int) :
        PlayerEvent()
}

/**
 * Ground item snapshot.
 *
 * @property id Item type ID
 * @property name Item name
 * @property count Item quantity
 * @property x X coordinate
 * @property z Z coordinate
 * @property distance Distance from player
 * @property visible Whether item is visible to player
 */
data class GroundItemSnapshot(
    val id: Int,
    val name: String,
    val count: Int,
    val x: Int,
    val z: Int,
    val distance: Int,
    val visible: Boolean = true,
)

/**
 * Ground item scan results.
 *
 * @property items List of ground items found
 * @property scanX Center X of scan
 * @property scanZ Center Z of scan
 * @property radius Scan radius
 * @property timestamp Scan tick timestamp
 */
data class GroundItemScan(
    val items: List<GroundItemSnapshot>,
    val scanX: Int,
    val scanZ: Int,
    val radius: Int,
    val timestamp: Int,
)

/** Ground item related events. */
sealed class GroundItemEvent : PlayerEvent() {
    /** Ground item appeared (spawned or walked into view). */
    data class ItemSpawned(
        override val tick: Int,
        val id: Int,
        val name: String,
        val count: Int,
        val x: Int,
        val z: Int,
    ) : GroundItemEvent()

    /** Ground item was picked up by player. */
    data class ItemPickedUp(
        override val tick: Int,
        val id: Int,
        val name: String,
        val count: Int,
        val x: Int,
        val z: Int,
    ) : GroundItemEvent()

    /** Ground item despawned or walked out of view. */
    data class ItemDespawned(
        override val tick: Int,
        val id: Int,
        val name: String,
        val x: Int,
        val z: Int,
    ) : GroundItemEvent()

    /** Scan completed for ground items. */
    data class ScanCompleted(override val tick: Int, val itemCount: Int, val radius: Int) :
        GroundItemEvent()
}

/**
 * Prayer state snapshot for a player.
 *
 * @property prayerPoints Current prayer points
 * @property maxPrayerPoints Maximum prayer points (based on level)
 * @property prayerLevel Current prayer level
 * @property activePrayers Map of prayer index to prayer name for active prayers
 * @property drainRate Current prayer drain rate (points per tick)
 */
data class PrayerState(
    val prayerPoints: Int,
    val maxPrayerPoints: Int,
    val prayerLevel: Int,
    val activePrayers: Map<Int, String>,
    val drainRate: Double,
)

/** Prayer names indexed by their position. */
enum class PrayerName(val index: Int, val level: Int, val drainRate: Double) {
    THICK_SKIN(0, 1, 0.5),
    BURST_OF_STRENGTH(1, 4, 0.5),
    CLARITY_OF_THOUGHT(2, 7, 0.5),
    ROCK_SKIN(3, 10, 1.0),
    SUPERHUMAN_STRENGTH(4, 13, 1.0),
    IMPROVED_REFLEXES(5, 16, 1.0),
    RAPID_RESTORE(6, 19, 0.2),
    RAPID_HEAL(7, 22, 0.4),
    PROTECT_ITEMS(8, 25, 0.5),
    STEEL_SKIN(9, 28, 2.0),
    ULTIMATE_STRENGTH(10, 31, 2.0),
    INCREDIBLE_REFLEXES(11, 34, 2.0),
    PROTECT_FROM_MAGIC(12, 37, 3.0),
    PROTECT_FROM_MISSILES(13, 40, 3.0),
    PROTECT_FROM_MELEE(14, 43, 3.0);

    companion object {
        /** Get prayer by index. */
        fun byIndex(index: Int): PrayerName? = values().find { it.index == index }

        /** Get prayer by name (case-insensitive). */
        fun byName(name: String): PrayerName? =
            values().find {
                it.name.equals(name, ignoreCase = true) ||
                    it.name.replace("_", " ").equals(name, ignoreCase = true)
            }

        /** Get all prayers available at given level. */
        fun availableAtLevel(level: Int): List<PrayerName> = values().filter { it.level <= level }
    }
}

/** Prayer-related events. */
sealed class PrayerEvent : PlayerEvent() {
    /** Prayer was activated. */
    data class PrayerActivated(
        override val tick: Int,
        val prayerIndex: Int,
        val prayerName: String,
    ) : PrayerEvent()

    /** Prayer was deactivated. */
    data class PrayerDeactivated(
        override val tick: Int,
        val prayerIndex: Int,
        val prayerName: String,
    ) : PrayerEvent()

    /** All prayers deactivated (ran out of prayer points). */
    data class AllPrayersDeactivated(override val tick: Int, val reason: String) : PrayerEvent()

    /** Prayer points drained. */
    data class PrayerDrained(
        override val tick: Int,
        val previousPoints: Int,
        val currentPoints: Int,
        val drainAmount: Int,
    ) : PrayerEvent()

    /** Prayer points restored. */
    data class PrayerRestored(
        override val tick: Int,
        val previousPoints: Int,
        val currentPoints: Int,
        val restoreAmount: Int,
    ) : PrayerEvent()

    /** Prayer points are low (below threshold). */
    data class PrayerLow(override val tick: Int, val prayerPoints: Int, val maxPrayerPoints: Int) :
        PrayerEvent()
}

/**
 * Bank state snapshot for a player.
 *
 * @property isOpen Whether the bank interface is currently open
 * @property items Items currently in the bank
 * @property freeSlots Number of free slots remaining
 * @property totalSlots Total number of bank slots
 */
data class BankState(
    val isOpen: Boolean,
    val items: List<BankItemSnapshot>,
    val freeSlots: Int,
    val totalSlots: Int = 800,
)

/**
 * Single item in the bank.
 *
 * @property slot Bank slot index (0-799)
 * @property id Item type ID
 * @property name Item name
 * @property count Item quantity
 */
data class BankItemSnapshot(val slot: Int, val id: Int, val name: String, val count: Int)

/**
 * Shop state snapshot for a player.
 *
 * @property isOpen Whether a shop interface is currently open
 * @property title Shop name/title
 * @property shopItems Items available to buy from the shop
 * @property playerItems Items the player can sell to the shop
 * @property buyMultiplier Price multiplier for buying (usually 100 = 1x)
 * @property sellMultiplier Price multiplier for selling (usually 60 = 0.6x)
 * @property haggle Haggle factor if applicable
 */
data class ShopState(
    val isOpen: Boolean,
    val title: String,
    val shopItems: List<ShopItemSnapshot>,
    val playerItems: List<ShopItemSnapshot>,
    val buyMultiplier: Int,
    val sellMultiplier: Int,
    val haggle: Int = 0,
)

/**
 * Single item in a shop.
 *
 * @property slot Shop slot index
 * @property id Item type ID
 * @property name Item name
 * @property count Item quantity available
 * @property baseCost Base value of item
 * @property buyPrice Price to buy from shop
 * @property sellPrice Price to sell to shop
 */
data class ShopItemSnapshot(
    val slot: Int,
    val id: Int,
    val name: String,
    val count: Int,
    val baseCost: Int,
    val buyPrice: Int,
    val sellPrice: Int,
)

// ---------------------------------------------------------------------------
// Action response types
// ---------------------------------------------------------------------------

/** Response from executing a bot action. */
data class ActionResponse(
    val success: Boolean,
    val message: String,
    val xpGained: Map<String, Int> = emptyMap(),
    val itemsChanged: List<ItemChange> = emptyList(),
)

data class ItemChange(
    val itemId: Int,
    val name: String,
    val qty: Int,
    val added: Boolean, // true = added, false = removed
)
