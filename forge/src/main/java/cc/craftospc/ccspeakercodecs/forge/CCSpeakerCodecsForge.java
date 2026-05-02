// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.forge;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CCSpeakerCodecs.MOD_ID)
public final class CCSpeakerCodecsForge {
    public CCSpeakerCodecsForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(CCSpeakerCodecs.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        CCSpeakerCodecs.init();
    }
}
