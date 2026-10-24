package de.skincopy;

import java.io.InputStream;
import java.util.UUID;
import net.labymod.api.client.network.PlayerSkin;
import net.labymod.api.client.resources.CompletableResourceLocation;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.session.MinecraftServices.SkinVariant;
import net.labymod.api.metadata.Metadata;

/** Checks player isolation, replacement skin instances, cape preservation and clearing. */
public class LiveSkinOverrideTest {
    static final class Location implements ResourceLocation {
        final String path;
        Location(String path) {this.path=path;}
        public String getNamespace(){return "test";}
        public String getPath(){return path;}
        public <T> T getMinecraftLocation(){return null;}
        public InputStream openStream(){throw new UnsupportedOperationException();}
        public boolean exists(){return true;}
        public void metadata(Metadata value){}
        public Metadata metadata(){return null;}
    }
    static final class Skin implements PlayerSkin {
        final CompletableResourceLocation texture = new CompletableResourceLocation(new Location("old"));
        final CompletableResourceLocation cape = new CompletableResourceLocation(new Location("own-cape"));
        final CompletableResourceLocation elytra = new CompletableResourceLocation(new Location("own-elytra"));
        SkinVariant variant = SkinVariant.CLASSIC;
        public void setSkinTexture(ResourceLocation location){texture.updateCompletable(location);}
        public void setCapeTexture(ResourceLocation location){throw new AssertionError("Cape changed");}
        public void setElytraTexture(ResourceLocation location){throw new AssertionError("Elytra changed");}
        public void setSkinVariant(SkinVariant value){variant=value;}
        public CompletableResourceLocation getCompletableSkinTexture(){return texture;}
        public CompletableResourceLocation getCompletableCapeTexture(){return cape;}
        public CompletableResourceLocation getCompletableElytraTexture(){return elytra;}
        public SkinVariant getSkinVariant(){return variant;}
    }
    public static void main(String[] args) {
        UUID own = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ResourceLocation wanted = new Location("new-skin");
        LiveSkinOverride.clear();
        LiveSkinOverride.activate(own,wanted,SkinVariant.SLIM);
        Skin remote = new Skin();
        ResourceLocation original = remote.texture.getCompleted();
        LiveSkinOverride.applyFromRender(other,remote);
        if(remote.texture.getCompleted()!=original || LiveSkinOverride.hasAppliedFromRender())throw new AssertionError("Other player modified");
        LiveSkinOverride.applyFromRender(own,new Object());
        if(LiveSkinOverride.hasAppliedFromRender())throw new AssertionError("Invalid skin marked applied");
        Skin local = new Skin();
        LiveSkinOverride.applyFromRender(own,local);
        if(local.texture.getCompleted()!=wanted || local.variant!=SkinVariant.SLIM || !LiveSkinOverride.hasAppliedFromRender())throw new AssertionError("Local skin was not applied");
        Skin replaced = new Skin();
        LiveSkinOverride.applyFromRender(own,replaced);
        if(replaced.texture.getCompleted()!=wanted)throw new AssertionError("New cache instance was not handled");
        LiveSkinOverride.clear();
        Skin disabled = new Skin();
        ResourceLocation disabledOriginal=disabled.texture.getCompleted();
        LiveSkinOverride.applyFromRender(own,disabled);
        if(disabled.texture.getCompleted()!=disabledOriginal || LiveSkinOverride.hasAppliedFromRender())throw new AssertionError("Override survived clearing");
        System.out.println("PASS: own player only, invalid return ignored, live texture and arms, replacement instance, own cape/elytra preserved, clearing.");
    }
}
