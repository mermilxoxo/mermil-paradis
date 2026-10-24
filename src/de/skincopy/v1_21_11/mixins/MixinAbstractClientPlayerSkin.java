package de.skincopy.v1_21_11.mixins;

import de.skincopy.LiveSkinOverride;
import net.labymod.api.client.entity.player.ClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Updates the object actually returned to the renderer, including replacement skin
 * instances made by the client. Only LocalPlayer implements LabyMod ClientPlayer.
 * This mixin targets Minecraft 1.21.11; no LabyMod core method is modified.
 */
@Mixin(targets = "net.minecraft.client.player.AbstractClientPlayer", remap = false)
public abstract class MixinAbstractClientPlayerSkin {
    @Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;",
            at = @At("RETURN"), remap = false, require = 1)
    private void skincopy$applyReturnedSkin(CallbackInfoReturnable<Object> callback) {
        if ((Object) this instanceof ClientPlayer local) {
            LiveSkinOverride.applyFromRender(local.getUniqueId(), callback.getReturnValue());
        }
    }
}
