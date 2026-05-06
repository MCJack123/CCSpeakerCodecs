// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.fabric;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class CCSpeakerCodecsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        CCSpeakerCodecs.init();
        ServerLifecycleEvents.SERVER_STARTING.register(CCSpeakerCodecs.CONFIG::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CCSpeakerCodecs.CONFIG.save());
    }
}
