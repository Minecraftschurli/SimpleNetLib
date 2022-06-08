package com.github.minecraftschurlimods.simplenetlib;

import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class makes communication between server and client easier by wrapping the vanilla SimpleChannel network implementation
 */
@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "unused"})
public final class NetworkHandler {
    private static final Map<ResourceLocation, NetworkHandler> HANDLERS = new ConcurrentHashMap<>();

    private static final Marker SEND_MARKER     = MarkerFactory.getMarker("NETWORK_SEND");
    private static final Marker REGISTER_MARKER = MarkerFactory.getMarker("NETWORK_REGISTER");

    private final Logger logger;
    private final SimpleChannel channel;
    private final Map<ResourceLocation, RegisteredPacket<? extends IPacket>> packets = new ConcurrentHashMap<>();

    /**
     * Create or get the {@link NetworkHandler} for the modid, and the channel name with the given version
     *
     * @param modid       the modid
     * @param channelName the name for the network channel
     * @param version     the version for the network channel
     * @return the {@link NetworkHandler} associated with the modid, and the channel name
     */
    public static NetworkHandler create(String modid, String channelName, int version) {
        return create(modid, channelName, String.valueOf(version));
    }

    /**
     * Create or get the {@link NetworkHandler} for the modid, and the channel name with the given version
     *
     * @param modid       the modid
     * @param channelName the name for the network channel
     * @param version     the version for the network channel
     * @return the {@link NetworkHandler} associated with the modid, and the channel name
     */
    public static NetworkHandler create(String modid, String channelName, String version) {
        return create(new ResourceLocation(modid, channelName), version);
    }

    /**
     * Create or get the {@link NetworkHandler} for the resourceLocation with the given version
     *
     * @param rl      the {@link ResourceLocation} for the network channel
     * @param version the version for the network channel
     * @return the {@link NetworkHandler} associated with the {@link ResourceLocation}
     */
    public static NetworkHandler create(ResourceLocation rl, String version) {
        return create(rl, () -> version, version::equals);
    }

    /**
     * Create or get the {@link NetworkHandler} for the modid, and the channel name with the given version supplier and predicate
     *
     * @param modid         the modid
     * @param channelName   the name for the network channel
     * @param version       the supplier for the version string
     * @param acceptVersion the predicate to check if the version is valid
     * @return the {@link NetworkHandler} associated with the modid, and the channel name
     */
    public static NetworkHandler create(String modid,
                                        String channelName,
                                        Supplier<String> version,
                                        Predicate<String> acceptVersion) {
        return create(new ResourceLocation(modid, channelName), version, acceptVersion);
    }

    /**
     * Create or get the {@link NetworkHandler} for the resourceLocation with the given version supplier and predicate
     *
     * @param rl            the {@link ResourceLocation} for the network channel
     * @param version       the supplier for the version string
     * @param acceptVersion the predicate to check if the version is valid
     * @return the {@link NetworkHandler} associated with the {@link ResourceLocation}
     */
    public static NetworkHandler create(ResourceLocation rl,
                                        Supplier<String> version,
                                        Predicate<String> acceptVersion) {
        return create(rl, version, acceptVersion, acceptVersion);
    }

    /**
     * Create or get the {@link NetworkHandler} for the modid, and the channel name with the given version supplier and predicates
     *
     * @param modid               the modid
     * @param channelName         the name for the network channel
     * @param version             the supplier for the version string
     * @param acceptVersionClient the predicate to check if the client version is valid
     * @param acceptVersionServer the predicate to check if the server version is valid
     * @return the {@link NetworkHandler} associated with the modid, and the channel name
     */
    public static NetworkHandler create(String modid,
                                        String channelName,
                                        Supplier<String> version,
                                        Predicate<String> acceptVersionClient,
                                        Predicate<String> acceptVersionServer) {
        return create(new ResourceLocation(modid, channelName), version, acceptVersionClient, acceptVersionServer);
    }

    /**
     * Create or get the {@link NetworkHandler} for the resourceLocation with the given version supplier and predicates
     *
     * @param rl                  the {@link ResourceLocation} for the network channel
     * @param version             the supplier for the version string
     * @param acceptVersionClient the predicate to check if the client version is valid
     * @param acceptVersionServer the predicate to check if the server version is valid
     * @return the {@link NetworkHandler} associated with the {@link ResourceLocation}
     */
    public static NetworkHandler create(ResourceLocation rl,
                                        Supplier<String> version,
                                        Predicate<String> acceptVersionClient,
                                        Predicate<String> acceptVersionServer) {
        return HANDLERS.computeIfAbsent(rl, resourceLocation -> new NetworkHandler(resourceLocation, version, acceptVersionClient, acceptVersionServer));
    }

    private NetworkHandler(ResourceLocation rl,
                           Supplier<String> version,
                           Predicate<String> acceptVersionClient,
                           Predicate<String> acceptVersionServer) {
        this.logger = LoggerFactory.getLogger(String.format("NetworkHandler(%s)", rl));
        this.logger.debug("Created NetworkHandler for mod with id {}", rl);
        this.channel = NetworkRegistry.newSimpleChannel(rl, version, acceptVersionClient, acceptVersionServer);
        this.channel.registerMessage(
                0,
                MasterPacket.class,
                MasterPacket::toBytes,
                buf -> {
                    ResourceLocation packetId = buf.readResourceLocation();
                    return new MasterPacket(packetId, deserializePacket(packetId, buf));
                },
                (masterPacket, contextSupplier) -> handlePacket(masterPacket.packetId, masterPacket.packet, contextSupplier));
    }

    @SuppressWarnings("unchecked")
    private void handlePacket(ResourceLocation packetId, IPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        RegisteredPacket<IPacket> registeredPacket = (RegisteredPacket<IPacket>) this.packets.get(packetId);
        if (registeredPacket == null) {
            this.logger.warn("Received packet {} but no packet handler was registered", packetId);
            return;
        }
        registeredPacket.handler().accept(packet, contextSupplier);
    }

    private IPacket deserializePacket(ResourceLocation packetId, FriendlyByteBuf buf) {
        RegisteredPacket<?> registeredPacket = this.packets.get(packetId);
        if (registeredPacket == null) {
            throw new IllegalArgumentException("No packet deserializer registered for packet " + packetId);
        }
        return registeredPacket.deserializer().apply(packetId, buf);
    }

    /**
     * Register a Packet for a {@link NetworkDirection}
     *
     * @param id    the id for the Packet
     * @param clazz the class of the Packet implementing {@link IPacket}
     * @param dir   the {@link NetworkDirection} for the Packet
     * @param <T>   the type of the Packet implementing {@link IPacket}
     */
    public <T extends IPacket> void register(ResourceLocation id, Class<T> clazz, @Nullable NetworkDirection dir) {
        this.register(id, clazz, Optional.ofNullable(dir));
    }

    /**
     * Register a Packet with defined encoder, decoder, consumer and direction
     *
     * @param id the id for the Packet
     * @param clazz the class of the registered Packet
     * @param encoder the encoder for the Packet
     * @param decoder the decoder for the Packet
     * @param consumer the consumer that handles the Packet
     * @param dir the Optional NetworkDirection for the Packet
     * @param <T> the Type of the Packet
     */
    public <T extends IPacket> void register(ResourceLocation id,
                                             Class<T> clazz,
                                             BiConsumer<T, FriendlyByteBuf> encoder,
                                             BiFunction<ResourceLocation, FriendlyByteBuf, T> decoder,
                                             BiConsumer<T, Supplier<NetworkEvent.Context>> consumer,
                                             Optional<NetworkDirection> dir) {
        if (dir.isPresent()) {
            this.logger.debug(REGISTER_MARKER, "Registered package {}({}) for direction {}", id, clazz.getName(), dir.get().name());
        } else {
            this.logger.debug(REGISTER_MARKER, "Registered package {}({}) for all directions", id, clazz.getName());
        }
        this.packets.put(id, new RegisteredPacket<>(clazz, encoder, decoder, consumer, dir));
    }

    /**
     * Sends a Packet to all Players in level
     *
     * @side server
     * @param packet the Packet to send
     * @param level  the World to send to
     */
    public void sendToWorld(IPacket packet, Level level) {
        if (level.isClientSide()) {
            this.logger.trace(SEND_MARKER, "Tried to send a message from the wrong side");
            return;
        }
        this.logger.trace(SEND_MARKER, "Sending packet {}({}) from server to the level {}", packet.id(), packet.getClass().getName(), level);
        this.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    /**
     * Sends a Packet to all Players in a defined radius
     *
     * @side server
     * @param packet the Packet to send
     * @param level  the World to send to
     * @param pos    the center position around which the radius is calculated
     * @param radius the radius in which players receive the packet
     */
    public void sendToAllAround(IPacket packet, Level level, BlockPos pos, float radius) {
        if (level.isClientSide()) {
            this.logger.trace(SEND_MARKER, "Tried to send a message from the wrong side");
            return;
        }
        this.logger.trace(SEND_MARKER, "Sending packet {}({}) to all clients in the level {} in radius {} around position {}", packet.id(), packet.getClass().getName(), level, radius, pos);
        this.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(pos.getX(), pos.getY(), pos.getZ(), radius, level.dimension())), packet);
    }

    /**
     * Sends a Packet to all Players tracking the state of a chunk
     *
     * @side server
     * @param packet the Packet to send
     * @param level  the World to send to
     * @param pos    the position within the tracked chunk
     */
    public void sendToAllTracking(IPacket packet, Level level, BlockPos pos) {
        this.sendToAllTracking(packet, level.getChunkAt(pos));
    }

    /**
     * Sends a Packet to all Players tracking the state of a chunk
     *
     * @side server
     * @param packet the Packet to send
     * @param level  the World to send to
     * @param pos    the position of the chunk
     */
    public void sendToAllTracking(IPacket packet, Level level, ChunkPos pos) {
        this.sendToAllTracking(packet, level.getChunkAt(pos.getWorldPosition()));
    }

    /**
     * Sends a Packet to all Players tracking the state of a chunk
     *
     * @side server
     * @param packet the Packet to send
     * @param chunk  the tracked Chunk
     */
    public void sendToAllTracking(IPacket packet, LevelChunk chunk) {
        if (chunk.getLevel().isClientSide()) {
            this.logger.trace(SEND_MARKER, "Tried to send a message from the wrong side");
            return;
        }
        this.logger.trace(SEND_MARKER, "Sending packet {}({}) to all clients tracking chunk {}", packet.id(), packet.getClass().getName(), chunk);
        this.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
    }

    /**
     * Sends a Packet to all Players tracking the state of an entity
     *
     * @side server
     * @param packet the Packet to send
     * @param entity the tracked Entity
     */
    public void sendToAllTracking(IPacket packet, Entity entity) {
        if (entity.level.isClientSide()) {
            this.logger.trace(SEND_MARKER, "Tried to send a message from the wrong side");
            return;
        }
        this.logger.trace(SEND_MARKER, "Sending packet {} to all clients tracking entity {}", packet.getClass().getName(), entity);
        this.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /**
     * Sends a Packet to the specified Player or all players if it is null
     *
     * @side server
     * @param packet the Packet to send
     * @param player the Player to send to or null
     */
    public void sendToPlayerOrAll(IPacket packet, @Nullable Player player) {
        if (player == null) {
            this.sendToAll(packet);
        } else {
            this.sendToPlayer(packet, player);
        }
    }

    /**
     * Sends a Packet to the specified Player
     *
     * @side server
     * @param packet the Packet to send
     * @param player the Player to send to
     */
    public void sendToPlayer(IPacket packet, Player player) {
        if (player.level.isClientSide() || !(player instanceof ServerPlayer)) {
            this.logger.trace(SEND_MARKER, "Tried to send a message from the wrong side");
            return;
        }
        this.logger.trace(SEND_MARKER, "Sending packet {} to player {}", packet.getClass().getName(), player);
        this.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), packet);
    }

    /**
     * Sends a Packet to all connected clients
     *
     * @side server
     * @param packet the Packet to send
     */
    public void sendToAll(IPacket packet) {
        this.logger.trace(SEND_MARKER, "Sending packet {} to all clients", packet.getClass().getName());
        this.send(PacketDistributor.ALL.noArg(), packet);
    }

    /**
     * Sends a Packet to all given connections
     *
     * @side server
     * @param packet the Packet to send
     */
    public void sendTo(IPacket packet, List<Connection> connections) {
        this.logger.trace(SEND_MARKER, "Sending packet {} to all clients", packet.getClass().getName());
        this.send(PacketDistributor.NMLIST.with(() -> connections), packet);
    }

    /**
     * Send a Packet to the server
     *
     * @side client
     * @param packet the Packet to send
     */
    public void sendToServer(IPacket packet) {
        this.logger.trace(SEND_MARKER, "Sending packet {} to server", packet.getClass().getName());
        this.channel.sendToServer(new MasterPacket(packet));
    }

    /**
     * Reply to a received message with packet
     *
     * @side both
     * @param packet  the Packet representing the replying message
     * @param context the context of the received Packet
     */
    public void reply(IPacket packet, NetworkEvent.Context context) {
        this.logger.trace(SEND_MARKER, "Sending packet {} as reply to context NetworkEvent.Context[{}]", packet.getClass().getName(), context.getDirection().name());
        this.channel.reply(new MasterPacket(packet), context);
    }

    private void send(PacketDistributor.PacketTarget target, IPacket message) {
        this.channel.send(target, new MasterPacket(message));
    }

    private <T extends IPacket> void register(ResourceLocation id, Class<T> clazz, Optional<NetworkDirection> dir) {
        BiConsumer<T, FriendlyByteBuf> encoder = IPacket::serialize;
        BiFunction<ResourceLocation, FriendlyByteBuf, T> decoder = getDeserializer(clazz);
        BiConsumer<T, Supplier<NetworkEvent.Context>> consumer = new DirectionFilteredPacketHandler<>(dir);
        this.register(id, clazz, encoder, decoder, consumer, dir);
    }

    private static <T extends IPacket> PacketDeserializer<T> getDeserializer(Class<T> clazz) {
        Optional<Constructor<T>> deserializingWithRLConstructor = getDeserializingWithRLConstructor(clazz);
        if (deserializingWithRLConstructor.isPresent()) {
            Constructor<T> constructor = deserializingWithRLConstructor.get();
            return constructor::newInstance;
        }
        Optional<Constructor<T>> deserializingConstructor = getDeserializingConstructor(clazz);
        if (deserializingConstructor.isPresent()) {
            Constructor<T> constructor = deserializingConstructor.get();
            return ($, buf) -> constructor.newInstance(buf);
        }
        Optional<Constructor<T>> primaryConstructorWithRL = getPrimaryConstructorWithRL(clazz);
        if (primaryConstructorWithRL.isPresent()) {
            Constructor<T> constructor = primaryConstructorWithRL.get();
            return (resourceLocation, buf) -> instantiatePacket(constructor, resourceLocation, buf);
        }
        Optional<Constructor<T>> primaryConstructor = getPrimaryConstructor(clazz);
        if (primaryConstructor.isPresent()) {
            Constructor<T> constructor = primaryConstructor.get();
            return ($, buf) -> instantiatePacket(constructor, buf);
        }
        throw new IllegalArgumentException(String.format("%s does not supply a deserialization mechanism", clazz));
    }

    private static <T extends IPacket> Optional<Constructor<T>> getDeserializingConstructor(Class<T> clazz) {
        try {
            return Optional.of(clazz.getConstructor(FriendlyByteBuf.class));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static <T extends IPacket> Optional<Constructor<T>> getDeserializingWithRLConstructor(Class<T> clazz) {
        try {
            return Optional.of(clazz.getConstructor(ResourceLocation.class, FriendlyByteBuf.class));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static <T extends IPacket> Optional<Constructor<T>> getPrimaryConstructor(Class<T> clazz) {
        try {
            return Optional.of(clazz.getConstructor());
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static <T extends IPacket> Optional<Constructor<T>> getPrimaryConstructorWithRL(Class<T> clazz) {
        try {
            return Optional.of(clazz.getConstructor(ResourceLocation.class));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static <T extends IPacket> T instantiatePacket(Constructor<T> constructor, ResourceLocation rl, FriendlyByteBuf buf) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        T packet = constructor.newInstance();
        packet.deserialize(rl, buf);
        return packet;
    }

    private static <T extends IPacket> T instantiatePacket(Constructor<T> constructor, FriendlyByteBuf buf) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        T packet = constructor.newInstance();
        packet.deserialize(buf);
        return packet;
    }

    private record MasterPacket(ResourceLocation packetId, IPacket packet) {

        private MasterPacket(IPacket packet) {
            this(packet.id(), packet);
        }

        private void toBytes(FriendlyByteBuf buf) {
            buf.writeResourceLocation(this.packetId);
            this.packet.serialize(buf);
        }
    }

    @FunctionalInterface
    private interface PacketDeserializer<T extends IPacket> extends BiFunction<ResourceLocation, FriendlyByteBuf, T> {
        T deserialize(ResourceLocation packetId, FriendlyByteBuf buf) throws Exception;

        default T apply(ResourceLocation packetId, FriendlyByteBuf buf) {
            try {
                return this.deserialize(packetId, buf);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private record DirectionFilteredPacketHandler<T extends IPacket>(Optional<NetworkDirection> direction) implements BiConsumer<T, Supplier<NetworkEvent.Context>> {
        @Override
        public void accept(T packet, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            if (ctx == null) return;
            if (direction.isPresent() && direction.get() != ctx.getDirection()) return;
            ctx.setPacketHandled(packet.handle_(ctx));
        }
    }

    private record RegisteredPacket<T extends IPacket>(Class<T> clazz, BiConsumer<T, FriendlyByteBuf> serializer, BiFunction<ResourceLocation, FriendlyByteBuf, T> deserializer, BiConsumer<T, Supplier<NetworkEvent.Context>> handler, Optional<NetworkDirection> direction) {}
}
