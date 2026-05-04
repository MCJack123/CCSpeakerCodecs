// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CCSpeakerCodecs {
    public static final String MOD_ID = "ccspeakercodecs";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    public static void init() {
        // Write common init code here.
        try {Class.forName("dan200.computercraft.shared.peripheral.speaker.DfpwmState");}
        catch (ClassNotFoundException ignore) {}
    }
}
