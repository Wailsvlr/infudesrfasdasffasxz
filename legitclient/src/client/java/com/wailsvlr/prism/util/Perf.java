package com.wailsvlr.prism.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tiny profiler: how long each module's tick and each part of the drawing takes (average and worst since the last
 * reset). Used by the tests and by the "Performance" line of the HUD info box.
 */
public final class Perf {
	/** {total ns, calls, worst ns} per key. */
	private static final Map<String, long[]> STATS = new ConcurrentHashMap<>();

	private Perf() { }

	public static long start() { return System.nanoTime(); }

	public static void end(String key, long t0) {
		long d = System.nanoTime() - t0;
		long[] s = STATS.computeIfAbsent(key, k -> new long[3]);
		synchronized (s) {
			s[0] += d;
			s[1]++;
			if (d > s[2]) s[2] = d;
		}
	}

	public static void reset() { STATS.clear(); }

	/** "key avg/worst ms" lines, the most expensive (by total time) first. */
	public static List<String> report() {
		List<Map.Entry<String, long[]>> es = new ArrayList<>(STATS.entrySet());
		es.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		List<String> out = new ArrayList<>();
		for (var e : es) {
			long[] s = e.getValue();
			if (s[1] == 0) continue;
			out.add(String.format(java.util.Locale.ROOT, "%-28s avg %6.3f ms  worst %7.3f ms  calls %d", e.getKey(), s[0] / 1e6 / s[1], s[2] / 1e6, s[1]));
		}
		return out;
	}

	/** Total time spent under {@code prefix} keys, in ms per call of the busiest one (for the HUD). */
	public static double totalAvgMs(String prefix) {
		double t = 0;
		for (var e : STATS.entrySet()) {
			if (!e.getKey().startsWith(prefix)) continue;
			long[] s = e.getValue();
			if (s[1] > 0) t += s[0] / 1e6 / s[1];
		}
		return t;
	}
}
