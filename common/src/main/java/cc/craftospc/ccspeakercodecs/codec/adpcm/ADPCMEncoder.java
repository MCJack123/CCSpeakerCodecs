// SPDX-FileCopyrightText: 2019 David Bryant, 2026 JackMacWindows
//
// SPDX-License-Identifier: BSD

package cc.craftospc.ccspeakercodecs.codec.adpcm;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/*
Code adapted from ADPCM-XQ (https://github.com/dbry/adpcm-xq) by David Bryant.
Licensed under the BSD license.

                        Copyright (c) David Bryant
                          All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Conifer Software nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

public class ADPCMEncoder {
    private static class adpcm_channel {
        int pcmdata;
        int shaping_weight, error;
        byte index;

        public adpcm_channel copy() {
            adpcm_channel copy = new adpcm_channel();
            copy.pcmdata = pcmdata;
            copy.shaping_weight = shaping_weight;
            copy.error = error;
            copy.index = index;
            return copy;
        }

        public int noise_shape(int sample) {
            int temp = -((this.shaping_weight * this.error + 512) >> 10);

            if (this.shaping_weight < 0 && temp != 0) {
                if (temp == this.error)
                    temp = (temp < 0) ? temp + 1 : temp - 1;

                this.error = -sample;
                sample += temp;
            }
            else
                this.error = -(sample += temp);

            return sample;
        }

        public long min_error_4bit (int nch, int csample, short[] psample, int psample_offset, int flags, int @Nullable [] best_nibble, long max_error)
        {
            int delta = csample - this.pcmdata, csample2;
            adpcm_channel chan = this.copy();
            short step = step_table[chan.index];
            int trial_delta = (step >>> 3);
            int nibble, testnbl;
            long min_error;

            // this odd-looking code always generates the nibble value with the least error,
            // regardless of step size (which was not true previously)

            if (delta < 0) {
                int mag = ((-delta << 2) + (step & 3) + ((step & 1) << 1)) / step;
                nibble = 0x8 | (mag > 7 ? 7 : mag);
            }
            else {
                int mag = ((delta << 2) + (step & 3) + ((step & 1) << 1)) / step;
                nibble = mag > 7 ? 7 : mag;
            }

            if ((nibble & 1) != 0) trial_delta += (step >>> 2);
            if ((nibble & 2) != 0) trial_delta += (step >>> 1);
            if ((nibble & 4) != 0) trial_delta += step;

            if ((nibble & 8) != 0)
                chan.pcmdata -= trial_delta;
            else
                chan.pcmdata += trial_delta;

            chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);
            if (best_nibble != null) best_nibble[0] = nibble;
            min_error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);

            // if we're at a leaf, or we're not at a leaf but have already exceeded the error limit, return
            if ((flags & LOOKAHEAD_DEPTH) == 0 || min_error >= max_error)
                return min_error;

            // otherwise we execute that naively closest nibble and search deeper for improvement

            chan.index += index_table[nibble & 0x07];
            chan.index = clamp(chan.index, 0, 88);

            if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                chan.error += chan.pcmdata;
                csample2 = chan.noise_shape(psample [psample_offset + nch]);
            }
            else
                csample2 = psample [psample_offset + nch];

            min_error += chan.min_error_4bit(nch, csample2, psample, psample_offset + nch, flags - 1, null, max_error - min_error);

            // min_error is the error (from here to the leaf) for the naively closest nibble.
            // Unless we've been told not to try, we may be able to improve on that by choosing
            // an alternative (not closest) nibble.

            if ((flags & LOOKAHEAD_NO_BRANCHING) != 0)
                return min_error;

            for (testnbl = 0; testnbl <= 0xF; ++testnbl) {
                long error, threshold;

                if (testnbl == nibble)  // don't do the same value again
                    continue;

                // we execute this branch if:
                // 1. we're doing an exhaustive search, or
                // 2. the test value is one of the maximum values (i.e., 0x7 or 0xf), or
                // 3. the test value's delta is within three of the initial estimate's delta

                if ((flags & LOOKAHEAD_EXHAUSTIVE) != 0 || (~testnbl & 0x7) == 0 || Math.abs(NIBBLE_TO_DELTA (4,nibble) - NIBBLE_TO_DELTA (4,testnbl)) <= 3) {
                    trial_delta = (short)(step >>> 3);
                    chan = this.copy();

                    if ((testnbl & 1) != 0) trial_delta += (short) (step >>> 2);
                    if ((testnbl & 2) != 0) trial_delta += (short) (step >>> 1);
                    if ((testnbl & 4) != 0) trial_delta += step;

                    if ((testnbl & 8) != 0)
                        chan.pcmdata -= trial_delta;
                    else
                        chan.pcmdata += trial_delta;

                    chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);

                    error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);
                    threshold = max_error < min_error ? max_error : min_error;

                    if (error < threshold) {
                        chan.index += index_table[testnbl & 0x07];
                        chan.index = clamp(chan.index, 0, 88);

                        if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                            chan.error += chan.pcmdata;
                            csample2 = chan.noise_shape (psample [psample_offset + nch]);
                        }
                        else
                            csample2 = psample [psample_offset + nch];

                        error += chan.min_error_4bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, threshold - error);

                        if (error < min_error) {
                            if (best_nibble != null) best_nibble[0] = testnbl;
                            min_error = error;
                        }
                    }
                }
            }

            return min_error;
        }

        public long min_error_2bit (int nch, int csample, short[] psample, int psample_offset, int flags, int @Nullable [] best_nibble, long max_error) {
            int delta = csample - this.pcmdata, csample2;
            adpcm_channel chan = this.copy();
            short step = step_table[chan.index];
            int nibble, testnbl;
            long min_error;

            if (delta < 0) {
                if (-delta >= step) {
                    chan.pcmdata -= step + (step >>> 1);
                    nibble = 3;
                }
                else {
                    chan.pcmdata -= step >>> 1;
                    nibble = 2;
                }
            }
            else
                chan.pcmdata += step * ((nibble = delta >= step ? 1 : 0)) + (step >>> 1);

            chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);
            if (best_nibble != null) best_nibble[0] = nibble;
            min_error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);

            // if we're at a leaf, or we're not at a leaf but have already exceeded the error limit, return
            if ((flags & LOOKAHEAD_DEPTH) == 0 || min_error >= max_error)
                return min_error;

            // otherwise we execute that naively closest nibble and search deeper for improvement

            chan.index += (nibble & 1) * 3 - 1;
            chan.index = clamp(chan.index, 0, 88);

            if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                chan.error += chan.pcmdata;
                csample2 = chan.noise_shape (psample [psample_offset + nch]);
            }
            else
                csample2 = psample [psample_offset + nch];

            min_error += chan.min_error_2bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, max_error - min_error);

            // min_error is the error (from here to the leaf) for the naively closest nibble.
            // Unless we've been told not to try, we may be able to improve on that by choosing
            // an alternative (not closest) nibble.

            if ((flags & LOOKAHEAD_NO_BRANCHING) != 0)
                return min_error;

            for (testnbl = 0; testnbl <= 0x3; ++testnbl) {
                long error, threshold;

                if (testnbl == nibble)  // don't do the same value again
                    continue;

                chan = this.copy();

                if ((testnbl & 2) != 0)
                    chan.pcmdata -= step * (testnbl & 1) + (step >>> 1);
                else
                    chan.pcmdata += step * (testnbl & 1) + (step >>> 1);

                chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);

                error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);
                threshold = max_error < min_error ? max_error : min_error;

                if (error < threshold) {
                    chan.index += (testnbl & 1) * 3 - 1;
                    chan.index = clamp(chan.index, 0, 88);

                    if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                        chan.error += chan.pcmdata;
                        csample2 = chan.noise_shape (psample [psample_offset + nch]);
                    }
                    else
                        csample2 = psample [psample_offset + nch];

                    error += chan.min_error_2bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, threshold - error);

                    if (error < min_error) {
                        if (best_nibble != null) best_nibble[0] = testnbl;
                        min_error = error;
                    }
                }
            }

            return min_error;
        }

        public long min_error_3bit (int nch, int csample, short[] psample, int psample_offset, int flags, int @Nullable [] best_nibble, long max_error)
        {
            int delta = csample - this.pcmdata, csample2;
            adpcm_channel chan = this.copy();
            short step = step_table[chan.index];
            int trial_delta = (short)(step >>> 2);
            int nibble, testnbl;
            long min_error;

            if (delta < 0) {
                int mag = ((-delta << 1) + (step & 1)) / step;
                nibble = 0x4 | (mag > 3 ? 3 : mag);
            }
            else {
                int mag = ((delta << 1) + (step & 1)) / step;
                nibble = mag > 3 ? 3 : mag;
            }

            if ((nibble & 1) != 0) trial_delta += (step >>> 1);
            if ((nibble & 2) != 0) trial_delta += step;

            if ((nibble & 4) != 0)
                chan.pcmdata -= trial_delta;
            else
                chan.pcmdata += trial_delta;

            chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);
            if (best_nibble != null) best_nibble[0] = nibble;
            min_error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);

            // if we're at a leaf, or we're not at a leaf but have already exceeded the error limit, return
            if ((flags & LOOKAHEAD_DEPTH) == 0 || min_error >= max_error)
                return min_error;

            // otherwise we execute that naively closest nibble and search deeper for improvement

            chan.index += index_table_3bit[nibble & 0x03];
            chan.index = clamp(chan.index, 0, 88);

            if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                chan.error += chan.pcmdata;
                csample2 = chan.noise_shape (psample [psample_offset + nch]);
            }
            else
                csample2 = psample [psample_offset + nch];

            min_error += chan.min_error_3bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, max_error - min_error);

            // min_error is the error (from here to the leaf) for the naively closest nibble.
            // Unless we've been told not to try, we may be able to improve on that by choosing
            // an alternative (not closest) nibble.

            if ((flags & LOOKAHEAD_NO_BRANCHING) != 0)
                return min_error;

            for (testnbl = 0; testnbl <= 0x7; ++testnbl) {
                long error, threshold;

                if (testnbl == nibble)  // don't do the same value again
                    continue;

                // we execute this branch if:
                // 1. we're doing an exhaustive search, or
                // 2. the test value is one of the maximum values (i.e., 0x3 or 0x7), or
                // 3. the test value's delta is within two of the initial estimate's delta

                if ((flags & LOOKAHEAD_EXHAUSTIVE) != 0 || (~testnbl & 0x3) == 0 || Math.abs (NIBBLE_TO_DELTA (3,nibble) - NIBBLE_TO_DELTA (3,testnbl)) <= 2) {
                    trial_delta = (short)(step >>> 2);
                    chan = this.copy();

                    if ((testnbl & 1) != 0) trial_delta += (step >>> 1);
                    if ((testnbl & 2) != 0) trial_delta += step;

                    if ((testnbl & 4) != 0)
                        chan.pcmdata -= trial_delta;
                    else
                        chan.pcmdata += trial_delta;

                    chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);
                    error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);
                    threshold = max_error < min_error ? max_error : min_error;

                    if (error < threshold) {
                        chan.index += index_table_3bit[testnbl & 0x03];
                        chan.index = clamp(chan.index, 0, 88);

                        if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                            chan.error += chan.pcmdata;
                            csample2 = chan.noise_shape (psample [psample_offset + nch]);
                        }
                        else
                            csample2 = psample [psample_offset + nch];

                        error += chan.min_error_3bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, threshold - error);

                        if (error < min_error) {
                            if (best_nibble != null) best_nibble[0] = testnbl;
                            min_error = error;
                        }
                    }
                }
            }

            return min_error;
        }

        private static final byte[] comp_table = { 0, 0, 0, 5, 0, 6, 4, 10, 0, 7, 6, 10, 4, 11, 11, 13 };
        public long min_error_5bit (int nch, int csample, short[] psample, int psample_offset, int flags, int @Nullable [] best_nibble, long max_error)
        {
            int delta = csample - this.pcmdata, csample2;
            adpcm_channel chan = this.copy();
            short step = step_table[chan.index];
            int trial_delta = (short)(step >>> 4);
            int nibble, testnbl;
            long min_error;

            if (delta < 0) {
                int mag = ((-delta << 3) + comp_table [step & 0xf]) / step;
                nibble = 0x10 | (mag > 0xf ? 0xf : mag);
            }
            else {
                int mag = ((delta << 3) + comp_table [step & 0xf]) / step;
                nibble = mag > 0xf ? 0xf : mag;
            }

            if ((nibble & 1) != 0) trial_delta += (step >>> 3);
            if ((nibble & 2) != 0) trial_delta += (step >>> 2);
            if ((nibble & 4) != 0) trial_delta += (step >>> 1);
            if ((nibble & 8) != 0) trial_delta += step;

            if ((nibble & 0x10) != 0)
                chan.pcmdata -= trial_delta;
            else
                chan.pcmdata += trial_delta;

            chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);
            if (best_nibble != null) best_nibble[0] = nibble;
            min_error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);

            // if we're at a leaf, or we're not at a leaf but have already exceeded the error limit, return
            if ((flags & LOOKAHEAD_DEPTH) == 0 || min_error >= max_error)
                return min_error;

            // otherwise we execute that naively closest nibble and search deeper for improvement

            chan.index += index_table_5bit[nibble & 0x0f];
            chan.index = clamp(chan.index, 0, 88);

            if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                chan.error += chan.pcmdata;
                csample2 = chan.noise_shape (psample [psample_offset + nch]);
            }
            else
                csample2 = psample [psample_offset + nch];

            min_error += chan.min_error_5bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, max_error - min_error);

            // min_error is the error (from here to the leaf) for the naively closest nibble.
            // Unless we've been told not to try, we may be able to improve on that by choosing
            // an alternative (not closest) nibble.

            if ((flags & LOOKAHEAD_NO_BRANCHING) != 0)
                return min_error;

            for (testnbl = 0; testnbl <= 0x1F; ++testnbl) {
                long error, threshold;

                if (testnbl == nibble)  // don't do the same value again
                    continue;

                // we execute this trial if:
                // 1. we're doing an exhaustive search, or
                // 2. the trial value is one of the four maximum values for the sign, or
                // 3. the test value's delta is within three of the initial estimate's delta

                if ((flags & LOOKAHEAD_EXHAUSTIVE) != 0 || (testnbl | 3) == (nibble | 0xf) || Math.abs (NIBBLE_TO_DELTA (5,nibble) - NIBBLE_TO_DELTA (5,testnbl)) <= 3) {
                    trial_delta = (short)(step >>> 4);
                    chan = this.copy();

                    if ((testnbl & 1) != 0) trial_delta += (step >>> 3);
                    if ((testnbl & 2) != 0) trial_delta += (step >>> 2);
                    if ((testnbl & 4) != 0) trial_delta += (step >>> 1);
                    if ((testnbl & 8) != 0) trial_delta += step;

                    if ((testnbl & 0x10) != 0)
                        chan.pcmdata -= trial_delta;
                    else
                        chan.pcmdata += trial_delta;

                    chan.pcmdata = clamp(chan.pcmdata, -32768, 32767);

                    error = (long) (chan.pcmdata - csample) * (chan.pcmdata - csample);
                    threshold = max_error < min_error ? max_error : min_error;

                    if (error < threshold) {
                        chan.index += index_table_5bit [testnbl & 0x0f];
                        chan.index = clamp(chan.index, 0, 88);

                        if ((flags & NOISE_SHAPING_ENABLED) != 0) {
                            chan.error += chan.pcmdata;
                            csample2 = chan.noise_shape (psample [psample_offset + nch]);
                        }
                        else
                            csample2 = psample [psample_offset + nch];

                        error += chan.min_error_5bit (nch, csample2, psample, psample_offset + nch, flags - 1, null, threshold - error);

                        if (error < min_error) {
                            if (best_nibble != null) best_nibble[0] = testnbl;
                            min_error = error;
                        }
                    }
                }
            }

            return min_error;
        }
    }

    public static final int NOISE_SHAPING_OFF       = 0;       // flat noise (no shaping)
    public static final int NOISE_SHAPING_STATIC    = 0x100;   // static 1st-order shaping (configurable, highpass default)
    public static final int NOISE_SHAPING_DYNAMIC   = 0x200;   // dynamically tilted noise based on signal

    public static final int LOOKAHEAD_DEPTH         = 0x0ff;   // depth of search
    public static final int LOOKAHEAD_EXHAUSTIVE    = 0x800;   // full breadth of search (all branches taken)
    public static final int LOOKAHEAD_NO_BRANCHING  = 0x400;   // no branches taken (internal use only!)

    static final long MAX_RMS_ERROR = 0x7FFFFFFFFFFFFFFFL;
    static final int NOISE_SHAPING_ENABLED = (NOISE_SHAPING_DYNAMIC | NOISE_SHAPING_STATIC);

    /* step table */
    static final short[] step_table = {
        7, 8, 9, 10, 11, 12, 13, 14,
        16, 17, 19, 21, 23, 25, 28, 31,
        34, 37, 41, 45, 50, 55, 60, 66,
        73, 80, 88, 97, 107, 118, 130, 143,
        157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658,
        724, 796, 876, 963, 1060, 1166, 1282, 1411,
        1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024,
        3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484,
        7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
        32767
    };

    /* step index tables */
    static final byte[] index_table = {
        /* adpcm data size is 4 */
        -1, -1, -1, -1, 2, 4, 6, 8
    };

    static final byte[] index_table_3bit = {
        /* adpcm data size is 3 */
        -1, -1, 1, 2
    };

    static final byte[] index_table_5bit = {
        /* adpcm data size is 5 */
        -1, -1, -1, -1, -1, -1, -1, -1, 1, 2, 4, 6, 8, 10, 13, 16
    };

    static int sample_count_to_block_size(int sample_count, int num_chans, int bps) {
        return ((sample_count - 1) * bps + 31) / 32 * num_chans * 4 + (num_chans * 4);
    }

    static int block_size_to_sample_count (int block_size, int num_chans, int bps) {
        return (block_size - num_chans * 4) / num_chans * 8 / bps + 1;
    }

    static int NIBBLE_TO_DELTA(int b, int n) {return ((n)<(1<<((b)-1))?(n)+1:(1<<((b)-1))-1-(n));}
    static int DELTA_TO_NIBBLE(int b, int d) {return ((d)<0?(1<<((b)-1))-1-(d):(d)-1);}
    public static int clamp(int num, int min, int max) { return Math.min(Math.max(num, min), max); }
    public static short clamp(short num, int min, int max) { return (short)Math.min(Math.max(num, min), max); }
    public static byte clamp(byte num, int min, int max) { return (byte)Math.min(Math.max(num, min), max); }
    public static float clamp(float num, float min, float max) { return Math.min(Math.max(num, min), max); }
    
    adpcm_channel[] channels;
    int num_channels, sample_rate, config_flags;
    short[] dynamic_shaping_array;
    short last_shaping_weight;
    int static_shaping_weight;

    public ADPCMEncoder(int num_channels, int sample_rate, int lookahead, int noise_shaping) {
        this.config_flags = noise_shaping | lookahead;
        this.static_shaping_weight = 1024;
        this.num_channels = num_channels;
        this.sample_rate = sample_rate;
        this.channels = new adpcm_channel[num_channels];
        for (int i = 0; i < num_channels; i++) {
            this.channels[i] = new adpcm_channel();
            this.channels[i].index = -1;
        }
    }

    public void set_shaping_weight(double shaping_weight) {
        this.static_shaping_weight = (int)Math.floor(shaping_weight * 1024.0 + 0.5);

        if (this.static_shaping_weight > 1024) this.static_shaping_weight = 1024;
        if (this.static_shaping_weight < -1023) this.static_shaping_weight = -1023;
    }

    private byte encode_sample (int ch, int bps, short[] psample, int psample_offset, int num_samples)
    {
        adpcm_channel pchan = this.channels[ch];
        short step = step_table[pchan.index];
        int flags = this.config_flags;
        int[] nibble = new int[1];
        int csample = psample[psample_offset];
        int trial_delta;

        if ((flags & NOISE_SHAPING_ENABLED) != 0)
            csample = pchan.noise_shape (csample);

        if ((flags & LOOKAHEAD_DEPTH) > num_samples - 1)
            flags = (flags & ~LOOKAHEAD_DEPTH) + num_samples - 1;

        if (bps == 2) {
            pchan.min_error_2bit (this.num_channels, csample, psample, psample_offset, flags, nibble, MAX_RMS_ERROR);

            if ((nibble[0] & 2) != 0)
                pchan.pcmdata -= step * (nibble[0] & 1) + (step >>> 1);
            else
                pchan.pcmdata += step * (nibble[0] & 1) + (step >>> 1);

            pchan.index += (nibble[0] & 1) * 3 - 1;
        }
        else if (bps == 3) {
            pchan.min_error_3bit (this.num_channels, csample, psample, psample_offset, flags, nibble, MAX_RMS_ERROR);
            trial_delta = (short)(step >>> 2);
            if ((nibble[0] & 1) != 0) trial_delta += (step >>> 1);
            if ((nibble[0] & 2) != 0) trial_delta += step;

            if ((nibble[0] & 4) != 0)
                pchan.pcmdata -= trial_delta;
            else
                pchan.pcmdata += trial_delta;

            pchan.index += index_table_3bit[nibble[0] & 0x03];
        }
        else if (bps == 4) {
            pchan.min_error_4bit (this.num_channels, csample, psample, psample_offset, flags, nibble, MAX_RMS_ERROR);
            trial_delta = (short)(step >>> 3);
            if ((nibble[0] & 1) != 0) trial_delta += (step >>> 2);
            if ((nibble[0] & 2) != 0) trial_delta += (step >>> 1);
            if ((nibble[0] & 4) != 0) trial_delta += step;

            if ((nibble[0] & 8) != 0)
                pchan.pcmdata -= trial_delta;
            else
                pchan.pcmdata += trial_delta;

            pchan.index += index_table[nibble[0] & 0x07];
        }
        else {  // bps == 5
            pchan.min_error_5bit (this.num_channels, csample, psample, psample_offset, flags, nibble, MAX_RMS_ERROR);
            trial_delta = (short)(step >>> 4);
            if ((nibble[0] & 1) != 0) trial_delta += (step >>> 3);
            if ((nibble[0] & 2) != 0) trial_delta += (step >>> 2);
            if ((nibble[0] & 4) != 0) trial_delta += (step >>> 1);
            if ((nibble[0] & 8) != 0) trial_delta += step;

            if ((nibble[0] & 0x10) != 0)
                pchan.pcmdata -= trial_delta;
            else
                pchan.pcmdata += trial_delta;

            pchan.index += index_table_5bit[nibble[0] & 0x0f];
        }

        pchan.index = clamp(pchan.index, 0, 88);
        pchan.pcmdata = clamp(pchan.pcmdata, -32768, 32767);

        if ((flags & NOISE_SHAPING_ENABLED) != 0)
            pchan.error += pchan.pcmdata;

        return (byte)nibble[0];
    }
    
    private int encode_chunks (byte[] outbuf, int outbuf_offset, short[] inbuf, int inbuf_offset, int inbufcount, int bps) {
        int offset = inbuf_offset;
        int ch;

        for (ch = 0; ch < this.num_channels; ++ch) {
            int shiftbits = 0, numbits = 0, i, j;

            if ((this.config_flags & NOISE_SHAPING_STATIC) != 0)
                this.channels [ch].shaping_weight = this.static_shaping_weight;

            offset = inbuf_offset + ch;

            for (j = i = 0; i < inbufcount; ++i) {
                if ((this.config_flags & NOISE_SHAPING_DYNAMIC) != 0)
                    this.channels [ch].shaping_weight = this.dynamic_shaping_array [i];

                shiftbits |= (encode_sample (ch, bps, inbuf, offset, inbufcount - i) << numbits);
                offset += this.num_channels;

                if ((numbits += bps) >= 8) {
                    outbuf [outbuf_offset + (j & ~3) * this.num_channels + (ch * 4) + (j & 3)] = (byte) (shiftbits & 0xFF);
                    shiftbits >>>= 8;
                    numbits -= 8;
                    j++;
                }
            }

            if (numbits != 0)
                outbuf [outbuf_offset + (j & ~3) * this.num_channels + (ch * 4) + (j & 3)] = (byte) (shiftbits & 0xFF);
        }

        return (inbufcount * bps + 31) / 32 * this.num_channels * 4;
    }

    /** Encode a block of 16-bit PCM data into N-bit ADPCM.
     *
     * @param  outbuf          destination buffer
     * @param  inbuf           source PCM samples
     * @param  bps             bits per ADPCM sample (2-5)
     *
     * @return total number of bytes for success or -1 for error (which is only invalid bit count)
     */
    public int encode_block_ex (byte[] outbuf, short[] inbuf, int bps) {
        int ch;

        int outbufsize = 0;
        int inbuf_offset = 0, outbuf_offset = 0;
        int inbufcount = inbuf.length / this.num_channels;

        if (bps < 2 || bps > 5)
            return -1;

        if (inbufcount == 0)
            return 0;

        // The first PCM sample is encoded verbatim. In theory, we should apply the noise shaping,
        // but we'll actually just apply the error term on the next sample.

        for (ch = 0; ch < this.num_channels; ch++)
            this.channels[ch].pcmdata = inbuf[inbuf_offset++];

        inbufcount--;

        // Use min_error_nbit() to find the optimum initial index if this is the first frame or
        // the lookahead depth is at least 3. Below that just using the value leftover from
        // the previous frame is better, and of course faster.

        if (inbufcount != 0 && (this.channels [0].index < 0 || (this.config_flags & LOOKAHEAD_DEPTH) >= 3)) {
            int flags = 16 | LOOKAHEAD_NO_BRANCHING;

            if ((flags & LOOKAHEAD_DEPTH) > inbufcount - 1)
                flags = (flags & ~LOOKAHEAD_DEPTH) + inbufcount - 1;

            for (ch = 0; ch < this.num_channels; ch++) {
                long min_error = MAX_RMS_ERROR;
                long[] error_per_index = new long[89];
                byte best_index = 0;
                byte tindex;

                for (tindex = 0; tindex <= 88; tindex++) {
                    adpcm_channel chan = this.channels [ch];

                    chan.index = tindex;
                    chan.shaping_weight = 0;

                    if (bps == 2)
                        error_per_index [tindex] = chan.min_error_2bit (this.num_channels, inbuf [ch], inbuf, inbuf_offset + ch, flags, null, MAX_RMS_ERROR);
                    else if (bps == 3)
                        error_per_index [tindex] = chan.min_error_3bit (this.num_channels, inbuf [ch], inbuf, inbuf_offset + ch, flags, null, MAX_RMS_ERROR);
                    else if (bps == 5)
                        error_per_index [tindex] = chan.min_error_5bit (this.num_channels, inbuf [ch], inbuf, inbuf_offset + ch, flags, null, MAX_RMS_ERROR);
                    else
                        error_per_index [tindex] = chan.min_error_4bit (this.num_channels, inbuf [ch], inbuf, inbuf_offset + ch, flags, null, MAX_RMS_ERROR);
                }

                // we use a 3-wide average window because the min_error_nbit() results can be noisy

                for (tindex = 0; tindex <= 87; tindex++) {
                    long terror = error_per_index [tindex];

                    if (tindex != 0)
                        terror = (error_per_index [tindex - 1] + terror + error_per_index [tindex + 1]) / 3;

                    if (terror < min_error) {
                        best_index = tindex;
                        min_error = terror;
                    }
                }

                this.channels [ch].index = best_index;
            }
        }

        // write the block header, which includes the first PCM sample verbatim

        for (ch = 0; ch < this.num_channels; ch++) {
            outbuf[outbuf_offset+0] = (byte)(this.channels[ch].pcmdata & 0xFF);
            outbuf[outbuf_offset+1] = (byte)(this.channels[ch].pcmdata >>> 8);
            outbuf[outbuf_offset+2] = this.channels[ch].index;
            outbuf[outbuf_offset+3] = 0;

            outbuf_offset += 4;
            outbufsize += 4;
        }

        if (inbufcount > 0 && (this.config_flags & NOISE_SHAPING_DYNAMIC) != 0) {
            this.dynamic_shaping_array = new short[inbufcount];
            generate_dns_values (inbuf, inbuf_offset, inbufcount, this.num_channels, this.sample_rate, this.dynamic_shaping_array, (short) -512, this.last_shaping_weight);
            this.last_shaping_weight = this.dynamic_shaping_array [inbufcount - 1];
        }

        // encode the rest of the PCM samples, if any, into 32-bit, possibly interleaved, chunks

        if (inbufcount > 0)
            outbufsize += encode_chunks (outbuf, outbuf_offset, inbuf, inbuf_offset, inbufcount, bps);

        if (this.dynamic_shaping_array != null && (this.config_flags & NOISE_SHAPING_DYNAMIC) != 0) {
            this.dynamic_shaping_array = null;
        }

        return outbufsize;
    }

    static final int FILTER_LENGTH = 15;
    static final int WINDOW_LENGTH = 101;
    static final int MIN_BLOCK_SAMPLES = 16;

    static void generate_dns_values (short[] samples, int samples_offset, int sample_count, int num_chans, int sample_rate,
                              short[] values, short min_value, short last_value)
    {
        float dB_offset = 7.3f, dB_scaler = 64.0f, max_dB, min_dB, max_ratio, min_ratio;
        int filtered_count = sample_count - FILTER_LENGTH + 1, i;
        float[] low_freq, high_freq;

        for (i = 0; i < sample_count; i++) values[i] = 0;

        if (filtered_count <= 0)
            return;

        low_freq = new float[filtered_count];
        high_freq = new float[filtered_count];

        // First, directly calculate the lowpassed audio using the 15-tap filter. This is
        // a basic sinc with Hann windowing (for a fast transition) and because the filter
        // is set to exactly fs/6, some terms are zero (which we can skip). Also, because
        // it's linear-phase and has an odd number of terms, we can just subtract the LF
        // result from the original to get the HF values.

        if (num_chans == 1)
            for (i = 0; i < filtered_count; ++i, ++samples_offset) {
                float filter_sum =
                    ((int) samples [samples_offset+0] + samples [samples_offset+14]) *  0.00150031f +
                        ((int) samples [samples_offset+2] + samples [samples_offset+12]) * -0.01703392f +
                        ((int) samples [samples_offset+3] + samples [samples_offset+11]) * -0.03449186f +
                        ((int) samples [samples_offset+5] + samples [ samples_offset+9]) *  0.11776258f +
                        ((int) samples [samples_offset+6] + samples [ samples_offset+8]) *  0.26543272f +
                        (int) samples [samples_offset+7]         *  0.33366033f;

                high_freq [i] = samples [samples_offset+(FILTER_LENGTH >> 1)] - filter_sum;
                low_freq [i] = filter_sum;
            }
        else
            for (i = 0; i < filtered_count; ++i, samples_offset += 2) {
                float filter_sum =
                    ((int) samples [ samples_offset+0] + samples [ samples_offset+1] + samples [samples_offset+28] + samples [samples_offset+29]) *  0.00150031f +
                        ((int) samples [ samples_offset+4] + samples [ samples_offset+5] + samples [samples_offset+24] + samples [samples_offset+25]) * -0.01703392f +
                        ((int) samples [ samples_offset+6] + samples [ samples_offset+7] + samples [samples_offset+22] + samples [samples_offset+23]) * -0.03449186f +
                        ((int) samples [samples_offset+10] + samples [samples_offset+11] + samples [samples_offset+18] + samples [samples_offset+19]) *  0.11776258f +
                        ((int) samples [samples_offset+12] + samples [samples_offset+13] + samples [samples_offset+16] + samples [samples_offset+17]) *  0.26543272f +
                        ((int) samples [samples_offset+14] + samples [samples_offset+15])                *  0.33366033f;

                high_freq [i] = samples [samples_offset+(FILTER_LENGTH & ~1)] + samples [samples_offset+FILTER_LENGTH] - filter_sum;
                low_freq [i] = filter_sum;
            }

        // Apply a simple first-order "delta" filter to the lowpass because frequencies below fs/6
        // become progressively less important for our purposes as the decorrelation filters make
        // those frequencies less and less relevant. Note that after all this filtering, the
        // magnitude level of the high frequency array will be 8.7 dB greater than the low frequency
        // array when the filters are presented with pure white noise (determined empirically).

        for (i = filtered_count - 1; i != 0; --i)
            low_freq [i] -= low_freq [i - 1];

        low_freq [0] = low_freq [1];    // simply duplicate for the "unknown" sample

        // Next we determine the averaged (absolute) levels for each sample using a box filter.

        win_average_buffer (low_freq, filtered_count, WINDOW_LENGTH >> 1);
        win_average_buffer (high_freq, filtered_count, WINDOW_LENGTH >> 1);

        // calculate the minimum and maximum ratios that won't be clipped so that we only
        // have to compute the logarithm when needed

        max_dB = 1024 / dB_scaler - dB_offset;
        min_dB = min_value / dB_scaler - dB_offset;
        max_ratio = (float) Math.pow (10.0f, max_dB / 20.0f);
        min_ratio = (float) Math.pow (10.0f, min_dB / 20.0f);

        for (i = 0; i < filtered_count; ++i)
            if (high_freq [i] > 1.0 && low_freq [i] > 1.0) {
                float ratio = high_freq [i] / low_freq [i];
                int shaping_value;

                if (ratio >= max_ratio)
                    shaping_value = 1024;
                else if (ratio <= min_ratio)
                    shaping_value = min_value;
                else
                    shaping_value = (int) Math.floor ((Math.log10 (ratio) * 20.0 + dB_offset) * dB_scaler + 0.5);

                values [i + (FILTER_LENGTH >> 1)] = (short) shaping_value;
            }

        // interpolate the first 7 values from the supplied "last_value" to the first new value

        for (i = 0; i < FILTER_LENGTH >> 1; ++i)
            values [i] =
                (short) ((
                        (int) values [FILTER_LENGTH >> 1] * (i + 1)     +
                            (int) last_value * ((FILTER_LENGTH >> 1) - i)   +
                            (FILTER_LENGTH >> 2)
                    ) / ((FILTER_LENGTH >> 1) + 1));

        // finally, copy the value at the end into the 7 final positions because unfortunately
        // we have no "next_value" to interpolate with

        for (i = filtered_count + (FILTER_LENGTH >> 1); i < sample_count; ++i)
            values [i] = values [(FILTER_LENGTH >> 1) + filtered_count - 1];
    }

    // Given a buffer of floating values, apply a simple box filter of specified half width
    // (total filter width is always odd) to determine the averaged magnitude at each point.
    // For the ends, we use only the visible samples.

    static void win_average_buffer (float[] samples, int sample_count, int half_width)
    {
        float[] output = samples; /* why was this a new array in C? */
        double sum = 0.0;
        int m = 0, n = 0;
        int i, j, k;

        for (i = 0; i < sample_count; ++i) {
            k = i + half_width + 1;
            j = i - half_width;

            if (k > sample_count) k = sample_count;
            if (j < 0) j = 0;

            while (m < j) {
                if ((sum -= samples [m] * samples [m]) < 0.0) sum = 0.0;
                m++;
            }

            while (n < k) {
                sum += samples [n] * samples [n];
                n++;
            }

            output [i] = (float)Math.sqrt (sum / (n - m));
        }
    }

    /** Encode a block of 16-bit PCM data into 4-bit ADPCM.
     *
     * @param inbuf           source PCM samples
     *
     * @return the encoded ADPCM block
     */
    public byte[] encode_block (short[] inbuf) {
        byte[] outbuf = new byte[inbuf.length / 4 + this.num_channels * 4 + 4];
        int sz = encode_block_ex (outbuf, inbuf, 4);
        return Arrays.copyOf(outbuf, sz);
    }
}
