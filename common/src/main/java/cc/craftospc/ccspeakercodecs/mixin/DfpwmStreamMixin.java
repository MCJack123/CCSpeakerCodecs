// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.mixin;

import cc.craftospc.ccspeakercodecs.CCSpeakerCodecs;
import cc.craftospc.ccspeakercodecs.codec.Codec;
import dan200.computercraft.shared.peripheral.speaker.EncodedAudio;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;

@Mixin(targets = "dan200.computercraft.client.sound.DfpwmStream")
public abstract class DfpwmStreamMixin {
    @Accessor("buffers")
    abstract Queue<ByteBuffer> ccspeakercodecs$getBuffers();

    @Inject(method = "push", at = @At("HEAD"), cancellable = true)
    void push(EncodedAudio audio, CallbackInfo ci) {
        if (audio.charge() < 0x8000) return; // process DFPWM normally
        byte id = (byte)(audio.charge() & 0xFF);
        Codec codec = Codec.byID(id);
        if (codec == null) {
            CCSpeakerCodecs.LOG.warning("Could not find codec for id " + id + ". Skipping audio chunk.");
            ci.cancel();
            return;
        }
        var input = audio.audio();
        if (!input.hasRemaining()) {
            CCSpeakerCodecs.LOG.warning("Received empty audio. Skipping audio chunk.");
            ci.cancel();
            return;
        }
        byte[] bytes = new byte[input.remaining()];
        input.get(bytes);
        try {
            short[] samples = codec.decode(bytes, audio.strength());
            ByteBuffer samples8 = ByteBuffer.allocate(samples.length).order(ByteOrder.nativeOrder());
            for (short sample : samples) samples8.put((byte) ((sample >> 8) ^ 0x80));
            samples8.flip();
            synchronized (this) {
                ccspeakercodecs$getBuffers().add(samples8);
            }
        } catch (RuntimeException e) {
            CCSpeakerCodecs.LOG.severe("Threw error while decoding audio. Skipping audio chunk.");
            CCSpeakerCodecs.LOG.severe(e.getMessage());
        } finally {
            ci.cancel();
        }
    }
}
