// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.mixin;

import cc.craftospc.ccspeakercodecs.DfpwmStateBridge;
import cc.craftospc.ccspeakercodecs.codec.Codec;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.api.lua.LuaValues;
import dan200.computercraft.shared.peripheral.speaker.EncodedAudio;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.Optional;

@Mixin(targets = "dan200.computercraft.shared.peripheral.speaker.DfpwmState")
abstract class DfpwmStateMixin implements DfpwmStateBridge {
    @Unique
    Codec codec_ccspeakercodecs = Codec.DFPWM;

    @Override
    public void setCodec_ccspeakercodecs(Codec codec) {codec_ccspeakercodecs = codec;}

    @Accessor("pendingAudio")
    abstract EncodedAudio ccspeakercodecs$get_pendingAudio();

    @Accessor("pendingAudio")
    abstract void ccspeakercodecs$set_pendingAudio(EncodedAudio pendingAudio);

    @Accessor("pendingVolume")
    abstract float ccspeakercodecs$get_pendingVolume();

    @Accessor("pendingVolume")
    abstract void ccspeakercodecs$set_pendingVolume(float pendingVolume);

    @Inject(method = "pushBuffer", at = @At("HEAD"), cancellable = true)
    void pushBuffer(LuaTable<?, ?> table, int size, Optional<Double> volume, CallbackInfoReturnable<Boolean> cir) throws LuaException {
        if (codec_ccspeakercodecs == Codec.DFPWM) return; // continue with regular execution
        if (ccspeakercodecs$get_pendingAudio() != null) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        short[] samples = new short[size];
        for (int i = 0; i < size; i++) {
            Object value = table.get(i+1);
            if (!(value instanceof Number number)) throw LuaValues.badTableItem(i+1, "number", LuaValues.getType(value));
            var level = number.doubleValue();
            if (level < -128.0 || level >= 128.0) {
                throw new LuaException("table item #" + i + " must be between -128 and 127");
            }
            samples[i] = (short) (level * 256.0);
        }
        byte[] bytes = codec_ccspeakercodecs.encode(samples);

        ccspeakercodecs$set_pendingAudio(new EncodedAudio(0x8000 | codec_ccspeakercodecs.id(), size, false, ByteBuffer.wrap(bytes)));
        ccspeakercodecs$set_pendingVolume((float) SpeakerPeripheralAccessor.callClampVolume(volume.orElse((double) ccspeakercodecs$get_pendingVolume())));
        cir.setReturnValue(true);
        cir.cancel();
    }
}
