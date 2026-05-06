// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class BufferedCodec extends Codec {
    private short[] buffer = null;
    private int bufferCount = 0;
    private int _lastEncodeLength = 0;

    public BufferedCodec(boolean interpolate) {
        super(interpolate);
    }

    /**
     * Returns the minimum number of resampled samples before encoding starts.
     * @return The minimum number of resampled samples before encoding starts
     */
    protected abstract int minBufferedSize();

    /**
     * Encodes already-resampled data. Subclasses implement this to do actual encoding.
     * @param data The input samples to encode (already resampled)
     * @param count The number of samples to encode, which may be lower than the buffer size
     * @param lastSample The last sample of the input block before resampling (used for interpolation)
     * @return The encoded bytes
     */
    protected abstract byte[] encodeBuffered(short[] data, int count, short lastSample);

    @Override
    public int lastEncodeLength() {
        return _lastEncodeLength;
    }

    @Override
    public byte[] encode(short[] data) {
        short[] resampled = resampleDown(data);

        if (this.buffer == null || this.buffer.length < bufferCount + resampled.length) {
            this.buffer = new short[Math.max(bufferCount + resampled.length, minBufferedSize()) * 2];
        }

        int oldBufferCount = bufferCount;
        System.arraycopy(resampled, 0, this.buffer, bufferCount, resampled.length);
        bufferCount += resampled.length;

        if (bufferCount < minBufferedSize()) {
            return new byte[0];
        }

        // Encode complete frames
        int totalFrames = bufferCount / minBufferedSize();
        int length = totalFrames * minBufferedSize();
        byte[] result = encodeBuffered(buffer, length, data[(length - oldBufferCount) * resampleFactor() - 1]);
        _lastEncodeLength = length;

        // Move remainder to front of buffer
        if (length > 0) {
            System.arraycopy(buffer, length, buffer, 0, bufferCount - length);
        }
        bufferCount -= length;

        return result;
    }

    /**
     * Flushes remaining buffered data as a partial frame.
     * @return Any bytes remaining to flush
     */
    public byte[] flush() {
        if (buffer == null || bufferCount == 0) {
            return new byte[0];
        }

        byte[] result = encodeBuffered(Arrays.copyOf(buffer, bufferCount), bufferCount, (short)0);
        this.buffer = null;
        this.bufferCount = 0;
        return result;
    }
}
