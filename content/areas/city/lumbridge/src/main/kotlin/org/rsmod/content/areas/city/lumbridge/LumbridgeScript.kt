package org.rsmod.content.areas.city.lumbridge

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LumbridgeScript
@Inject
constructor(private val locRepo: LocRepository, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.winch") { operateWinch() }
        onOpLoc1("loc.log_withaxe") { takeAxeFromLogs(it.loc) }

        // Castle double doors — handled explicitly here since they may not
        // match the generic DoubleDoorScript content groups in all cache builds
        onOpLoc1("loc.castledoubledoorl") { swapDoorState(it.loc) }
        onOpLoc1("loc.castledoubledoorr") { swapDoorState(it.loc) }
        onOpLoc1("loc.opencastledoubledoorl") { swapDoorState(it.loc) }
        onOpLoc1("loc.opencastledoubledoorr") { swapDoorState(it.loc) }

        // Single doors — castle interior uses poshdoor variants
        onOpLoc1("loc.poshdoor") { swapDoorState(it.loc) }
        onOpLoc1("loc.poshdooropen") { swapDoorState(it.loc) }

        // Elf-fashioned doors (Duke's room) — authentic OSRS behavior
        onOpLoc1("loc.elfdoor") { elfDoorInteract(it.loc) }
        onOpLoc1("loc.elfdooropen") { elfDoorInteract(it.loc) }
    }

    private fun ProtectedAccess.operateWinch() {
        mes("It seems the winch is jammed - I can't move it.")
        soundSynth("synth.lever")
    }

    private suspend fun ProtectedAccess.takeAxeFromLogs(loc: BoundLocInfo) {
        if ("content.woodcutting_axe" in inv) {
            mesbox("You already have an axe.")
            return
        }

        if (inv.isFull()) {
            mesbox("You don't have enough room for the axe.")
            return
        }

        locRepo.change(loc, "loc.log_withoutaxe", 50)
        invAddOrDrop(objRepo, "obj.bronze_axe")
        soundSynth("synth.take_axe")
        objbox("obj.bronze_axe", 400, "You take a bronze axe from the logs.")
    }

    /**
     * Swap a door between closed ↔ open using its next_loc_stage param.
     * Handles both single and double doors; sound is optional.
     */
    private fun ProtectedAccess.swapDoorState(loc: BoundLocInfo) {
        val nextStage = locParam(loc, params.next_loc_stage)
        locParamOrNull(loc, params.opensound)?.let { soundSynth(it) }
        locParamOrNull(loc, params.closesound)?.let { soundSynth(it) }
        locRepo.del(loc, 500)
        locRepo.add(loc.coords, nextStage, 500, loc.turnAngle(rotations = 1), loc.shape)
    }

    /**
     * Elf-fashioned door interaction.
     * These doors are intentionally non-functional on OSRS — matching that behavior.
     */
    private fun ProtectedAccess.elfDoorInteract(loc: BoundLocInfo) {
        mes("It's stuck.")
        soundSynth("synth.lever")
    }
}
