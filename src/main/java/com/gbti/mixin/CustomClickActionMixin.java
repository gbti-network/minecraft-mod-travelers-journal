package com.gbti.mixin;

import com.gbti.TravelersJournal;
import com.gbti.dialog.DialogRouter;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes travelers_journal:* dialog buttons to {@link DialogRouter}. Built into the 1.21.6 and
 * later jars only, since server dialogs start there; Fabric API has no hook for their clicks.
 *
 * <p>handleCustomClickAction is defined on the common listener. The injection point is its call to
 * MinecraftServer.handleCustomClickAction rather than HEAD: vanilla first moves the packet onto the
 * server thread, so HEAD would run twice, once on the network thread. At the call it runs once, on
 * the server thread. Only this mod's namespace is cancelled, and a handler error is logged rather
 * than allowed to drop the connection.</p>
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CustomClickActionMixin {
    @Inject(
            method = "handleCustomClickAction",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.21.11 {
                    target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
                    //?} else {
                    /*target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/ResourceLocation;Ljava/util/Optional;)V"),
                    *///?}
            cancellable = true)
    private void travelers_journal$onCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!DialogRouter.NAMESPACE.equals(packet.id().getNamespace())) {
            return;
        }
        try {
            // Only during play: the configuration-phase listener has no player
            if ((Object) this instanceof ServerGamePacketListenerImpl game) {
                ServerPlayer player = game.player;
                if (player != null) {
                    DialogRouter.handle(player, packet.id().getPath(), packet.payload());
                }
            }
        } catch (Throwable t) {
            TravelersJournal.logError("[TJ] A journal dialog button failed: {}", t.toString());
        }
        ci.cancel();
    }
}
