package com.github.minecraftschurlimods.simplenetlib;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Abstract base class for a Packet using a codec to encode and decode the data.
 *
 * @param <T> the datatype of the data being sent.
 */
public abstract class CodecPacket<T> implements IPacket {
    protected ResourceLocation id;
    protected final T data;

    /**
     * Constructor accepting the data to send.
     *
     * @param data the data to send.
     */
    public CodecPacket(ResourceLocation id, T data) {
        this.id = id;
        this.data = data;
    }

    /**
     * Constructor for deserialization.<br>
     * Subclasses must have this constructor present.
     */
    public CodecPacket(ResourceLocation id, FriendlyByteBuf buf) {
        this.id = id;
        CompoundTag tag = buf.readAnySizeNbt();
        this.data = this.codec().decode(NbtOps.INSTANCE, tag.get("data")).get().mapLeft(Pair::getFirst).orThrow();
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        tag.put("data", this.codec().encodeStart(NbtOps.INSTANCE, this.data).get().orThrow());
        buf.writeNbt(tag);
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    /**
     * Implement this method and return the codec to encode and decode the data.
     *
     * @return the codec to encode and decode the data.
     */
    protected abstract Codec<T> codec();
}
