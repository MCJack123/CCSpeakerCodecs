// SPDX-FileCopyrightText: 2019 David Bryant, 2026 JackMacWindows
//
// SPDX-License-Identifier: BSD

package cc.craftospc.ccspeakercodecs.codec.adpcm;

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

import static cc.craftospc.ccspeakercodecs.codec.adpcm.ADPCMEncoder.*;

public class ADPCMDecoder {
    /** Decode the block of 4-bit ADPCM data into PCM. This requires no context because ADPCM
     * blocks are independently decodable. This assumes that a single entire block is always
     * decoded; it must be called multiple times for multiple blocks and cannot resume in the
     * middle of a block. Note that for all other bit depths, use adpcm_decode_block_ex().
     *
     * @param outbuf          destination for interleaved PCM samples
     * @param inbuf           source ADPCM block
     * @param channels        number of channels in block (must be determined from other context)
     *
     * @return number of converted composite samples (total samples divided by number of channels)
     */
    public static int decode_block (short[] outbuf, byte[] inbuf, int channels) {
        int ch, samples = 1, chunks;
        int[] pcmdata = new int[channels];
        byte[] index = new byte[channels];
        int inbufsize = inbuf.length;
        int inbuf_offset = 0, outbuf_offset = 0;

        if (inbufsize < channels * 4)
            return 0;

        for (ch = 0; ch < channels; ch++) {
            outbuf[outbuf_offset++] = (short) (pcmdata[ch] = (inbuf [inbuf_offset+0] | (inbuf [inbuf_offset+1] << 8)));
            index[ch] = inbuf [inbuf_offset+2];

            if (index [ch] < 0 || index [ch] > 88 || inbuf [inbuf_offset+3] != 0)     // sanitize the input a little...
                return 0;

            inbufsize -= 4;
            inbuf_offset += 4;
        }

        chunks = inbufsize / (channels * 4);
        samples += chunks * 8;

        while (chunks-- > 0) {
            int i;

            for (ch = 0; ch < channels; ++ch) {

                for (i = 0; i < 4; ++i) {
                    short step = step_table [index [ch]], delta = (short) (step >>> 3);

                    if ((inbuf[inbuf_offset] & 1) != 0) delta += (step >>> 2);
                    if ((inbuf[inbuf_offset] & 2) != 0) delta += (step >>> 1);
                    if ((inbuf[inbuf_offset] & 4) != 0) delta += step;

                    if ((inbuf[inbuf_offset] & 8) != 0)
                        pcmdata[ch] -= delta;
                    else
                        pcmdata[ch] += delta;

                    index[ch] += index_table [inbuf[inbuf_offset] & 0x7];
                    index[ch] = clamp(index[ch], 0, 88);
                    pcmdata[ch] = clamp(pcmdata[ch], -32768, 32767);
                    outbuf [outbuf_offset + i * 2 * channels] = (short)pcmdata[ch];

                    step = step_table [index [ch]]; delta = (short) (step >>> 3);

                    if ((inbuf[inbuf_offset] & 0x10) != 0) delta += (step >>> 2);
                    if ((inbuf[inbuf_offset] & 0x20) != 0) delta += (step >>> 1);
                    if ((inbuf[inbuf_offset] & 0x40) != 0) delta += step;

                    if ((inbuf[inbuf_offset] & 0x80) != 0)
                        pcmdata[ch] -= delta;
                    else
                        pcmdata[ch] += delta;

                    index[ch] += index_table [(inbuf[inbuf_offset] >>> 4) & 0x7];
                    index[ch] = clamp(index[ch], 0, 88);
                    pcmdata[ch] = clamp(pcmdata[ch], -32768, 32767);
                    outbuf [outbuf_offset + (i * 2 + 1) * channels] = (short) pcmdata[ch];

                    inbuf_offset++;
                }

                outbuf_offset++;
            }

            outbuf_offset += channels * 7;
        }

        return samples;
    }

    /** Decode the block of ADPCM data, with from 2 to 5 bits per sample, into 16-bit PCM.
     * This requires no context because ADPCM blocks are independently decodable. This assumes
     * that a single entire block is always decoded; it must be called multiple times for
     * multiple blocks and cannot resume in the middle of a block.
     *
     * @param outbuf          destination for interleaved PCM samples
     * @param inbuf           source ADPCM block
     * @param channels        number of channels in block (must be determined from other context)
     * @param bps             bits per ADPCM sample (2-5, must be determined from other context)
     *
     * Returns number of converted composite samples (total samples divided by number of channels)
     */
    public static int decode_block_ex (short[] outbuf, byte[] inbuf, int channels, int bps) {
        int samples = 1, ch;
        int[] pcmdata = new int[channels];
        byte[] index = new byte[channels];
        int inbufsize = inbuf.length;
        int inbuf_offset = 0, outbuf_offset = 0;

        if (bps == 4)
            return decode_block (outbuf, inbuf, channels);

        if (bps < 2 || bps > 5 || inbufsize < channels * 4)
            return 0;

        for (ch = 0; ch < channels; ch++) {
            outbuf[outbuf_offset++] = (short) (pcmdata[ch] = (inbuf [inbuf_offset+0] | (inbuf [inbuf_offset+1] << 8)));
            index[ch] = inbuf [inbuf_offset+2];

            if (index [ch] < 0 || index [ch] > 88 || inbuf [inbuf_offset+3] != 0)     // sanitize the input a little...
                return 0;

            inbufsize -= 4;
            inbuf_offset += 4;
        }

        if (inbufsize == 0 || (inbufsize % (channels * 4)) != 0)             // extra clean
            return samples;

        samples += inbufsize / channels * 8 / bps;

        switch (bps) {
            case 2:
                for (ch = 0; ch < channels; ++ch) {
                    int shiftbits = 0, numbits = 0, i, j;

                    for (j = i = 0; i < samples - 1; ++i) {
                        short step = step_table [index [ch]];

                        if (numbits < bps) {
                            shiftbits |= inbuf [inbuf_offset + (j & ~3) * channels + (ch * 4) + (j & 3)] << numbits;
                            numbits += 8;
                            j++;
                        }

                        if ((shiftbits & 2) != 0)
                            pcmdata[ch] -= step * (shiftbits & 1) + (step >>> 1);
                        else
                            pcmdata[ch] += step * (shiftbits & 1) + (step >>> 1);

                        index[ch] += (shiftbits & 1) * 3 - 1;
                        shiftbits >>>= bps;
                        numbits -= bps;

                        index[ch] = clamp(index[ch], 0, 88);
                        pcmdata[ch] = clamp(pcmdata[ch], -32768, 32767);
                        outbuf [outbuf_offset + i * channels + ch] = (short) pcmdata[ch];
                    }
                }

                break;

            case 3:
                for (ch = 0; ch < channels; ++ch) {
                    int shiftbits = 0, numbits = 0, i, j;

                    for (j = i = 0; i < samples - 1; ++i) {
                        short step = step_table [index [ch]], delta = (short) (step >>> 2);

                        if (numbits < bps) {
                            shiftbits |= inbuf [inbuf_offset + (j & ~3) * channels + (ch * 4) + (j & 3)] << numbits;
                            numbits += 8;
                            j++;
                        }

                        if ((shiftbits & 1) != 0) delta += (step >>> 1);
                        if ((shiftbits & 2) != 0) delta += step;

                        if ((shiftbits & 4) != 0)
                            pcmdata[ch] -= delta;
                        else
                            pcmdata[ch] += delta;

                        index[ch] += index_table_3bit [shiftbits & 0x3];
                        shiftbits >>>= bps;
                        numbits -= bps;

                        index[ch] = clamp(index[ch], 0, 88);
                        pcmdata[ch] = clamp(pcmdata[ch], -32768, 32767);
                        outbuf [outbuf_offset + i * channels + ch] = (short) pcmdata[ch];
                    }
                }

                break;

            case 5:
                for (ch = 0; ch < channels; ++ch) {
                    int shiftbits = 0, numbits = 0, i, j;

                    for (j = i = 0; i < samples - 1; ++i) {
                        short step = step_table [index [ch]], delta = (short) (step >>> 4);

                        if (numbits < bps) {
                            shiftbits |= inbuf [inbuf_offset + (j & ~3) * channels + (ch * 4) + (j & 3)] << numbits;
                            numbits += 8;
                            j++;
                        }

                        if ((shiftbits & 1) != 0) delta += (step >>> 3);
                        if ((shiftbits & 2) != 0) delta += (step >>> 2);
                        if ((shiftbits & 4) != 0) delta += (step >>> 1);
                        if ((shiftbits & 8) != 0) delta += step;

                        if ((shiftbits & 0x10) != 0)
                            pcmdata[ch] -= delta;
                        else
                            pcmdata[ch] += delta;

                        index[ch] += index_table_5bit [shiftbits & 0xf];
                        shiftbits >>>= bps;
                        numbits -= bps;

                        index[ch] = clamp(index[ch], 0, 88);
                        pcmdata[ch] = clamp(pcmdata[ch], -32768, 32767);
                        outbuf [outbuf_offset + i * channels + ch] = (short) pcmdata[ch];
                    }
                }

                break;

            default:
                return 0;
        }

        return samples;
    }
}
