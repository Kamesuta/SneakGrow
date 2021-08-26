package net.teamfruit.sneakgrow;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

public class ReflectionUtil {

    private static Class<?> nmsItemBoneMeal;
    private static Class<?> nmsItemStack;
    private static Class<?> nmsWorld;
    private static Class<?> nmsBlockPosition;
    private static Class<?> nmsEnumDirection;

    private static Class<?> craftItemStack;
    private static Class<?> craftWorld;

    private static Constructor<?> nmsBlockPositionConstructor;

    private static Method nmsItemBoneMealApply;
    private static Method nmsItemBoneMealUnderwaterApply;
    private static Method craftItemStackAsNmsCopy;
    private static Method craftWorldGetHandle;

    private static final String NMS_NAMESPACE = "net.minecraft.server";
    private static final String CRAFTBUKKIT_NAMESPACE = "org.bukkit.craftbukkit";

    private static final String VERSION;

    private static final Class[] NO_ARGUMENTS = new Class[0];

    static {
        String path = Bukkit.getServer().getClass().getPackage().getName();
        VERSION = path.substring(path.lastIndexOf('.') + 1);

        try {
            nmsItemBoneMeal = Class.forName("net.minecraft.world.item.ItemBoneMeal");
            nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");
            nmsWorld = Class.forName("net.minecraft.world.level.World");
            nmsBlockPosition = Class.forName("net.minecraft.core.BlockPosition");
            nmsEnumDirection = Class.forName("net.minecraft.core.EnumDirection");

            craftItemStack = getCraftBukkitClass("inventory.CraftItemStack");
            craftWorld = getCraftBukkitClass("CraftWorld");

            nmsBlockPositionConstructor = nmsBlockPosition.getConstructor(int.class, int.class, int.class);

            nmsItemBoneMealApply = nmsItemBoneMeal.getMethod("a", nmsItemStack, nmsWorld, nmsBlockPosition);
            nmsItemBoneMealUnderwaterApply = nmsItemBoneMeal.getMethod("a", nmsItemStack, nmsWorld, nmsBlockPosition, nmsEnumDirection);
            craftItemStackAsNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            craftWorldGetHandle = craftWorld.getMethod("getHandle", NO_ARGUMENTS);
        } catch (Exception e) {
            SneakGrow.log.log(Level.SEVERE,
                    "Error loading NMS Classes, are you using the right version?", e);
        }
    }

    @Deprecated
    public static Class<?> getNmsClass(String name) throws ClassNotFoundException {
        return Class.forName(NMS_NAMESPACE + "." + VERSION + "." + name);
    }

    public static Class<?> getCraftBukkitClass(String name) throws ClassNotFoundException {
        return Class.forName(CRAFTBUKKIT_NAMESPACE + "." + VERSION + "." + name);
    }

    public static Object itemStackAsNmsCopy(ItemStack itemStack) {
        try {
            return craftItemStackAsNmsCopy.invoke(null, itemStack);
        } catch (IllegalAccessException | InvocationTargetException e) {
            SneakGrow.log.log(Level.SEVERE, "Error creating NMS ItemStack", e);
        }
        return null;
    }

    public static Object craftWorldGetHandle(World world) {
        try {
            return craftWorldGetHandle.invoke(world);
        } catch (IllegalAccessException | InvocationTargetException e) {
            SneakGrow.log.log(Level.SEVERE, "Error getting NMS World", e);
        }
        return null;
    }

    public static Object constructBlockPosition(int x, int y, int z) {
        try {
            return nmsBlockPositionConstructor.newInstance(x, y, z);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            SneakGrow.log.log(Level.SEVERE, "Error constructing Block Position", e);
        }
        return null;
    }

    public static void applyBoneMeal(Object nmsItemStack, Object nmsWorld, Object nmsBlockPosition) {
        try {
            nmsItemBoneMealApply.invoke(null, nmsItemStack, nmsWorld, nmsBlockPosition);
            for (Object constant : nmsEnumDirection.getEnumConstants()) {
                nmsItemBoneMealUnderwaterApply.invoke(null, nmsItemStack, nmsWorld, nmsBlockPosition, constant);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            SneakGrow.log.log(Level.SEVERE, "Error applying bone meal!", e);
        }
    }

}
