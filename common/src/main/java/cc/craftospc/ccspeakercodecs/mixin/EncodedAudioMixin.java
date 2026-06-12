// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.mixin;

import dan200.computercraft.shared.peripheral.speaker.EncodedAudio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

@Mixin(dan200.computercraft.shared.peripheral.speaker.EncodedAudio.class)
public abstract class EncodedAudioMixin {
    @Accessor("STREAM_CODEC")
    @Mutable
    static void setSTREAM_CODEC(StreamCodec<ByteBuf, EncodedAudio> codec) {
        throw new AssertionError();
    }

    static {
        setSTREAM_CODEC(new StreamCodec<ByteBuf, EncodedAudio>() {
            @Override
            public void encode(@NotNull ByteBuf _buf, @NotNull EncodedAudio obj) {
                FriendlyByteBuf buf = new FriendlyByteBuf(_buf);
                buf.writeVarInt(obj.charge()); // this won't be used if the length is 0
                buf.writeVarInt(obj.strength()); // holds the sample count
                buf.writeBoolean(obj.previousBit());
                // byte buffer is stored as varint size + data
                if (obj.charge() >= 0x8000) {
                    // write dummy null chunk for backcompat
                    buf.writeVarInt(0);
                }
                // write actual block
                buf.writeVarInt(obj.audio().remaining());
                buf.writeBytes(obj.audio().duplicate());
            }

            @Override
            public @NotNull EncodedAudio decode(@NotNull ByteBuf _buf) {
                FriendlyByteBuf buf = new FriendlyByteBuf(_buf);
                var charge = buf.readVarInt();
                var strength = buf.readVarInt();
                var previousBit = buf.readBoolean();

                if (charge >= 0x8000) buf.readVarInt(); // ignore length for extended chunk
                var length = buf.readVarInt();
                var bytes = new byte[length];
                buf.readBytes(bytes);

                return new EncodedAudio(charge, strength, previousBit, ByteBuffer.wrap(bytes));
            }
        });
    }
}
