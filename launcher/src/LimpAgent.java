import java.lang.instrument.Instrumentation;
import java.io.*;
import java.nio.file.*;

/**
 * Java Agent that gets injected into a running Minecraft JVM via Attach API.
 * This is how Vape V4 / Prestige Client work.
 *
 * agentmain is called when the agent is loaded into an already-running JVM.
 * args = path to mods directory
 */
public class LimpAgent {

    private static final String MOD_JAR = "legitclient-1.0.0.jar";

    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[LIMP Client] Agent injected into running Minecraft!");

        // args format: "modsDir|modSourcePath"
        if (args != null && !args.isEmpty()) {
            String[] parts = args.split("\\|", 2);
            String modsDir = parts[0];
            String modSource = parts.length > 1 ? parts[1] : null;

            try {
                Path mods = Paths.get(modsDir);
                Files.createDirectories(mods);

                if (modSource != null) {
                    Path src = Paths.get(modSource);
                    if (Files.exists(src)) {
                        Files.copy(src, mods.resolve(MOD_JAR), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("[LIMP Client] Mod copied to " + mods);
                    }
                }
            } catch (Exception e) {
                System.err.println("[LIMP Client] Failed to copy mod: " + e);
            }
        }

        // Try to show in-game notification via Minecraft's chat
        try {
            showInGameMessage();
        } catch (Exception e) {
            System.err.println("[LIMP Client] Could not show in-game message: " + e);
        }
    }

    private static void showInGameMessage() throws Exception {
        // Find the correct classloader (Fabric/Knot uses its own)
        ClassLoader mcLoader = findMinecraftClassLoader();
        if (mcLoader == null) {
            System.err.println("[LIMP Client] Could not find Minecraft classloader");
            return;
        }

        // Load Minecraft class using its own classloader
        Class<?> mcClass = mcLoader.loadClass("net.minecraft.client.Minecraft");

        // Get Minecraft instance - try common method names (Mojang mappings)
        Object mc = null;
        for (String methodName : new String[]{"getInstance", "method_1551", "m_91087_"}) {
            try {
                mc = mcClass.getMethod(methodName).invoke(null);
                if (mc != null) break;
            } catch (NoSuchMethodException ignored) {}
        }

        if (mc == null) {
            // Try field access
            for (java.lang.reflect.Field f : mcClass.getDeclaredFields()) {
                if (f.getType() == mcClass) {
                    f.setAccessible(true);
                    mc = f.get(null);
                    if (mc != null) break;
                }
            }
        }

        if (mc == null) {
            System.err.println("[LIMP Client] Could not get Minecraft instance");
            return;
        }

        // Schedule on main thread
        final Object mcInst = mc;
        Runnable chatTask = new Runnable() {
            public void run() {
                try {
                    sendChatMessage(mcInst, mcLoader);
                } catch (Exception e) {
                    System.err.println("[LIMP Client] Chat message failed: " + e);
                }
            }
        };

        // Use Minecraft.execute(Runnable) to run on render thread
        try {
            mcClass.getMethod("execute", Runnable.class).invoke(mc, chatTask);
        } catch (NoSuchMethodException e) {
            // Try alternative method names
            for (String m : new String[]{"method_18858", "m_18707_", "tell"}) {
                try {
                    mcClass.getMethod(m, Runnable.class).invoke(mc, chatTask);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }
            // Fallback: just run directly (risky but might work)
            chatTask.run();
        }
    }

    private static void sendChatMessage(Object mc, ClassLoader loader) throws Exception {
        // Get gui field
        Object gui = getField(mc, "gui", "field_1705", "f_91065_");
        if (gui == null) return;

        // Get chat component
        Object chat = getField(gui, "chat", "field_2013", "f_93823_");
        if (chat == null) {
            // Try getChat() method
            for (String m : new String[]{"getChat", "method_2162", "m_93820_"}) {
                try {
                    chat = gui.getClass().getMethod(m).invoke(gui);
                    if (chat != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
        }
        if (chat == null) return;

        // Create Component.literal("text")
        Class<?> componentClass = null;
        for (String cn : new String[]{"net.minecraft.network.chat.Component", "net.minecraft.class_2561"}) {
            try { componentClass = loader.loadClass(cn); break; } catch (ClassNotFoundException ignored) {}
        }
        if (componentClass == null) return;

        Object message = null;
        for (String m : new String[]{"literal", "method_43470", "m_237113_"}) {
            try {
                message = componentClass.getMethod(m, String.class)
                        .invoke(null, "§d§l[LIMP Client]§r §7Injected successfully! Restart for full features.");
                if (message != null) break;
            } catch (NoSuchMethodException ignored) {}
        }
        if (message == null) return;

        // addMessage
        for (String m : new String[]{"addMessage", "method_932", "m_93791_"}) {
            try {
                chat.getClass().getMethod(m, componentClass).invoke(chat, message);
                System.out.println("[LIMP Client] Chat message sent!");
                return;
            } catch (NoSuchMethodException ignored) {}
        }
    }

    private static Object getField(Object obj, String... names) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                for (String name : names) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        try { return f.get(obj); } catch (Exception ignored) {}
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static ClassLoader findMinecraftClassLoader() {
        // Try thread context classloaders
        Thread[] threads = new Thread[Thread.activeCount() + 10];
        int count = Thread.enumerate(threads);
        for (int i = 0; i < count; i++) {
            Thread t = threads[i];
            if (t == null) continue;
            String name = t.getName().toLowerCase();
            if (name.contains("render") || name.contains("main") || name.contains("client")) {
                ClassLoader cl = t.getContextClassLoader();
                if (cl != null) {
                    try {
                        cl.loadClass("net.minecraft.client.Minecraft");
                        return cl;
                    } catch (ClassNotFoundException ignored) {}
                }
            }
        }

        // Try all thread classloaders
        for (int i = 0; i < count; i++) {
            if (threads[i] == null) continue;
            ClassLoader cl = threads[i].getContextClassLoader();
            if (cl != null) {
                try {
                    cl.loadClass("net.minecraft.client.Minecraft");
                    return cl;
                } catch (ClassNotFoundException ignored) {}
            }
        }

        // Last resort: try system classloader and its parents
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            try {
                cl.loadClass("net.minecraft.client.Minecraft");
                return cl;
            } catch (ClassNotFoundException ignored) {}
            cl = cl.getParent();
        }

        return null;
    }
}
