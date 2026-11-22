package id.javrock.carryon;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CarryManagerService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static class CarriedBlockData {
        final BlockData blockData;
        final Material material;
        final ItemStack[] contents;
        final BlockDisplay display;

        CarriedBlockData(BlockData blockData, Material material, ItemStack[] contents, BlockDisplay display) {
            this.blockData = blockData;
            this.material = material;
            this.contents = contents;
            this.display = display;
        }
    }

    private static class CarriedEntityData {
        final Entity entity;
        final boolean hadGravity;
        final boolean hadAi;

        CarriedEntityData(Entity entity, boolean hadGravity, boolean hadAi) {
            this.entity = entity;
            this.hadGravity = hadGravity;
            this.hadAi = hadAi;
        }
    }

    private final JavrockCarryOnPlugin plugin;
    private final Map<UUID, CarriedBlockData> carriedBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, CarriedEntityData> carriedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastToggleMillis = new ConcurrentHashMap<>();

    private double carryDistance;
    private double carryHeightOffset;
    private float blockDisplayScale;
    private boolean preserveContainerContents;
    private boolean allowAllBlocks;
    private boolean entitiesEnabled;
    private Set<Material> blockWhitelist = new HashSet<>();
    private Set<Material> blockBlacklist = new HashSet<>();
    private Set<String> entityBlacklist = new HashSet<>();

    private BukkitTask followTask;

    public CarryManagerService(JavrockCarryOnPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        this.carryDistance = Math.max(0.6, plugin.getConfig().getDouble("settings.carry-distance", 1.25));
        this.carryHeightOffset = plugin.getConfig().getDouble("settings.carry-height-offset", 0.75);
        this.blockDisplayScale = (float) Math.max(0.25, plugin.getConfig().getDouble("settings.block-display-scale", 0.75));
        this.preserveContainerContents = plugin.getConfig().getBoolean("settings.preserve-container-contents", true);
        this.allowAllBlocks = plugin.getConfig().getBoolean("blocks.allow-all", false);
        this.entitiesEnabled = plugin.getConfig().getBoolean("entities.enabled", true);

        this.blockWhitelist = parseMaterials(plugin.getConfig().getStringList("blocks.whitelist"));
        this.blockBlacklist = parseMaterials(plugin.getConfig().getStringList("blocks.blacklist"));

        this.entityBlacklist = new HashSet<>();
        List<String> eBlack = plugin.getConfig().getStringList("entities.blacklist");
        for (String s : eBlack) {
            this.entityBlacklist.add(s.toUpperCase(Locale.ROOT));
        }
    }

    private Set<Material> parseMaterials(List<String> names) {
        Set<Material> set = new HashSet<>();
        for (String raw : names) {
            Material mat = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
            if (mat != null) {
                set.add(mat);
            }
        }
        return set;
    }

    public void start() {
        stop();
        this.followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCarriedObjects, 1L, 1L);
    }

    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    public boolean isCarrying(UUID uuid) {
        return carriedBlocks.containsKey(uuid) || carriedEntities.containsKey(uuid);
    }

    private boolean canToggleNow(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastToggleMillis.get(uuid);
        if (last != null && (now - last) < 250L) {
            return false;
        }
        lastToggleMillis.put(uuid, now);
        return true;
    }

    public boolean canCarryBlock(Material type) {
        if (type == null || type.isAir() || !type.isBlock() || blockBlacklist.contains(type)) {
            return false;
        }
        return allowAllBlocks || blockWhitelist.contains(type);
    }

    public boolean canCarryEntity(Entity entity) {
        if (!entitiesEnabled || entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        return !entityBlacklist.contains(entity.getType().name().toUpperCase(Locale.ROOT));
    }

    public boolean pickUpBlock(Player player, Block block) {
        if (!canToggleNow(player.getUniqueId()) || isCarrying(player.getUniqueId())) {
            return false;
        }
        Material type = block.getType();
        if (!canCarryBlock(type)) {
            sendPrefixed(player, plugin.getConfig().getString("messages.cannot-carry", "&cᴏʙᴊᴇᴋ ɪɴɪ ᴛɪᴅᴀᴋ ᴅɪɪᴢɪɴᴋᴀɴ ᴜɴᴛᴜᴋ ᴅɪᴀɴɢᴋᴀᴛ."));
            return false;
        }

        BlockData data = block.getBlockData().clone();
        ItemStack[] contents = null;

        BlockState state = block.getState();
        if (state instanceof Container container && preserveContainerContents) {
            ItemStack[] original = container.getInventory().getContents();
            contents = new ItemStack[original.length];
            for (int i = 0; i < original.length; i++) {
                contents[i] = original[i] != null ? original[i].clone() : null;
            }
            container.getInventory().clear();
            state.update(true, false);
        }

        block.setType(Material.AIR, false);

        Location spawnLoc = computeCarryLocation(player);
        BlockDisplay display = player.getWorld().spawn(spawnLoc, BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setBillboard(Display.Billboard.FIXED);
            bd.setInterpolationDuration(1);
            bd.setTeleportDuration(1);
            bd.setTransformation(new Transformation(
                    new Vector3f(-blockDisplayScale / 2f, 0f, -blockDisplayScale / 2f),
                    new AxisAngle4f(),
                    new Vector3f(blockDisplayScale, blockDisplayScale, blockDisplayScale),
                    new AxisAngle4f()
            ));
        });

        carriedBlocks.put(player.getUniqueId(), new CarriedBlockData(data, type, contents, display));

        String msg = plugin.getConfig().getString(
                "messages.picked-block",
                "&9&lᴊᴀᴠʀᴏᴄᴋ &8» &7ᴍᴇɴɢᴀɴɢᴋᴀᴛ ʙʟᴏᴋ &3%item%"
        ).replace("%item%", toSmallCaps(type.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        player.sendActionBar(LEGACY.deserialize(msg));
        return true;
    }

    public boolean placeCarriedBlock(Player player, Block clickedBlock, BlockFace face) {
        if (!canToggleNow(player.getUniqueId())) {
            return false;
        }
        CarriedBlockData carried = carriedBlocks.get(player.getUniqueId());
        if (carried == null) {
            return false;
        }

        Block target = (clickedBlock != null && face != null)
                ? clickedBlock.getRelative(face)
                : player.getLocation().getBlock().getRelative(getHorizontalFacing(player));

        if (!target.getType().isAir() && !target.isReplaceable()) {
            sendPrefixed(player, plugin.getConfig().getString("messages.cannot-place", "&cᴛɪᴅᴀᴋ ʙɪꜱᴀ ᴍᴇɴᴀʀᴜʜ ʙʟᴏᴋ ᴅɪ ꜱɪɴɪ!"));
            return false;
        }

        carriedBlocks.remove(player.getUniqueId());
        if (carried.display != null && carried.display.isValid()) {
            carried.display.remove();
        }

        target.setBlockData(carried.blockData, true);
        BlockState state = target.getState();
        if (state instanceof Container container && carried.contents != null && preserveContainerContents) {
            container.getInventory().setContents(carried.contents);
            state.update(true, true);
        }

        String msg = plugin.getConfig().getString(
                "messages.placed-block",
                "&9&lᴊᴀᴠʀᴏᴄᴋ &8» &7ᴍᴇɴᴀʀᴜʜ ʙʟᴏᴋ &3%item%"
        ).replace("%item%", toSmallCaps(carried.material.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        player.sendActionBar(LEGACY.deserialize(msg));
        return true;
    }

    public boolean pickUpEntity(Player player, Entity entity) {
        if (!canToggleNow(player.getUniqueId()) || isCarrying(player.getUniqueId())) {
            return false;
        }
        if (!canCarryEntity(entity)) {
            sendPrefixed(player, plugin.getConfig().getString("messages.cannot-carry", "&cᴏʙᴊᴇᴋ ɪɴɪ ᴛɪᴅᴀᴋ ᴅɪɪᴢɪɴᴋᴀɴ ᴜɴᴛᴜᴋ ᴅɪᴀɴɢᴋᴀᴛ."));
            return false;
        }

        boolean hadGravity = entity.hasGravity();
        boolean hadAi = false;
        if (entity instanceof LivingEntity living) {
            hadAi = living.hasAI();
            living.setAI(false);
        }
        entity.setGravity(false);
        entity.teleport(computeCarryLocation(player));

        carriedEntities.put(player.getUniqueId(), new CarriedEntityData(entity, hadGravity, hadAi));

        String msg = plugin.getConfig().getString(
                "messages.picked-entity",
                "&9&lᴊᴀᴠʀᴏᴄᴋ &8» &7ᴍᴇɴɢᴀɴɢᴋᴀᴛ &3%entity%"
        ).replace("%entity%", toSmallCaps(entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        player.sendActionBar(LEGACY.deserialize(msg));
        return true;
    }

    public boolean releaseCarriedEntity(Player player) {
        if (!canToggleNow(player.getUniqueId())) {
            return false;
        }
        CarriedEntityData data = carriedEntities.remove(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Entity entity = data.entity;
        if (entity != null && entity.isValid() && !entity.isDead()) {
            Location dropLoc = player.getLocation().clone().add(getHorizontalDirection(player).multiply(1.1));
            dropLoc.setY(player.getLocation().getY());
            entity.teleport(dropLoc);
            entity.setGravity(data.hadGravity);
            if (entity instanceof LivingEntity living) {
                living.setAI(data.hadAi);
            }

            String msg = plugin.getConfig().getString(
                    "messages.released-entity",
                    "&9&lᴊᴀᴠʀᴏᴄᴋ &8» &7ᴍᴇʟᴇᴘᴀꜱ &3%entity%"
            ).replace("%entity%", toSmallCaps(entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
            player.sendActionBar(LEGACY.deserialize(msg));
        }
        return true;
    }

    private void tickCarriedObjects() {
        for (Map.Entry<UUID, CarriedBlockData> entry : carriedBlocks.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            CarriedBlockData data = entry.getValue();
            if (player == null || !player.isOnline() || player.isDead()) {
                dropBlockOnDisconnect(entry.getKey(), data);
                continue;
            }
            if (data.display != null && data.display.isValid()) {
                Location target = computeCarryLocation(player);
                data.display.teleport(target);
                data.display.setRotation(player.getLocation().getYaw(), 0f);
            }
        }

        for (Map.Entry<UUID, CarriedEntityData> entry : carriedEntities.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            CarriedEntityData data = entry.getValue();
            if (player == null || !player.isOnline() || player.isDead() || data.entity == null || !data.entity.isValid()) {
                if (data.entity != null && data.entity.isValid()) {
                    data.entity.setGravity(data.hadGravity);
                    if (data.entity instanceof LivingEntity living) {
                        living.setAI(data.hadAi);
                    }
                }
                carriedEntities.remove(entry.getKey());
                continue;
            }
            Location target = computeCarryLocation(player);
            target.setYaw(player.getLocation().getYaw() + 180f);
            target.setPitch(0f);
            data.entity.teleport(target);
        }
    }

    private void dropBlockOnDisconnect(UUID uuid, CarriedBlockData data) {
        carriedBlocks.remove(uuid);
        if (data.display != null && data.display.isValid()) {
            Location loc = data.display.getLocation();
            data.display.remove();
            Block b = loc.getBlock();
            if (b.getType().isAir() || b.isReplaceable()) {
                b.setBlockData(data.blockData, false);
                BlockState state = b.getState();
                if (state instanceof Container container && data.contents != null) {
                    container.getInventory().setContents(data.contents);
                    state.update(true, false);
                }
            }
        }
    }

    public void cleanupAll() {
        stop();
        for (UUID uuid : carriedBlocks.keySet()) {
            CarriedBlockData data = carriedBlocks.get(uuid);
            if (data != null) {
                dropBlockOnDisconnect(uuid, data);
            }
        }
        for (Map.Entry<UUID, CarriedEntityData> entry : carriedEntities.entrySet()) {
            CarriedEntityData data = entry.getValue();
            if (data != null && data.entity != null && data.entity.isValid()) {
                data.entity.setGravity(data.hadGravity);
                if (data.entity instanceof LivingEntity living) {
                    living.setAI(data.hadAi);
                }
            }
        }
        carriedEntities.clear();
    }

    private Location computeCarryLocation(Player player) {
        Location base = player.getLocation().clone();
        Vector dir = getHorizontalDirection(player).multiply(carryDistance);
        Location loc = base.add(dir);
        loc.setY(player.getLocation().getY() + carryHeightOffset);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(0f);
        return loc;
    }

    private Vector getHorizontalDirection(Player player) {
        double yawRad = Math.toRadians(player.getLocation().getYaw());
        double x = -Math.sin(yawRad);
        double z = Math.cos(yawRad);
        return new Vector(x, 0, z).normalize();
    }

    private BlockFace getHorizontalFacing(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw >= 45 && yaw < 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return BlockFace.NORTH;
        if (yaw >= 225 && yaw < 315) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    private void sendPrefixed(Player player, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&9&lᴊᴀᴠʀᴏᴄᴋ &8» ");
        player.sendMessage(LEGACY.deserialize(prefix + message));
    }

    public static String toSmallCaps(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = Character.toLowerCase(input.charAt(i));
            switch (c) {
                case 'a' -> sb.append('ᴀ');
                case 'b' -> sb.append('ʙ');
                case 'c' -> sb.append('ᴄ');
                case 'd' -> sb.append('ᴅ');
                case 'e' -> sb.append('ᴇ');
                case 'f' -> sb.append('ꜰ');
                case 'g' -> sb.append('ɢ');
                case 'h' -> sb.append('ʜ');
                case 'i' -> sb.append('ɪ');
                case 'j' -> sb.append('ᴊ');
                case 'k' -> sb.append('ᴋ');
                case 'l' -> sb.append('ʟ');
                case 'm' -> sb.append('ᴍ');
                case 'n' -> sb.append('ɴ');
                case 'o' -> sb.append('ᴏ');
                case 'p' -> sb.append('ᴘ');
                case 'q' -> sb.append('ǫ');
                case 'r' -> sb.append('ʀ');
                case 's' -> sb.append('ꜱ');
                case 't' -> sb.append('ᴛ');
                case 'u' -> sb.append('ᴜ');
                case 'v' -> sb.append('ᴠ');
                case 'w' -> sb.append('ᴡ');
                case 'x' -> sb.append('x');
                case 'y' -> sb.append('ʏ');
                case 'z' -> sb.append('ᴢ');
                default -> sb.append(input.charAt(i));
            }
        }
        return sb.toString();
    }
}
