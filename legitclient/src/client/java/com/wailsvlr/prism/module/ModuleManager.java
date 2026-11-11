package com.wailsvlr.prism.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public <M extends Module> M register(M module) {
		modules.add(module);
		return module;
	}

	public List<Module> all() { return Collections.unmodifiableList(modules); }

	public List<Module> in(Category category) {
		List<Module> out = new ArrayList<>();
		for (Module m : modules) if (m.getCategory() == category) out.add(m);
		return out;
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		for (Module m : modules) if (type.isInstance(m)) return (M) m;
		throw new IllegalArgumentException("No module " + type.getSimpleName());
	}

	public void tick() {
		for (Module m : modules) {
			if (!m.isEnabled()) continue;
			long t = com.wailsvlr.prism.util.Perf.start();
			m.onTick();
			com.wailsvlr.prism.util.Perf.end("tick " + m.getName(), t);
		}
	}

	/** Toggles every module bound to {@code key}. */
	public void onKey(int key) {
		for (Module m : modules) if (m.getKey() == key) m.toggle();
	}
}
