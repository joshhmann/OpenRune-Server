package org.rsmod.content.skills.mining

import org.rsmod.api.stats.levelmod.InvisibleLevelMod
import org.rsmod.game.entity.Player
import org.rsmod.map.square.MapSquareKey

class MiningLevelBoosts : InvisibleLevelMod("stat.mining") {
    override fun Player.calculateBoost(): Int {
        var boost = 0

        // Mining Guild invisible +7 boost (members only area)
        if (isInMiningGuild()) {
            boost += MINING_GUILD_BOOST
        }

        return boost
    }

    private fun Player.isInMiningGuild(): Boolean {
        val mapSquare = MapSquareKey.from(coords)
        return mapSquare == MINING_GUILD_REGION
    }

    companion object {
        const val MINING_GUILD_BOOST: Int = 7
        val MINING_GUILD_REGION: MapSquareKey = MapSquareKey(47, 52)
    }
}
