// SPDX-FileCopyrightText: 2023-2026 Piotr Fusik
//
// SPDX-License-Identifier: MIT

package cc.craftospc.ccspeakercodecs.codec.qoa;

// Code from qoa-fu (https://github.com/pfusik/qoa-fu) by Piotr Fusik.
// Licensed under the MIT license.

/**
 * Least Mean Squares Filter.
 */
class LMS
{
    final int[] history = new int[4];
    final int[] weights = new int[4];

    final void assign(LMS source)
    {
        System.arraycopy(source.history, 0, this.history, 0, 4);
        System.arraycopy(source.weights, 0, this.weights, 0, 4);
    }

    final int predict()
    {
        return (this.history[0] * this.weights[0] + this.history[1] * this.weights[1] + this.history[2] * this.weights[2] + this.history[3] * this.weights[3]) >> 13;
    }

    final void update(int sample, int residual)
    {
        int delta = residual >> 4;
        this.weights[0] += this.history[0] < 0 ? -delta : delta;
        this.weights[1] += this.history[1] < 0 ? -delta : delta;
        this.weights[2] += this.history[2] < 0 ? -delta : delta;
        this.weights[3] += this.history[3] < 0 ? -delta : delta;
        this.history[0] = this.history[1];
        this.history[1] = this.history[2];
        this.history[2] = this.history[3];
        this.history[3] = sample;
    }
}

