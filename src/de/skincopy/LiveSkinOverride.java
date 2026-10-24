package de.skincopy;

import java.util.Objects;
import java.util.UUID;
import net.labymod.api.client.network.PlayerSkin;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.session.MinecraftServices.SkinVariant;

/** Local render override. This class never changes network packets or other players. */
public final class LiveSkinOverride {
    private static volatile Desired desired;
    private static volatile boolean appliedFromRender;
    private LiveSkinOverride() {}

    public static void activate(UUID accountId, ResourceLocation texture, SkinVariant variant) {
        desired = new Desired(Objects.requireNonNull(accountId), Objects.requireNonNull(texture),
                Objects.requireNonNull(variant));
        appliedFromRender = false;
    }

    public static void clear() {
        desired = null;
        appliedFromRender = false;
    }

    public static boolean hasAppliedFromRender() {
        return appliedFromRender;
    }

    /** Called at RETURN of the local player's real Minecraft getSkin() method. */
    public static void applyFromRender(UUID playerId, Object returnedSkin) {
        Desired value = desired;
        if (value == null || !value.accountId().equals(playerId) || !(returnedSkin instanceof PlayerSkin skin)) {
            return;
        }
        if (!Objects.equals(skin.getCompletableSkinTexture().getCompleted(), value.texture())) {
            skin.setSkinTexture(value.texture());
        }
        if (skin.getSkinVariant() != value.variant()) {
            skin.setSkinVariant(value.variant());
        }
        appliedFromRender = true;
    }

    private record Desired(UUID accountId, ResourceLocation texture, SkinVariant variant) {}
}
