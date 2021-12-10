package com.github.minecraftschurli.simplenetlib;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Abstract base class for a Packet using a codec to encode and decode the data.
 *
 * @param <T> the datatype of the data being sent.
 */
public abstract class CodecPacket<T> implements IPacket {
    protected final T data;

    /**
     * Constructor accepting the data to send.
     *
     * @param data the data to send.
     */
    public CodecPacket(T data) {
        this.data = data;
    }

    /**
     * Constructor for deserialization.<br>
     * Subclasses must have this constructor present.
     */
    public CodecPacket(FriendlyByteBuf buf) {
        this.data = buf.readWithCodec(this.getCodec());
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        buf.writeWithCodec(this.getCodec(), this.data);
    }

    /**
     * Implement this method and return the codec to encode and decode the data.
     *
     * @return the codec to encode and decode the data.
     */
    protected abstract Codec<T> getCodec();
}
