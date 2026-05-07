// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.forge;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CCSpeakerCodecs.MOD_ID)
public final class CCSpeakerCodecsForge {
    public CCSpeakerCodecsForge(FMLJavaModLoadingContext context) {
        // Run our common setup.
        CCSpeakerCodecs.init();
        context.getModEventBus().addListener(this::onServerStarting);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CCSpeakerCodecs.CONFIG.load(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CCSpeakerCodecs.CONFIG.save();
    }
}
