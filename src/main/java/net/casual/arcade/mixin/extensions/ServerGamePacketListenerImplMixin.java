package net.casual.arcade.mixin.extensions;

import net.casual.arcade.ducks.Arcade$ExtensionHolder;
import net.casual.arcade.ducks.Arcade$TemporaryExtensionHolder;
import net.casual.arcade.events.GlobalEventHandler;
import net.casual.arcade.events.player.PlayerCreatedEvent;
import net.casual.arcade.extensions.ExtensionHolder;
import net.casual.arcade.extensions.ExtensionMap;
import net.casual.arcade.utils.ExtensionUtils;
import net.casual.arcade.utils.PlayerUtils;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin implements Arcade$ExtensionHolder {
	@Shadow public ServerPlayer player;

	@Unique private ExtensionMap arcade$extensionMap;

	@Inject(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/Object;<init>()V",
			shift = At.Shift.AFTER
		)
	)
	private void mergeExtensionMap(MinecraftServer server, Connection connection, ServerPlayer player, CallbackInfo ci) {
		this.arcade$extensionMap = ((Arcade$TemporaryExtensionHolder) player).arcade$getTemporaryExtensionMap();
	}

	@Inject(
		method = "<init>",
		at = @At("TAIL")
	)
	private void onCreatePlayer(MinecraftServer server, Connection connection, ServerPlayer player, CallbackInfo ci) {
		PlayerCreatedEvent event = new PlayerCreatedEvent(player);
		GlobalEventHandler.broadcast(event);
	}

	@Override
	public ExtensionMap arcade$getExtensionMap() {
		return this.arcade$extensionMap;
	}
}
