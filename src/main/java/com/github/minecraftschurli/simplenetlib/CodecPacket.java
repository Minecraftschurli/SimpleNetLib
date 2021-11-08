package com.github.minecraftschurli.simplenetlib;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;

public abstract class CodecPacket<T> implements IPacket {
    protected final T data;

    public CodecPacket(T data) {
        this.data = data;
    }

    public CodecPacket(FriendlyByteBuf buf) {
        this.data = buf.readWithCodec(this.getCodec());
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        buf.writeWithCodec(this.getCodec(), this.data);
    }

    protected abstract Codec<T> getCodec();
}
