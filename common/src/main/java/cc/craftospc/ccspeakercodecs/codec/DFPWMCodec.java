// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

import dan200.computercraft.api.lua.LuaException;

import java.util.Map;
import java.util.Optional;

public class DFPWMCodec extends Codec {
    public static class Instances extends Codec.Instances {
        public Instances() {super("dfpwm");}

        @Override
        protected Codec create(int instance, Optional<Map<?, ?>> options) throws LuaException {
            return INSTANCE;
        }

        @Override
        protected Codec create(int id) {
            return INSTANCE;
        }
    }

    public static final Codec INSTANCE = new DFPWMCodec();

    DFPWMCodec() {super(false);}

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

    public int lastEncodeLength() {
        return 0;
    }
}
