// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec.qoa;

import cc.craftospc.ccspeakercodecs.codec.BufferedCodec;
import cc.craftospc.ccspeakercodecs.codec.Codec;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class QOACodec extends BufferedCodec {
    private final int sampleRate;
    private final int skipFactor;

    private final int id;
    private final Encoder encoder;

    public static class Instances extends Codec.Instances {
        private final boolean enhancedRate;
        public Instances(boolean enhancedRate) {
            super(enhancedRate ? "qoa+" : "qoa");
            this.enhancedRate = enhancedRate;
        }

        @Override
        protected Codec create(int instance, LuaTable<String, ?> options) throws LuaException {
            return new QOACodec(instance | ((enhancedRate ? Codec.TYPE_QOA_PLUS : Codec.TYPE_QOA) << 4) | (options.optBoolean("interpolate").orElse(true) ? 0x100 : 0), enhancedRate);
        }

        @Override
        protected Codec create(int id) {
            return new QOACodec(id, enhancedRate);
        }
    }

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

        public byte[] finish(short lastSample) {
            byte[] retval = new byte[output.position()+1];
            output.flip();
            output.get(retval, 0, retval.length - 1);
            output.flip();
            retval[retval.length-1] = (byte) (lastSample >> 8);
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

    public QOACodec(int id, boolean enhancedRate) {
        super((id & 0x100) != 0);
        this.id = id;
        this.encoder = new Encoder();
        this.sampleRate = enhancedRate ? 16000 : 12000;
        this.skipFactor = 48000 / sampleRate;
    }

    @Override
    protected int minBufferedSize() {
        return 20;
    }

    @Override
    public int resampleFactor() {
        return skipFactor;
    }

    @Override
    protected byte[] encodeBuffered(short[] data, int count, short lastSample) {
        encoder.writeHeader(count, 1, sampleRate);
        for (int i = 0; i < count; i += 5120) {
            int chunkSize = Math.min(5120, count - i);
            encoder.writeFrame(Arrays.copyOfRange(data, i, i + chunkSize), chunkSize);
        }
        return encoder.finish(lastSample);
    }

    @Override
    public short[] decode(byte[] data, int numSamples) throws RuntimeException {
        Decoder decoder = new Decoder(data);
        if (!decoder.readHeader()) throw new IllegalStateException("Invalid header data");
        int totalSamples = decoder.getTotalSamples();
        short[] retval = new short[totalSamples * sampleRate];
        short[] tmp = new short[5120];
        for (int i = 0; i < totalSamples; i += 5120) {
            int framesz = decoder.readFrame(tmp);
            if (framesz < 0) break;
            System.arraycopy(tmp, 0, retval, i, framesz);
        }
        retval[totalSamples] = (short) (data[data.length - 1] << 8);
        return resampleUp(retval, totalSamples);
    }

    @Override
    public int id() {
        return id;
    }
}
