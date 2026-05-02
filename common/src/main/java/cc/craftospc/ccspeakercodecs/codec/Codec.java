// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.codec;

import cc.craftospc.ccspeakercodecs.codec.adpcm.ADPCMCodec;
import cc.craftospc.ccspeakercodecs.codec.qoa.QOACodec;
import org.jspecify.annotations.Nullable;

public abstract class Codec {
    public static final int DFPWM_ID = 0;
    public static final Codec DFPWM;
    public static final int QOA_ID = 1;
    public static final Codec QOA;
    public static final int ADPCM_2_ID = 2;
    public static final Codec ADPCM_2;
    public static final int ADPCM_3_ID = 3;
    public static final Codec ADPCM_3;
    public static final int ADPCM_4_ID = 4;
    public static final Codec ADPCM_4;
    public static final int ADPCM_5_ID = 5;
    public static final Codec ADPCM_5;

    static {
        DFPWM = CodecHolder.DFPWM;
        QOA = CodecHolder.QOA;
        ADPCM_2 = CodecHolder.ADPCM_2;
        ADPCM_3 = CodecHolder.ADPCM_3;
        ADPCM_4 = CodecHolder.ADPCM_4;
        ADPCM_5 = CodecHolder.ADPCM_5;
    }

    private static class CodecHolder {
        static final Codec DFPWM = new DFPWMCodec();
        static final Codec QOA = new QOACodec();
        static final Codec ADPCM_2 = new ADPCMCodec(2, 24000);
        static final Codec ADPCM_3 = new ADPCMCodec(3, 16000);
        static final Codec ADPCM_4 = new ADPCMCodec(4, 12000);
        static final Codec ADPCM_5 = new ADPCMCodec(5, 9600);
    }

    public static @Nullable Codec byName(String name) {
        if (name.equalsIgnoreCase("dfpwm")) return DFPWM;
        if (name.equalsIgnoreCase("qoa")) return QOA;
        if (name.equalsIgnoreCase("adpcm2")) return ADPCM_2;
        if (name.equalsIgnoreCase("adpcm3")) return ADPCM_3;
        if (name.equalsIgnoreCase("adpcm4") || name.equalsIgnoreCase("adpcm")) return ADPCM_4;
        if (name.equalsIgnoreCase("adpcm5")) return ADPCM_5;
        return null;
    }

    public static @Nullable Codec byID(int id) {
        return switch (id) {
            case DFPWM_ID -> DFPWM;
            case QOA_ID -> QOA;
            case ADPCM_2_ID -> ADPCM_2;
            case ADPCM_3_ID -> ADPCM_3;
            case ADPCM_4_ID -> ADPCM_4;
            case ADPCM_5_ID -> ADPCM_5;
            default -> null;
        };
    }

    /**
     * Encodes 48 kHz audio to the target codec.
     * @param data The audio to encode
     * @return The encoded bytes
     */
    public abstract byte[] encode(short[] data);

    /**
     * Decodes codec data to 48 kHz audio.
     * @param data The bytes to decode
     * @return The decoded samples
     */
    public abstract short[] decode(byte[] data);

    /**
     * Returns the ID of the codec.
     * @return The ID of the codec
     */
    public abstract int id();
}
