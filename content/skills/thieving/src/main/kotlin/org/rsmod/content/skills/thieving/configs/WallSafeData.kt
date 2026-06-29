package org.rsmod.content.skills.thieving.configs

/**
 * Wall safe thieving data.
 *
 * Wall safes are a F2P thieving mechanic found in the Rogues' Den
 * (and other member-only areas). They involve picking a lock, disarming a
 * trap, and looting.
 *
 * **Deferred:** The 239 cache does not currently contain the wall safe
 * loc objects. This stub exists so the data structure is available when
 * the cache is updated. See porting-roadmap-detailed.md lines 352-357.
 */
public data class WallSafeLoot(
    val obj: String,
    val min: Int = 1,
    val max: Int = min,
    val weight: Double = 1.0,
)

public data class WallSafeEntry(
    val levelReq: Int,
    val xp: Double,
    val respawnTicks: Int,
    val dismantleChance: Int,
    val trapMinDamage: Int,
    val trapMaxDamage: Int,
    val loot: List<WallSafeLoot>,
)

@Suppress("MemberVisibilityCanBePrivate")
public object WallSafeData {
    // Empty for now — wall safe locs not verified present in 239 cache.
    // Populate with entries like:
    // val ROgues_DEN_SAFE = WallSafeEntry(...)
    val locToEntry: Map<String, WallSafeEntry> = emptyMap()
}
