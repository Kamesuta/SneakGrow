package net.teamfruit.sneakgrow;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SneakHandler implements Listener {
    private Map<String, PlayerState> states = new HashMap<>();

    private static final ItemStack boneMeal = new ItemStack(Material.BONE_MEAL);
    private static final Object nmsBoneMeal = ReflectionUtil.itemStackAsNmsCopy(boneMeal);

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
        if (!player.hasPermission("sneakgrow"))
            return;

        PlayerState state = states.computeIfAbsent(player.getName(), e -> new PlayerState());

        {
            int ticksNow = Bukkit.getCurrentTick();
            int ticksSinceLastCheck = ticksNow - state.ticksLastCheck;
            if (ticksSinceLastCheck >= SneakGrow.cooldown) {
                state.ticksLastCheck = ticksNow;

                World world = player.getWorld();
                Object nmsWorld = ReflectionUtil.craftWorldGetHandle(world);
                Location location = player.getLocation();
                List<Block> coords = getNearestBlocks(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());

                if (state.isSneaked) {
                    if (coords.size() > 0) {

                        Collections.shuffle(coords);

                        for (Block block : coords) {
                            if (ThreadLocalRandom.current().nextFloat() < .45) {
                                Object nmsBlockPosition = ReflectionUtil.constructBlockPosition(block.getX(), block.getY(), block.getZ());
                                ReflectionUtil.applyBoneMeal(nmsBoneMeal, nmsWorld, nmsBlockPosition);
                            }

                            if (SneakGrow.showParticles)
                                sendPacket(block);

                            break;
                        }
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
    }

    private void sendPacket(Block block) {
        Location location = block.getLocation();
        Collection<Player> players = location.getNearbyPlayers(48);
        for (Player player : players)
            player.playEffect(location, Effect.VILLAGER_PLANT_GROW, Integer.valueOf(0));
    }

    private List<Block> getNearestBlocks(World world, int centerX, int centerY, int centerZ) {
        List<Block> list = new ArrayList<>();
        for (int x = -5; x <= 5; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -5; z <= 5; z++) {
                    Block block = world.getBlockAt(x + centerX, y + centerY, z + centerZ);
                    BlockData blockData = block.getBlockData();
                    if (SneakGrow.enableSaplings && blockData instanceof Sapling) {
                        list.add(block);
                    } else if (SneakGrow.enableCrops && blockData instanceof Ageable) {
                        Ageable data = (Ageable) blockData;
                        if (data.getAge() < data.getMaximumAge())
                            list.add(block);
                    }
                }
        return list;
    }
}
