package com.github.minecraftschurlimods.simplenetlib;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * The {@link IPacket} interface represents the base for all packets handled by simplenetlib.<br>
 * @implSpec All implementations either need to have a default constructor and implement the deserialize method or a constructor with a single {@link FriendlyByteBuf} parameter.
 * <pre>{@code
 * public class TestPacket implements IPacket {
 *     private int i;
 *
 *     public TestPacket() {} // create empty instance for deserialization
 *
 *     public TestPacket(int i) { // create instance for sending
 *         this.i = i;
 *     }
 *
 *     @Override
 *     public void deserialize(FriendlyByteBuf buf) {
 *         // deserialize from FriendlyByteBuf
 *         this.i = buf.readInt();
 *     }
 *
 *     @Override
 *     public void serialize(FriendlyByteBuf buf) {
 *         // serialize to FriendlyByteBuf
 *     }
 *
 *     @Override
 *     public void handle(NetworkEvent.Context ctx) {
 *         // handle the received and deserialized Packet
 *     }
 * }
 * }</pre>
 * or
 * <pre>{@code
 * public record ExamplePacket(int i) implements IPacket {
 *     public ExamplePacket(FriendlyByteBuf buf) {
 *         this(buf.readInt()); // deserialize from FriendlyByteBuf
 *     }
 *
 *     @Override
 *     public void serialize(FriendlyByteBuf buf) {
 *         // serialize to FriendlyByteBuf
 *     }
 *
 *     @Override
 *     public void handle(NetworkEvent.Context ctx) {
 *         // handle the received and deserialized Packet
 *     }
 * }
 * }</pre>
 */
@SuppressWarnings("unused")
public interface IPacket {
    /**
     * @return The id of the packet.
     */
    ResourceLocation id();

    /**
     * @param buf the {@link FriendlyByteBuf} to put the information into
     */
    void serialize(FriendlyByteBuf buf);

    /**
     * @param buf the {@link FriendlyByteBuf} containing the information
     */
    default void deserialize(FriendlyByteBuf buf) {throw new NotImplementedException();}

    /**
     * @param id  the {@link ResourceLocation id} of the packet
     * @param buf the {@link FriendlyByteBuf} containing the information
     */
    default void deserialize(ResourceLocation id, FriendlyByteBuf buf) {this.deserialize(buf);}

    /**
     * Handle the received message in this method
     * the data should already be deserialized when this is called
     *
     * @param ctx the {@link NetworkEvent.Context} of the received message
     */
    void handle(NetworkEvent.Context ctx);

    /**
     * Internal version of {@link IPacket#handle} Override when you want to return something else than true
     *
     * @param ctx the {@link NetworkEvent.Context} of the received message
     * @return whether the packet was handled or not
     */
    default boolean handle_(NetworkEvent.Context ctx) {
        this.handle(ctx);
        return true;
    }

    /**
     * Exception thrown when a method is not implemented.
     */
    class NotImplementedException extends RuntimeException {}
}
