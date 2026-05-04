// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

public class DFPWMCodec extends Codec {
    @Override
    public byte[] encode(short[] data) {
        throw new UnsupportedOperationException("Attempted to encode with dummy DFPWM codec. This is a bug, and may be due to a codec with the wrong ID.");
    }

    @Override
    public short[] decode(byte[] data, int numSamples) {
        throw new UnsupportedOperationException("Attempted to decode with dummy DFPWM codec. This is a bug, and may be due to a codec with the wrong ID.");
    }

    @Override
    public int id() {
        return 0;
    }
}
