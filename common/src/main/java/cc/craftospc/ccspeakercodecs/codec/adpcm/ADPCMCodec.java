// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec.adpcm;

import cc.craftospc.ccspeakercodecs.codec.Codec;
import dan200.computercraft.api.lua.LuaException;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public class ADPCMCodec extends Codec {
    private final int id;
    private final int bps;
    private final int skipFactor;
    private final ADPCMEncoder encoder;
    private int _lastEncodeLength = 0;

    public static class Instances extends Codec.Instances {
        private final int bps, sampleRate, lookahead, noiseShaping;

        public Instances(int bps, int sampleRate, int lookahead, int noiseShaping) {
            super("adpcm" + bps);
            this.bps = bps;
            this.sampleRate = sampleRate;
            this.lookahead = lookahead;
            this.noiseShaping = noiseShaping;
        }

        @Override
        protected Codec create(int instance, Optional<Map<?, ?>> options) throws LuaException {
            return new ADPCMCodec(instance | bps << 4 | (getBoolean(options, "interpolate", true) ? 0x100 : 0), bps, sampleRate, lookahead, noiseShaping);
        }

        @Override
        protected Codec create(int id) {
            return new ADPCMCodec(id, bps, sampleRate, lookahead, noiseShaping);
        }
    }

    public ADPCMCodec(int id, int bps, int sampleRate, int lookahead, int noiseShaping) {
        super((id & 0x100) != 0);
        this.id = id;
        this.bps = bps;
        this.skipFactor = 48000 / sampleRate;
        this.encoder = new ADPCMEncoder(1, sampleRate, lookahead, noiseShaping);
    }

    @Override
    public int resampleFactor() {
        return skipFactor;
    }

    public int lastEncodeLength() {
        return _lastEncodeLength;
    }

    @Override
    public byte[] encode(short[] data) {
        short[] resampled = resampleDown(data);
        byte[] retval = new byte[resampled.length * bps / 8 + 9];
        int sz = encoder.encode_block_ex(retval, resampled, bps);
        retval[sz] = (byte) (data[data.length - 1] >> 8);
        _lastEncodeLength = data.length;
        return Arrays.copyOf(retval, sz + 1);
    }

    @Override
    public short[] decode(byte[] data, int numSamples) {
        short[] retval = new short[numSamples];
        int sz = ADPCMDecoder.decode_block_ex(retval, data, 1, bps);
        sz = Math.min(sz, numSamples / skipFactor);
        retval[sz] = (short) (data[data.length - 1] << 8);
        return resampleUp(retval, sz);
    }

    @Override
    public int id() {
        return id;
    }
}
