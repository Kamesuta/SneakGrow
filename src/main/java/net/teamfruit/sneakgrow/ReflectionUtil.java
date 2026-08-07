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
            nmsItemBoneMeal = getFirstNmsClass("net.minecraft.world.item", "ItemBoneMeal", "BoneMealItem", "ItemDye");
            nmsItemStack = getNmsClass("net.minecraft.world.item", "ItemStack");
            nmsWorld = getFirstNmsClass("net.minecraft.world.level", "World", "Level");
            nmsBlockPosition = getFirstNmsClass("net.minecraft.core", "BlockPosition", "BlockPos");
            nmsEnumDirection = getFirstNmsClass("net.minecraft.core", "EnumDirection", "Direction");

            craftItemStack = getCraftBukkitClass("inventory.CraftItemStack");
            craftWorld = getCraftBukkitClass("CraftWorld");

            nmsBlockPositionConstructor = nmsBlockPosition.getConstructor(int.class, int.class, int.class);

            nmsItemBoneMealApply = getFirstMethod(nmsItemBoneMeal,
                    new String[]{"a", "growCrop"}, nmsItemStack, nmsWorld, nmsBlockPosition);
            nmsItemBoneMealUnderwaterApply = getOptionalFirstMethod(nmsItemBoneMeal,
                    new String[]{"a", "growWaterPlant"}, nmsItemStack, nmsWorld, nmsBlockPosition, nmsEnumDirection);
            craftItemStackAsNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            craftWorldGetHandle = craftWorld.getMethod("getHandle", NO_ARGUMENTS);
        } catch (Exception e) {
            SneakGrow.log.log(Level.SEVERE,
                    "Error loading NMS Classes, are you using the right version?", e);
        }
    }

    public static Class<?> getNmsClass(String packagePath, String name) throws ClassNotFoundException {
        try {
            return Class.forName(packagePath + "." + name);
        } catch (ClassNotFoundException e) {
            return Class.forName(NMS_NAMESPACE + "." + VERSION + "." + name);
        }
    }

    private static Class<?> getFirstNmsClass(String packagePath, String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return getNmsClass(packagePath, name);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Method getFirstMethod(Class<?> owner, String[] names, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                return owner.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Method getOptionalFirstMethod(Class<?> owner, String[] names, Class<?>... parameterTypes) {
        try {
            return getFirstMethod(owner, names, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static boolean hasMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            owner.getMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static <T extends Enum<T>> T getFirstEnumValue(Class<T> enumClass, String... names) {
        for (String name : names) {
            try {
                return Enum.valueOf(enumClass, name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new IllegalArgumentException("No matching enum value found in " + enumClass.getName());
    }

    public static Class<?> getCraftBukkitClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(CRAFTBUKKIT_NAMESPACE + "." + name);
        } catch (ClassNotFoundException e) {
            return Class.forName(CRAFTBUKKIT_NAMESPACE + "." + VERSION + "." + name);
        }
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
            if (nmsItemBoneMealUnderwaterApply != null) {
                // 上面から水中の植物に骨粉を適用する
                Object direction = getFirstEnumValue(nmsEnumDirection.asSubclass(Enum.class), "UP");
                nmsItemBoneMealUnderwaterApply.invoke(
                        null, nmsItemStack, nmsWorld, nmsBlockPosition, direction);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            SneakGrow.log.log(Level.SEVERE, "Error applying bone meal!", e);
        }
    }

}
