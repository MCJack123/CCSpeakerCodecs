// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

public class DFPWMCodec extends Codec {
    @Override
    public byte[] encode(short[] data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public short[] decode(byte[] data, int numSamples) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int id() {
        return 0;
    }
}
