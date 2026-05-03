// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.mixin;

import dan200.computercraft.shared.peripheral.speaker.EncodedAudio;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(dan200.computercraft.shared.peripheral.speaker.EncodedAudio.class)
public abstract class EncodedAudioMixin {
    @Invoker("charge")
    abstract int ccspeakercodecs$charge();

    @Invoker("strength")
    abstract int ccspeakercodecs$strength();

    @Invoker("audio")
    abstract ByteBuffer ccspeakercodecs$audio();

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    void write(FriendlyByteBuf buf, CallbackInfo ci) {
        if (ccspeakercodecs$charge() < 0x8000) return; // do not process valid DFPWM chunks
        // write dummy null chunk for backcompat
        buf.writeVarInt(ccspeakercodecs$charge()); // this won't be used if the length is 0
        buf.writeVarInt(ccspeakercodecs$strength()); // holds the sample count
        buf.writeBoolean(false);
        buf.writeVarInt(0);
        // write actual block
        buf.writeVarInt(ccspeakercodecs$audio().remaining());
        buf.writeBytes(ccspeakercodecs$audio().duplicate());
        ci.cancel();
    }

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private static void read(FriendlyByteBuf buf, CallbackInfoReturnable<EncodedAudio> cir) {
        var charge = buf.readVarInt();
        var strength = buf.readVarInt();
        var previousBit = buf.readBoolean();

        if (charge >= 0x8000) buf.readVarInt(); // ignore length for extended chunk
        var length = buf.readVarInt();
        var bytes = new byte[length];
        buf.readBytes(bytes);

        cir.setReturnValue(new EncodedAudio(charge, strength, previousBit, ByteBuffer.wrap(bytes)));
        cir.cancel();
    }
}
