// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;
import cc.craftospc.ccspeakercodecs.codec.adpcm.ADPCMCodec;
import cc.craftospc.ccspeakercodecs.codec.adpcm.ADPCMEncoder;
import cc.craftospc.ccspeakercodecs.codec.qoa.QOACodec;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTable;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public abstract class Codec {
    public static abstract class Instances {
        private final Codec[] codecs = new Codec[MAX_INSTANCES];
        private int nextIndex = 0;
        private final String name;

        protected abstract Codec create(int instance, LuaTable<String, ?> options) throws LuaException;
        protected abstract Codec create(int id);

        protected Instances(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Codec createInstance(LuaTable<String, ?> options) throws LuaException {
            Codec c = create(nextIndex, options);
            codecs[nextIndex] = c;
            if (++nextIndex >= MAX_INSTANCES) nextIndex = 0;
            return c;
        }

        public Codec getInstance(int id, boolean forceNew) {
            int instance = instanceIndex(id);
            if (forceNew || codecs[instance] == null) codecs[instance] = create(id);
            return codecs[instance];
        }
    }

    public static final int TYPE_DFPWM = 0;
    public static final int TYPE_QOA = 1;
    public static final int TYPE_ADPCM_2 = 2;
    public static final int TYPE_ADPCM_3 = 3;
    public static final int TYPE_ADPCM_4 = 4;
    public static final int TYPE_ADPCM_5 = 5;
    public static final int TYPE_OPUS = 6;
    public static final int TYPE_QOA_PLUS = 7;

    private static final int MAX_INSTANCES = 16;

    private static final Instances[] codecs = new Instances[] {
        new DFPWMCodec.Instances(),
        new QOACodec.Instances(false),
        new ADPCMCodec.Instances(2, 24000, 5, ADPCMEncoder.NOISE_SHAPING_DYNAMIC),
        new ADPCMCodec.Instances(3, 16000, 5, ADPCMEncoder.NOISE_SHAPING_DYNAMIC),
        new ADPCMCodec.Instances(4, 12000, 4, ADPCMEncoder.NOISE_SHAPING_DYNAMIC),
        new ADPCMCodec.Instances(5, 9600, 4, ADPCMEncoder.NOISE_SHAPING_DYNAMIC),
        new OpusCodec.Instances(),
        new QOACodec.Instances(true),
    };

    private final boolean interpolate;
    private boolean targetChanged = true;

    protected Codec(boolean interpolate) {
        this.interpolate = interpolate;
    }

    public static @Nullable Codec byName(String name, LuaTable<String, ?> options) throws LuaException {
        if (name.equalsIgnoreCase("adpcm")) name = "adpcm4";
        if (!CCSpeakerCodecs.CONFIG.allowedCodecs.contains(name.toLowerCase())) return null;
        Instances instances = null;
        for (int i = 0; i < MAX_INSTANCES; i++) {
            if (Codec.codecs[i].getName().equalsIgnoreCase(name)) {
                instances = Codec.codecs[i];
                break;
            }
        }
        if (instances == null) return null;
        return instances.createInstance(options);
    }

    public static Codec byID(int id, boolean forceNew) {
        int type = typeId(id);
        if (type >= codecs.length) return null;
        return codecs[type].getInstance(id, forceNew);
    }

    public static int typeId(int id) {
        return (id >>> 4) & 0xF;
    }

    public static int instanceIndex(int id) {
        return id & 0xF;
    }

    /**
     * Checks whether the codec was just created.
     * This will set the flag to false for the next read.
     * @return Whether this codec was just created (only returns true once)
     */
    public boolean readTargetChanged() {
        boolean changed = this.targetChanged;
        this.targetChanged = false;
        return changed;
    }

    /**
     * Encodes 48 kHz audio to the target codec.
     * @param data The audio to encode
     * @return The encoded bytes
     */
    public abstract byte[] encode(short[] data);

    /**
     * Returns the length of the audio that was last encoded. This may be different
     * from the last input length in the case of a buffered codec.
     * @return The length of the last encoded audio
     */
    public abstract int lastEncodeLength();

    /**
     * Decodes codec data to 48 kHz audio.
     * @param data The bytes to decode
     * @return The decoded samples
     */
    public abstract short[] decode(byte[] data, int numSamples) throws RuntimeException;

    /**
     * Returns the ID of the codec.
     * @return The ID of the codec
     */
    public abstract int id();

    /**
     * Returns the sample rate resampling factor (48000 / target rate).
     * Default is 1 (no resampling).
     * @return The resample factor for the codec
     */
    protected int resampleFactor() {
        return 1;
    }

    /**
     * Resamples input audio down to codec sample rate.
     * @param input The audio to resample
     * @return The newly resampled audio
     */
    protected short[] resampleDown(short[] input) {
        int factor = resampleFactor();
        if (factor == 1) return input;

        short[] output = new short[input.length / factor];
        for (int i = 0; i < output.length; i++) {
            output[i] = input[i * factor];
        }
        return output;
    }

    /**
     * Resamples a downsampled audio chunk back to native.
     * @param retval The audio to resample, which must have length of at least `totalSamples * resampleFactor()`
     * @param totalSamples The total number of samples before resampling
     * @return The newly resampled audio chunk
     */
    protected short[] resampleUp(short[] retval, int totalSamples) {
        int resampleFactor = resampleFactor();
        for (int i = totalSamples - 1; i >= 0; i--) {
            for (int j = 0; j < resampleFactor; j++) {
                if (interpolate) retval[i * resampleFactor + j] = (short) (retval[i] * (resampleFactor - j) / resampleFactor + retval[i + 1] * j / resampleFactor);
                else retval[i * resampleFactor + j] = retval[i];
            }
        }
        return Arrays.copyOf(retval, totalSamples * resampleFactor);
    }
}
