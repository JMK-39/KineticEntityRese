package dev.xyat.kineticentityrese.entityrese.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.kineticentityrese.KineticEntityRese;
import dev.xyat.kineticentityrese.entityrese.client.EntityReseRuleClient;
import dev.xyat.kineticentityrese.entityrese.config.EntityReseConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class EntityReseRuleNetwork {
    public static final byte RESULT_SAVE_SUCCESS = 1;
    public static final byte RESULT_REMOVE_SUCCESS = 2;
    public static final byte RESULT_PERMISSION_DENIED = 3;
    public static final byte RESULT_INVALID_RULE = 4;
    public static final byte RESULT_SAVE_FAILED = 5;

    private static final String PROTOCOL = "3";
    private static final int MAX_RULES = 4096;
    private static final int MAX_RULE_LENGTH = 512;
    private static final int MAX_COMPRESSED_RULE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_RULE_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final Type RULE_LIST_TYPE = new TypeToken<List<String>>() { }.getType();
    private static int packetId;
    private static boolean registered;

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(KineticEntityRese.MODID, "entity_rese_rules"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private EntityReseRuleNetwork() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        CHANNEL.messageBuilder(RequestRulesPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestRulesPacket::new)
                .encoder(RequestRulesPacket::encode)
                .consumerMainThread(RequestRulesPacket::handle)
                .add();

        CHANNEL.messageBuilder(SaveRulePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveRulePacket::new)
                .encoder(SaveRulePacket::encode)
                .consumerMainThread(SaveRulePacket::handle)
                .add();

        CHANNEL.messageBuilder(RemoveRulePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RemoveRulePacket::new)
                .encoder(RemoveRulePacket::encode)
                .consumerMainThread(RemoveRulePacket::handle)
                .add();

        CHANNEL.messageBuilder(RulesSnapshotPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(RulesSnapshotPacket::new)
                .encoder(RulesSnapshotPacket::encode)
                .consumerMainThread(RulesSnapshotPacket::handle)
                .add();

        CHANNEL.messageBuilder(OperationResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OperationResultPacket::new)
                .encoder(OperationResultPacket::encode)
                .consumerMainThread(OperationResultPacket::handle)
                .add();
    }

    public static void requestRules() {
        CHANNEL.sendToServer(new RequestRulesPacket());
    }

    public static void saveRule(
            String entityId,
            int threshold,
            boolean countRealDeath,
            boolean countPreventedDeath,
            boolean countCancelledDeath
    ) {
        CHANNEL.sendToServer(new SaveRulePacket(
                entityId,
                threshold,
                countRealDeath,
                countPreventedDeath,
                countCancelledDeath
        ));
    }

    public static void removeRule(String entityId) {
        CHANNEL.sendToServer(new RemoveRulePacket(entityId));
    }

    private static void sendSnapshot(ServerPlayer player) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RulesSnapshotPacket(EntityReseConfig.snapshotRules())
        );
    }

    private static void broadcastSnapshot() {
        CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new RulesSnapshotPacket(EntityReseConfig.snapshotRules())
        );
    }

    private static void sendResult(ServerPlayer player, byte result) {
        CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OperationResultPacket(result, EntityReseConfig.snapshotRules())
        );
    }

    private static boolean canEdit(ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    private static boolean validEntityId(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId == null ? "" : entityId.trim());
        return id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id);
    }

    public static final class RequestRulesPacket {
        public RequestRulesPacket() {
        }

        private RequestRulesPacket(FriendlyByteBuf buffer) {
        }

        private void encode(FriendlyByteBuf buffer) {
        }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer player = context.getSender();
            if (player != null) sendSnapshot(player);
            context.setPacketHandled(true);
        }
    }

    public static final class SaveRulePacket {
        private final String entityId;
        private final int threshold;
        private final boolean countRealDeath;
        private final boolean countPreventedDeath;
        private final boolean countCancelledDeath;

        public SaveRulePacket(
                String entityId,
                int threshold,
                boolean countRealDeath,
                boolean countPreventedDeath,
                boolean countCancelledDeath
        ) {
            this.entityId = entityId == null ? "" : entityId;
            this.threshold = threshold;
            this.countRealDeath = countRealDeath;
            this.countPreventedDeath = countPreventedDeath;
            this.countCancelledDeath = countCancelledDeath;
        }

        private SaveRulePacket(FriendlyByteBuf buffer) {
            this(
                    buffer.readUtf(MAX_RULE_LENGTH),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(entityId, MAX_RULE_LENGTH);
            buffer.writeVarInt(threshold);
            buffer.writeBoolean(countRealDeath);
            buffer.writeBoolean(countPreventedDeath);
            buffer.writeBoolean(countCancelledDeath);
        }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer player = context.getSender();
            if (!canEdit(player)) {
                if (player != null) sendResult(player, RESULT_PERMISSION_DENIED);
                context.setPacketHandled(true);
                return;
            }
            if (threshold < 1 || !validEntityId(entityId)) {
                sendResult(player, RESULT_INVALID_RULE);
                context.setPacketHandled(true);
                return;
            }

            boolean saved = EntityReseConfig.saveRuleAuthoritative(
                    entityId,
                    threshold,
                    countRealDeath,
                    countPreventedDeath,
                    countCancelledDeath
            );
            if (saved) {
                broadcastSnapshot();
                sendResult(player, RESULT_SAVE_SUCCESS);
            } else {
                sendResult(player, RESULT_SAVE_FAILED);
            }
            context.setPacketHandled(true);
        }
    }

    public static final class RemoveRulePacket {
        private final String entityId;

        public RemoveRulePacket(String entityId) {
            this.entityId = entityId == null ? "" : entityId;
        }

        private RemoveRulePacket(FriendlyByteBuf buffer) {
            this(buffer.readUtf(MAX_RULE_LENGTH));
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(entityId, MAX_RULE_LENGTH);
        }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer player = context.getSender();
            if (!canEdit(player)) {
                if (player != null) sendResult(player, RESULT_PERMISSION_DENIED);
                context.setPacketHandled(true);
                return;
            }
            if (!validEntityId(entityId)) {
                sendResult(player, RESULT_INVALID_RULE);
                context.setPacketHandled(true);
                return;
            }

            boolean saved = EntityReseConfig.removeRuleAuthoritative(entityId);
            if (saved) {
                broadcastSnapshot();
                sendResult(player, RESULT_REMOVE_SUCCESS);
            } else {
                sendResult(player, RESULT_SAVE_FAILED);
            }
            context.setPacketHandled(true);
        }
    }

    public static final class RulesSnapshotPacket {
        private final List<String> rules;

        public RulesSnapshotPacket(List<String> rules) {
            this.rules = sanitizeRules(rules);
        }

        private RulesSnapshotPacket(FriendlyByteBuf buffer) {
            this.rules = readRules(buffer);
        }

        private void encode(FriendlyByteBuf buffer) {
            writeRules(buffer, rules);
        }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            List<String> copiedRules = new ArrayList<>(rules);
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> EntityReseRuleClient.handleSnapshot(copiedRules)
            ));
            context.setPacketHandled(true);
        }
    }

    public static final class OperationResultPacket {
        private final byte result;
        private final List<String> rules;

        public OperationResultPacket(byte result, List<String> rules) {
            this.result = result;
            this.rules = sanitizeRules(rules);
        }

        private OperationResultPacket(FriendlyByteBuf buffer) {
            this.result = buffer.readByte();
            this.rules = readRules(buffer);
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeByte(result);
            writeRules(buffer, rules);
        }

        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            byte copiedResult = result;
            List<String> copiedRules = new ArrayList<>(rules);
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> EntityReseRuleClient.handleOperationResult(copiedResult, copiedRules)
            ));
            context.setPacketHandled(true);
        }
    }

    private static List<String> sanitizeRules(List<String> rules) {
        List<String> result = new ArrayList<>();
        if (rules == null) return result;
        int limit = Math.min(rules.size(), MAX_RULES);
        for (int i = 0; i < limit; i++) {
            String rule = rules.get(i);
            if (rule != null && rule.length() <= MAX_RULE_LENGTH) result.add(rule);
        }
        return result;
    }

    private static void writeRules(FriendlyByteBuf buffer, List<String> rules) {
        List<String> safeRules = sanitizeRules(rules);
        byte[] compressed = NetworkCompressUtil.compress(GSON.toJson(safeRules, RULE_LIST_TYPE));
        if (compressed.length > MAX_COMPRESSED_RULE_BYTES) {
            throw new IllegalArgumentException("compressed entity rule payload is too large");
        }
        buffer.writeByteArray(compressed);
    }

    private static List<String> readRules(FriendlyByteBuf buffer) {
        byte[] compressed = buffer.readByteArray(MAX_COMPRESSED_RULE_BYTES);
        String json = NetworkCompressUtil.decompress(compressed, MAX_DECOMPRESSED_RULE_BYTES);
        List<String> decoded = GSON.fromJson(json, RULE_LIST_TYPE);
        if (decoded == null) return new ArrayList<>();
        if (decoded.size() > MAX_RULES) {
            throw new IllegalArgumentException("invalid entity rule count: " + decoded.size());
        }
        for (String rule : decoded) {
            if (rule == null || rule.length() > MAX_RULE_LENGTH) {
                throw new IllegalArgumentException("invalid entity rule payload");
            }
        }
        return new ArrayList<>(decoded);
    }
}
