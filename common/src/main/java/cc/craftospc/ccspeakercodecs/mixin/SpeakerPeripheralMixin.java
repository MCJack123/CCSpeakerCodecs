// SPDX-FileCopyrightText: 2026 JackMacWindows
//
// SPDX-License-Identifier: MPL-2.0

package cc.craftospc.ccspeakercodecs.mixin;

import cc.craftospc.ccspeakercodecs.DfpwmStateBridge;
import cc.craftospc.ccspeakercodecs.codec.Codec;
import cc.craftospc.ccspeakercodecs.codec.DFPWMCodec;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.shared.peripheral.speaker.DfpwmState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral.class)
public abstract class SpeakerPeripheralMixin {
    @Unique
    public Codec codec_ccspeakercodecs = DFPWMCodec.INSTANCE;

    @Accessor("dfpwmState")
    abstract DfpwmState ccspeakercodecs$get_dfpwmState();

    @Invoker("clampVolume")
    public static double ccspeakercodecs$clampVolume(double volume) {throw new AssertionError();}

    @Inject(method = "playAudio", at = @At(
        value = "FIELD",
        target = "Ldan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral;dfpwmState:Ldan200/computercraft/shared/peripheral/speaker/DfpwmState;",
        opcode = Opcodes.PUTFIELD,
        shift = At.Shift.AFTER
    ))
    private void createDfpwmState(ILuaContext context, LuaTable<?, ?> audio, Optional<Double> volume, CallbackInfoReturnable<Boolean> cir) {
        DfpwmState state = ccspeakercodecs$get_dfpwmState();
        if (state != null) ((DfpwmStateBridge)state).setCodec_ccspeakercodecs(codec_ccspeakercodecs);
    }

    @LuaFunction
    public final void setAudioCodec(String name, LuaTable<String, ?> options) throws LuaException {
        Codec codec = Codec.byName(name, options);
        if (codec == null) throw new LuaException("Unknown codec: " + name);
        codec_ccspeakercodecs = codec;
        DfpwmState state = ccspeakercodecs$get_dfpwmState();
        if (state != null) ((DfpwmStateBridge)state).setCodec_ccspeakercodecs(codec);
    }
}
