package dev.donut.client.module;

import dev.donut.client.module.combat.*;
import dev.donut.client.module.movement.*;
import dev.donut.client.module.network.*;
import dev.donut.client.module.player.*;
import dev.donut.client.module.render.*;
import dev.donut.client.module.utility.*;
import dev.donut.client.module.world.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void init() {
        register(new AimAssist());
        register(new Triggerbot());
        register(new KillAura());
        register(new KeepSprint());
        register(new ShieldBreaker());
        register(new Velocity());

        register(new ESP());
        register(new Tracers());
        register(new NameTags());
        register(new ItemESP());
        register(new FullBright());
        register(new StorageESP());
        register(new Trajectories());
        register(new Indicators());

        register(new Sprint());
        register(new Speed());
        register(new Fly());
        register(new SafeWalk());

        register(new AutoTotem());
        register(new ChestStealer());
        register(new FastPlace());
        register(new AntiAFK());
        register(new Scaffold());
        register(new AutoArmor());
        register(new InvCleaner());

        register(new AutoPearl());
        register(new HitSwap());
        register(new Panic());
        register(new PearlCatch());
        register(new SpeedLeap());

        register(new AutoFish());
        register(new AutoTool());
        register(new Freecam());
        register(new HitBoxes());

        register(new BackTrack());
        register(new Blink());
        register(new FakeLag());
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() { return modules; }

    public List<Module> getByCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        return (T) modules.stream().filter(m -> m.getClass() == clazz).findFirst().orElse(null);
    }

    public Module getByName(String name) {
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
