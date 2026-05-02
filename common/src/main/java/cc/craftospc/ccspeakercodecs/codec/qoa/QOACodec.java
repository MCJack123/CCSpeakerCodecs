// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec.qoa;

import cc.craftospc.ccspeakercodecs.codec.Codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class QOACodec extends Codec {
    static final int SAMPLE_RATE = 12000;
    static final int SKIP_FACTOR = 48000 / SAMPLE_RATE;

    private static class Encoder extends QOAEncoder {
        ByteBuffer output = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN);

        @Override
        protected boolean writeLong(long l) {
            if (output.remaining() < 8) {
                ByteBuffer newbuf = ByteBuffer.allocate(output.capacity() + 1024).order(ByteOrder.BIG_ENDIAN);
                byte[] bytes = new byte[output.position()];
                output.flip();
                output.get(bytes);
                newbuf.put(bytes);
                output = newbuf;
            }
            output.putLong(l);
            return true;
        }

        public byte[] finish() {
            byte[] retval = new byte[output.position()];
            output.flip();
            output.get(retval);
            return retval;
        }
    }

    private static class Decoder extends QOADecoder {
        ByteBuffer input;

        Decoder(byte[] input) {
            this.input = ByteBuffer.wrap(input);
        }

        @Override
        protected int readByte() {
            if (!input.hasRemaining()) return -1;
            int b = input.get();
            if (b < 0) return b + 256;
            return b;
        }

        @Override
        protected void seekToByte(int position) {
            input.position(position);
        }
    }

    @Override
    public byte[] encode(short[] data) {
        short[] resampled = new short[data.length / SKIP_FACTOR];
        for (int i = 0; i < resampled.length; i++) resampled[i] = data[i * SKIP_FACTOR];
        Encoder encoder = new Encoder();
        encoder.writeHeader(resampled.length, 1, SAMPLE_RATE);
        for (int i = 0; i < resampled.length; i += 5120) encoder.writeFrame(Arrays.copyOfRange(resampled, i, Math.min(i + 5120, resampled.length)), Math.min(resampled.length - i, 5120));
        return encoder.finish();
    }

    @Override
    public short[] decode(byte[] data) {
        Decoder decoder = new Decoder(data);
        if (!decoder.readHeader()) throw new AssertionError("Invalid header data");
        int sz = decoder.getTotalSamples();
        short[] retval = new short[sz * SKIP_FACTOR];
        short[] tmp = new short[5120];
        for (int i = 0; i < sz; i += 5120) {
            int framesz = decoder.readFrame(tmp);
            if (framesz < 0) break;
            System.arraycopy(tmp, 0, retval, i, framesz);
        }
        for (int j = 0; j < SKIP_FACTOR; j++) retval[(sz-1)*SKIP_FACTOR+j] = retval[(sz-1)];
        for (int i = sz - 2; i >= 0; i--) {
            for (int j = 0; j < SKIP_FACTOR; j++) retval[i*SKIP_FACTOR+j] = (short) (retval[i] * (SKIP_FACTOR - j) / SKIP_FACTOR + retval[i+1] * j / SKIP_FACTOR);
        }
        return Arrays.copyOf(retval, sz * SKIP_FACTOR);
    }

    @Override
    public int id() {
        return Codec.QOA_ID;
    }
}
