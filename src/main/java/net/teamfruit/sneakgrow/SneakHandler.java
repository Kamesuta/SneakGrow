package net.teamfruit.sneakgrow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class SneakHandler implements Listener {
    private Map<String, PlayerState> states = new HashMap<>();

    private static final ItemStack boneMeal = new ItemStack(Material.BONE_MEAL);
    private static final Object nmsBoneMeal = ReflectionUtil.itemStackAsNmsCopy(boneMeal);

    private final Random rnd = new Random();

    public class PlayerState {
        public boolean isSneaking;
        public boolean isSneaked;
        public int ticksLastCheck;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        onAction(event.getPlayer());
    }

    private void onAction(Player player) {
        boolean bPermissionBlock = player.hasPermission("sneakgrow.block");
        boolean bPermissionEntity = player.hasPermission("sneakgrow.entity");
        if (!bPermissionBlock && !bPermissionEntity)
            return;

        PlayerState state = states.computeIfAbsent(player.getName(), e -> new PlayerState());

        int ticksNow = Bukkit.getCurrentTick();
        int ticksSinceLastCheck = ticksNow - state.ticksLastCheck;
        if (ticksSinceLastCheck >= SneakGrow.cooldown) {
            state.ticksLastCheck = ticksNow;

            World world = player.getWorld();
            Object nmsWorld = ReflectionUtil.craftWorldGetHandle(world);
            Location location = player.getLocation();

            if (state.isSneaked) {
                if (bPermissionBlock) {
                    Optional<Block> blockOptional = getRandomElement(getAgeableBlockInRange(location));

                    blockOptional.ifPresent(block -> {
                        if (rnd.nextFloat() < .45) {
                            Object nmsBlockPosition = ReflectionUtil.constructBlockPosition(block.getX(), block.getY(), block.getZ());
                            ReflectionUtil.applyBoneMeal(nmsBoneMeal, nmsWorld, nmsBlockPosition);
                        }

                        if (SneakGrow.showParticles)
                            sendPacketGrowBlock(block.getLocation());
                    });
                }

                if (bPermissionEntity) {
                    Optional<Ageable> entityOptional = getRandomElement(getAgeableEntityInRange(location));

                    entityOptional.ifPresent(entity -> {
                        if (rnd.nextFloat() < .25) {
                            entity.setAge(entity.getAge() + 1);
                            entity.setBreed(true);
                        }

                        if (SneakGrow.showParticles)
                            sendPacketGrowEntity(entity.getLocation());
                    });
                }

                state.isSneaked = false;
            }
        }

        boolean isSneaking = player.isSneaking();
        if (state.isSneaking != isSneaking) {
            state.isSneaking = isSneaking;
            state.isSneaked = true;
        }
    }

    private <T> Optional<T> getRandomElement(List<T> elements) {
        if (elements.isEmpty())
            return Optional.empty();
        return Optional.ofNullable(elements.get(rnd.nextInt(elements.size() - 1)));
    }

    private void sendPacketGrowBlock(Location location) {
        Collection<Player> players = location.getNearbyPlayers(48);
        for (Player player : players)
            player.playEffect(location, Effect.VILLAGER_PLANT_GROW, Integer.valueOf(0));
    }

    private void sendPacketGrowEntity(Location location) {
        Collection<Player> players = location.getNearbyPlayers(48);
        for (Player player : players)
            player.spawnParticle(Particle.VILLAGER_HAPPY, location, 5, .2, .2, .2);
    }

    private List<Block> getAgeableBlockInRange(Location location) {
        World world = location.getWorld();
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        List<Block> list = new ArrayList<>();
        for (int x = -5; x <= 5; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -5; z <= 5; z++) {
                    Block block = world.getBlockAt(x + centerX, y + centerY, z + centerZ);
                    BlockData blockData = block.getBlockData();
                    if (SneakGrow.enableSaplings && blockData instanceof Sapling) {
                        list.add(block);
                    } else if (SneakGrow.enableCrops && blockData instanceof org.bukkit.block.data.Ageable) {
                        org.bukkit.block.data.Ageable data = (org.bukkit.block.data.Ageable) blockData;
                        if (data.getAge() < data.getMaximumAge())
                            list.add(block);
                    }
                }
        return list;
    }

    private List<org.bukkit.entity.Ageable> getAgeableEntityInRange(Location location) {
        return location.getNearbyLivingEntities(5).stream()
                .filter(e -> e instanceof org.bukkit.entity.Ageable)
                .map(org.bukkit.entity.Ageable.class::cast)
                .filter(e -> !e.isAdult() || !e.canBreed())
                .collect(Collectors.toList());
    }
}
