// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.shared.peripheral.speaker;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;

public class DfpwmState {
    static {
        CCSpeakerCodecs.LOG.severe("This should never be loaded! Audio will be broken.");
    }
}
