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

    // TODO: calibrate these for optimal performance
    static final int ADPCM_LOOKAHEAD = 3;
    static final int ADPCM_NOISE_SHAPING = ADPCMEncoder.NOISE_SHAPING_OFF;

    public ADPCMCodec(int bps, int sampleRate) {
        this.bps = bps;
        this.skipFactor = 48000 / sampleRate;
        this.encoder = new ADPCMEncoder(1, sampleRate, ADPCM_LOOKAHEAD, ADPCM_NOISE_SHAPING);
    }

    @Override
    public byte[] encode(short[] data) {
        short[] resampled = new short[data.length / this.skipFactor];
        for (int i = 0; i < resampled.length; i++) resampled[i] = data[i * this.skipFactor];
        byte[] retval = new byte[resampled.length * this.bps / 8 + 8];
        int sz = encoder.encode_block_ex(retval, resampled, this.bps);
        return Arrays.copyOf(retval, sz);
    }

    @Override
    public short[] decode(byte[] data) {
        short[] retval = new short[(data.length * 8 / this.bps + 1) * this.skipFactor];
        int sz = ADPCMDecoder.decode_block_ex(retval, data, 1, this.bps);
        for (int j = 0; j < this.skipFactor; j++) retval[(sz-1)*this.skipFactor+j] = retval[(sz-1)];
        for (int i = sz - 2; i >= 0; i--) {
            for (int j = 0; j < this.skipFactor; j++) retval[i*this.skipFactor+j] = (short) (retval[i] * (this.skipFactor - j) / this.skipFactor + retval[i+1] * j / this.skipFactor);
        }
        return Arrays.copyOf(retval, sz * this.skipFactor);
    }

    @Override
    public int id() {
        return this.bps;
    }
}
