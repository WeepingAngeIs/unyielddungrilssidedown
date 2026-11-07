package dev.unyielding.upsidedown;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Dimension- and instance-scoped access to the existing persistent gravity system. */
public final class DungeonZoneBridge {
    private DungeonZoneBridge() {}

    /** Names must begin with {@code instancePrefix + "/"}; unrelated zones are preserved. */
    public static void replace(MinecraftServer server, ResourceKey<Level> dimension,
                               String instancePrefix, List<Flip.Zone> replacements) {
        requireServerThread(server);
        Objects.requireNonNull(dimension, "dimension");
        String prefix = scopedPrefix(instancePrefix);
        var names = new HashSet<String>();
        var safe = List.copyOf(replacements);
        for (Flip.Zone zone : safe) {
            if (!dimension.equals(zone.dim()) || !zone.name().startsWith(prefix)
                    || !names.add(zone.name()) || zone.gravity() == null) {
                throw new IllegalArgumentException("Zones must have unique names within their dungeon and dimension");
            }
            var box = zone.box();
            if (box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.minY)
                    || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxX)
                    || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                    || box.maxX <= box.minX || box.maxY <= box.minY || box.maxZ <= box.minZ) {
                throw new IllegalArgumentException("Gravity bounds must be finite and nonempty");
            }
        }
        var all = new ArrayList<>(Flip.SERVER);
        all.removeIf(zone -> dimension.equals(zone.dim()) && zone.name().startsWith(prefix));
        all.addAll(safe);
        UpsideDown.publish(server, all);
    }

    public static int remove(MinecraftServer server, ResourceKey<Level> dimension, String instancePrefix) {
        requireServerThread(server);
        int count = snapshot(server, dimension, instancePrefix).size();
        replace(server, dimension, instancePrefix, List.of());
        return count;
    }

    public static List<Flip.Zone> snapshot(MinecraftServer server, ResourceKey<Level> dimension, String instancePrefix) {
        requireServerThread(server);
        Objects.requireNonNull(dimension, "dimension");
        String prefix = scopedPrefix(instancePrefix);
        return Flip.SERVER.stream()
                .filter(zone -> dimension.equals(zone.dim()) && zone.name().startsWith(prefix)).toList();
    }

    /**
     * Commit gravity state before marking a dungeon journal transition durable.
     * Ordinary SavedData.save queues asynchronous writes and logs (rather than
     * propagates) IO errors. This path waits older snapshots out, then atomically
     * writes and fsyncs this snapshot; a failed write remains dirty and is thrown.
     */
    public static void flush(MinecraftServer server) throws IOException {
        requireServerThread(server);
        IOUtilities.waitUntilIOWorkerComplete();
        ZoneData data = ZoneData.get(server);
        data.setDirty(true);
        CompoundTag root = new CompoundTag();
        root.put("data", data.save(new CompoundTag(), server.registryAccess()));
        NbtUtils.addCurrentDataVersion(root);
        var target = server.getWorldPath(LevelResource.ROOT).resolve("data/cuupsidedown_zones.dat");
        java.nio.file.Files.createDirectories(target.getParent());
        IOUtilities.writeNbtCompressed(root, target);
        data.setDirty(false);
    }

    private static String scopedPrefix(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Dungeon instance prefix is required");
        return value.endsWith("/") ? value : value + "/";
    }

    private static void requireServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) throw new IllegalStateException("Gravity changes must run on the server thread");
    }
}
