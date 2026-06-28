package org.rsmod.api.net.rsprot

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import net.rsprot.protocol.game.outgoing.info.InfoProtocols
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfoPacket
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.util.PacketResult
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.api.config.refs.params
import org.rsmod.api.net.rsprot.ext.setFaceTarget
import org.rsmod.api.player.righthand
import org.rsmod.game.client.ClientCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

/**
 * rsprot player-info cycle for server-owned players without a client session.
 *
 * Noop players still need a player avatar in rsprot's global info protocol; otherwise real clients
 * can have a `Player` in `PlayerList` with nothing to encode in nearby-player updates.
 */
internal class RspBotCycle(private val infos: Infos, private val infoProtocols: InfoProtocols) :
    ClientCycle {
    private var knownCoords: CoordGrid = CoordGrid.ZERO

    private var knownCachedSpeed: MoveSpeed = MoveSpeed.Stationary

    private var knownFaceEntity: Int? = -1

    private var released: Boolean = false

    private val playerInfo
        get() = infos.playerInfo

    private val playerExtendedInfo: PlayerAvatarExtendedInfo
        get() = playerInfo.avatar.extendedInfo

    override fun update(player: Player) {
        if (released) {
            return
        }
        player.updateMoveSpeed()
        player.updateCoords()
        player.applyExactMove()
        player.applyPublicMessage()
        player.applyFacePathingEntity()
        player.applyFaceAngle()
        player.applyAnim()
        player.applySpotanims()
        player.applySay()
        player.applyHeadbars()
        player.applyHitmarks()
        player.syncAppearance()
    }

    override fun flush(player: Player) {
        // Server-owned bots have no session to flush. Real clients receive this bot through their
        // own RspCycle after the shared info protocol is updated. The per-bot packet results still
        // need releasing every cycle so rsprot can compute the next packet without warnings.
        releaseGeneratedPackets()
    }

    override fun release() {
        if (released) {
            return
        }
        releaseGeneratedPackets()
        released = true
        infoProtocols.dealloc(infos)
    }

    private fun releaseGeneratedPackets() {
        val infoPackets = infos.getPackets()
        val rootPackets = infoPackets.rootWorldInfoPackets

        releasePacketResult(rootPackets.worldEntityInfo)
        releasePacketResult(rootPackets.playerInfo)
        releaseNpcInfoPacket(rootPackets.npcInfo)
        for (world in infoPackets.activeWorlds) {
            releaseNpcInfoPacket(world.npcInfo)
        }
    }

    private fun <T : OutgoingGameMessage> releasePacketResult(result: PacketResult<T>) {
        result.getOrThrow().safeRelease()
    }

    private fun releaseNpcInfoPacket(result: PacketResult<NpcInfoPacket>) {
        if (result.isEmpty()) {
            result.safeReleaseOrThrow()
            return
        }
        result.getOrThrow().safeRelease()
    }

    private fun Player.updateMoveSpeed() {
        if (knownCachedSpeed != cachedMoveSpeed) {
            val extendedInfo = playerInfo.avatar.extendedInfo
            extendedInfo.setMoveSpeed(cachedMoveSpeed.steps)
            knownCachedSpeed = cachedMoveSpeed
        }
        val moveSpeed = resolvePendingMoveSpeed()
        if (moveSpeed != cachedMoveSpeed && coords != knownCoords) {
            val extendedInfo = playerInfo.avatar.extendedInfo
            extendedInfo.setTempMoveSpeed(moveSpeed.steps)
        }
    }

    private fun Player.resolvePendingMoveSpeed(): MoveSpeed =
        when {
            pendingTelejump -> MoveSpeed.Stationary
            pendingTeleport -> MoveSpeed.Walk
            pendingStepCount == 1 -> MoveSpeed.Walk
            pendingStepCount == 2 -> MoveSpeed.Run
            else -> moveSpeed
        }

    private fun Player.updateCoords() {
        infos.updateRootCoord(level, x, z)
        knownCoords = coords
    }

    private fun Player.applyPublicMessage() {
        val message = publicMessage ?: return
        playerExtendedInfo.setChat(
            colour = message.colour,
            effects = message.effect,
            modicon = message.modIcon,
            autotyper = message.autoTyper,
            text = message.text,
            pattern = message.pattern,
        )
        publicMessage = null
    }

    private fun Player.applyFacePathingEntity() {
        if (knownFaceEntity != faceEntity.entitySlot) {
            playerExtendedInfo.setFaceTarget(faceEntity)
            knownFaceEntity = faceEntity.entitySlot
        }
    }

    private fun Player.applyFaceAngle() {
        if (pendingFaceAngle != EntityFaceAngle.NULL) {
            playerExtendedInfo.setFaceAngle(pendingFaceAngle.intValue)
        }
    }

    private fun Player.applyAnim() {
        when (pendingSequence) {
            EntitySeq.NULL -> return
            EntitySeq.ZERO -> playerExtendedInfo.setSequence(-1, 0)
            else -> playerExtendedInfo.setSequence(pendingSequence.id, pendingSequence.delay)
        }
    }

    private fun Player.applySpotanims() {
        if (pendingSpotanims.isEmpty) {
            return
        }
        for (packed in pendingSpotanims.longIterator()) {
            val (id, delay, height, slot) = EntitySpotanim(packed)
            playerExtendedInfo.setSpotAnim(slot, id, delay, height)
        }
    }

    private fun Player.applySay() {
        val text = pendingSay ?: return
        playerExtendedInfo.setSay(text)
    }

    private fun Player.applyExactMove() {
        val move = pendingExactMove ?: return
        playerExtendedInfo.setExactMove(
            deltaX1 = move.deltaX1,
            deltaZ1 = move.deltaZ1,
            delay1 = move.clientDelay1,
            deltaX2 = move.deltaX2,
            deltaZ2 = move.deltaZ2,
            delay2 = move.clientDelay2,
            angle = move.direction,
        )
    }

    private fun Player.applyHeadbars() {
        for (packedHeadbar in activeHeadbars.longIterator()) {
            val headbar = Headbar(packedHeadbar)
            playerExtendedInfo.addHeadBar(
                sourceIndex = if (headbar.isNoSource) -1 else headbar.sourceSlot,
                selfType = headbar.self,
                otherType = if (headbar.isPrivate) -1 else headbar.public,
                startFill = headbar.startFill,
                endFill = headbar.endFill,
                startTime = headbar.startTime,
                endTime = headbar.endTime,
            )
        }
    }

    private fun Player.applyHitmarks() {
        for (packedHitmark in activeHitmarks.longIterator()) {
            val hitmark = Hitmark(packedHitmark)
            playerExtendedInfo.addHitMark(
                sourceIndex = if (hitmark.isNoSource) -1 else hitmark.sourceSlot,
                selfType = hitmark.self,
                sourceType = hitmark.source,
                otherType = if (hitmark.isPrivate) -1 else hitmark.public,
                value = hitmark.damage,
                delay = hitmark.delay,
            )
        }
    }

    private fun Player.syncAppearance() {
        if (!appearance.rebuild) {
            return
        }
        val info = playerExtendedInfo

        val colours = appearance.coloursSnapshot()
        for (i in colours.indices) {
            info.setColour(i, colours[i].toInt())
        }

        val identKit = appearance.identKitSnapshot()
        for (i in identKit.indices) {
            info.setIdentKit(i, identKit[i].toInt())
        }

        info.setName(displayName)
        info.setOverheadIcon(overheadIcon ?: -1)
        info.setSkullIcon(skullIcon ?: -1)
        info.setCombatLevel(combatLevel)
        info.setBodyType(appearance.bodyType)
        info.setPronoun(appearance.pronoun)
        info.setHidden(appearance.softHidden)

        info.setNameExtras(
            beforeName = appearance.namePrefix ?: "",
            afterName = appearance.nameSuffix ?: "",
            afterCombatLevel = appearance.combatLvlSuffix ?: "",
        )

        val bas = this.appearance.bas
        val weapon = this.righthand
        val transmog = this.transmog

        val readyAnim: Int
        val turnOnSpotAnim: Int
        val walkForwardAnim: Int
        val walkBackAnim: Int
        val walkLeftAnim: Int
        val walkRightAnim: Int
        val runningAnim: Int

        if (bas != null) {
            readyAnim = bas.readyAnim
            turnOnSpotAnim = bas.turnOnSpot
            walkForwardAnim = bas.walkForward
            walkBackAnim = bas.walkBack
            walkLeftAnim = bas.walkLeft
            walkRightAnim = bas.walkRight
            runningAnim = bas.running
        } else if (transmog != null) {
            readyAnim = transmog.standAnim
            turnOnSpotAnim = transmog.rotateBackAnim
            walkForwardAnim = transmog.walkAnim
            walkBackAnim = transmog.walkAnim
            walkLeftAnim = transmog.walkLeftAnim
            walkRightAnim = transmog.walkRightAnim
            runningAnim = transmog.runSequence
        } else if (weapon != null) {
            val type = getInvObj(weapon)
            readyAnim = type.param(params.bas_readyanim).id
            turnOnSpotAnim = type.param(params.bas_turnonspot).id
            walkForwardAnim = type.param(params.bas_walk_f).id
            walkBackAnim = type.param(params.bas_walk_b).id
            walkLeftAnim = type.param(params.bas_walk_l).id
            walkRightAnim = type.param(params.bas_walk_r).id
            runningAnim = type.param(params.bas_running).id
        } else {
            val default =
                ServerCacheManager.getBas("bas.human_default".asRSCM())
                    ?: error("Unable to find Bas Human")
            readyAnim = default.readyAnim
            turnOnSpotAnim = default.turnOnSpot
            walkForwardAnim = default.walkForward
            walkBackAnim = default.walkBack
            walkLeftAnim = default.walkLeft
            walkRightAnim = default.walkRight
            runningAnim = default.running
        }

        info.setTransmogrification(transmog?.id ?: -1)
        info.setBaseAnimationSet(
            readyAnim = readyAnim,
            turnAnim = turnOnSpotAnim,
            walkAnim = walkForwardAnim,
            walkAnimBack = walkBackAnim,
            walkAnimLeft = walkLeftAnim,
            walkAnimRight = walkRightAnim,
            runAnim = runningAnim,
        )

        for (wearpos in Wearpos.visibleWearpos) {
            val obj = worn[wearpos.slot]
            if (obj == null) {
                info.setWornObj(wearpos.slot, -1, -1, -1)
                continue
            }
            val objType = getInvObj(obj)
            info.setWornObj(wearpos.slot, obj.id, objType.wearpos2, objType.wearpos3)
        }
    }
}
