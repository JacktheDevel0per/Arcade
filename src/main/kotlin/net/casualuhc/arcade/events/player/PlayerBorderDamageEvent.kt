package net.casualuhc.arcade.events.player

import net.casualuhc.arcade.events.core.InvokableEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageSources
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes

data class PlayerBorderDamageEvent(
    val player: ServerPlayer,
    val source: DamageSource,
    val amount: Float
): InvokableEvent<Boolean>() {
    override fun execute(): Boolean {
        return this.player.hurt(this.source, this.amount)
    }
}