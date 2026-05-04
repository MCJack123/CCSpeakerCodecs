package cc.craftospc.ccspeakercodecs.codec;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;

import java.nio.ByteBuffer;

public class OpusCodec extends Codec {
    OpusEncoder encoder;
    OpusDecoder decoder;
    int _id;

    public OpusCodec(int id) {
        _id = id;
        try {
            encoder = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(48000);
            decoder = new OpusDecoder(48000, 1);
        } catch (OpusException ignored) {}
    }

    @Override
    public byte[] encode(short[] data) {
        try {
            byte[] output = new byte[1920];
            ByteBuffer buf = ByteBuffer.allocate(data.length + data.length * 4 / output.length + 4);
            for (int i = 0; i < data.length;) {
                int frameSize = output.length;
                while (data.length - i < frameSize && frameSize > 120) frameSize /= 2;
                int len;
                if (data.length - i < frameSize) {
                    // pad tiny frame
                    short[] frame = new short[frameSize];
                    System.arraycopy(data, i, frame, 0, data.length - i);
                    len = encoder.encode(frame, 0, frameSize, output, 0, output.length);
                } else len = encoder.encode(data, i, frameSize, output, 0, output.length);
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
                if (i + frameSize - 1 >= numSamples) {
                    short[] frame = new short[frameSize];
                    decoder.decode(packet, 0, len, frame, 0, frameSize, false);
                    System.arraycopy(frame, 0, output, i, numSamples - i);
                    i = numSamples;
                } else i += decoder.decode(packet, 0, len, output, i, frameSize, false);
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
