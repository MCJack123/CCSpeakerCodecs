// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.forge;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(CCSpeakerCodecs.MOD_ID)
public final class CCSpeakerCodecsForge {
    public CCSpeakerCodecsForge(IEventBus context) {
        // Run our common setup.
        CCSpeakerCodecs.init();
        NeoForge.EVENT_BUS.register(this);
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
