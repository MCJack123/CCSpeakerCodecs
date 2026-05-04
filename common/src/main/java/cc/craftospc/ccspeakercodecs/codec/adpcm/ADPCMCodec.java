// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec.adpcm;

import cc.craftospc.ccspeakercodecs.codec.Codec;

import java.util.Arrays;

public class ADPCMCodec extends Codec {
    int bps;
    int skipFactor;
    ADPCMEncoder encoder;

    public ADPCMCodec(int bps, int sampleRate, int lookahead, int noiseShaping) {
        this.bps = bps;
        this.skipFactor = 48000 / sampleRate;
        this.encoder = new ADPCMEncoder(1, sampleRate, lookahead, noiseShaping);
    }

    @Override
    public byte[] encode(short[] data) {
        short[] resampled = new short[data.length / this.skipFactor];
        for (int i = 0; i < resampled.length; i++) resampled[i] = data[i * this.skipFactor];
        byte[] retval = new byte[resampled.length * this.bps / 8 + 9];
        int sz = encoder.encode_block_ex(retval, resampled, this.bps);
        retval[sz] = (byte) (data[data.length - 1] >> 8);
        return Arrays.copyOf(retval, sz + 1);
    }

    @Override
    public short[] decode(byte[] data, int numSamples) {
        short[] retval = new short[numSamples];
        int sz = ADPCMDecoder.decode_block_ex(retval, data, 1, this.bps);
        sz = Math.min(sz, numSamples / this.skipFactor);
        retval[sz] = (short) (data[data.length - 1] << 8);
        for (int i = sz - 1; i >= 0; i--) {
            for (int j = 0; j < this.skipFactor; j++) retval[i*this.skipFactor+j] = (short) (retval[i] * (this.skipFactor - j) / this.skipFactor + retval[i+1] * j / this.skipFactor);
        }
        return Arrays.copyOf(retval, sz * this.skipFactor);
    }

    @Override
    public int id() {
        return this.bps;
    }
}
