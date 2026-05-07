// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

import dan200.computercraft.api.lua.LuaException;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

public class OpusCodec extends BufferedCodec {
    private final int _id;
    private final OpusEncoder encoder;
    private final OpusDecoder decoder;
    private final int bufferSize;

    public static class Instances extends Codec.Instances {
        public Instances() {super("opus");}

        @Override
        protected Codec create(int instance, Optional<Map<?, ?>> options) throws LuaException {
            int bufferSize = getNumber(options, "bufferSize", 960);
            if (bufferSize >= 1920) bufferSize = 0x400;
            else if (bufferSize >= 960) bufferSize = 0x300;
            else if (bufferSize >= 480) bufferSize = 0x200;
            else if (bufferSize >= 240) bufferSize = 0x100;
            else bufferSize = 0x000;
            return new OpusCodec(instance | (Codec.TYPE_OPUS << 4) | bufferSize);
        }

        @Override
        protected Codec create(int id) {
            return new OpusCodec(id);
        }
    }

    public OpusCodec(int id) {
        super(false);
        this._id = id;
        this.bufferSize = switch (id & 0x700) {
            case 0x100 -> 240;
            case 0x200 -> 480;
            case 0x300 -> 960;
            case 0x400 -> 1920;
            default -> 120;
        };
        try {
            encoder = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(48000);
            decoder = new OpusDecoder(48000, 1);
        } catch (OpusException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected int minBufferedSize() {
        return bufferSize;
    }

    @Override
    protected byte[] encodeBuffered(short[] data, int count, short lastSample) {
        try {
            byte[] output = new byte[1920];
            ByteBuffer buf = ByteBuffer.allocate(count * 6);
            for (int i = 0; i < count;) {
                int frameSize = output.length;
                while (count - i < frameSize && frameSize > 120) frameSize /= 2;
                int len = encoder.encode(data, i, frameSize, output, 0, output.length);
                buf.putShort((short) frameSize);
                buf.putShort((short) len);
                buf.put(output, 0, len);
                i += frameSize;
            }
            byte[] retval = new byte[buf.position()];
            buf.flip();
            buf.get(retval);
            return retval;
        } catch (OpusException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public short[] decode(byte[] data, int numSamples) throws RuntimeException {
        try {
            short[] output = new short[numSamples];
            ByteBuffer buf = ByteBuffer.wrap(data);
            for (int i = 0; i < numSamples;) {
                short frameSize = buf.getShort();
                short len = buf.getShort();
                byte[] packet = new byte[len];
                buf.get(packet);
                i += decoder.decode(packet, 0, len, output, i, frameSize, false);
            }
            return output;
        } catch (OpusException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int id() {
        return _id;
    }
}
