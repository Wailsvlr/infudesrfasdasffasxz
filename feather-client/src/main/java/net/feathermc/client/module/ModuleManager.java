package net.feathermc.client.module;

import net.feathermc.client.module.hud.*;
import net.feathermc.client.module.movement.*;
import net.feathermc.client.module.render.*;
import net.feathermc.client.module.visual.*;
import net.feathermc.client.module.misc.*;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void registerAll() {
        // HUD modules
        register(new FPSDisplay());
        register(new CPSDisplay());
        register(new CoordinatesDisplay());
        register(new PingDisplay());
        register(new KeystrokesDisplay());
        register(new ArmorStatusDisplay());
        register(new PotionEffectsDisplay());
        register(new DirectionDisplay());
        register(new ReachDisplay());
        register(new ComboCounter());
        register(new ClockDisplay());
        register(new SaturationDisplay());
        register(new ServerAddressDisplay());

        // Movement modules
        register(new ToggleSprint());
        register(new ToggleSneak());

        // Render modules
        register(new Zoom());
        register(new Fullbright());
        register(new Freelook());
        register(new MotionBlur());
        register(new TimeChanger());
        register(new BlockOverlay());
        register(new ItemPhysics());

        // Visual modules
        register(new Crosshair());
        register(new HitColor());
        register(new NameTags());
        register(new Animations());
        register(new Scoreboard());
        register(new BossBar());
        register(new TabList());
        register(new ParticleMultiplier());

        // Misc modules
        register(new ScreenshotModule());
        register(new NickHider());
        register(new ChatShortcuts());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (clazz.isInstance(m)) return clazz.cast(m);
        }
        return null;
    }

    public Module getModule(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    public List<Module> getByCategory(Module.Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
    }
}
