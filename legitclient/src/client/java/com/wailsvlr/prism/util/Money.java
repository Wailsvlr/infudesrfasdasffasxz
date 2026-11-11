package com.wailsvlr.prism.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads amounts the way servers print them ("$80.4M", "$12,345", "5.5k", "Price: $1.2B") and prints them back short. */
public final class Money {
	private static final Pattern DOLLAR = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTt])?(?![A-Za-z])");
	private static final Pattern PLAIN = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KkMmBbTt])?(?![A-Za-z])");

	private Money() { }

	/** The first "$" amount in the text, or null. */
	public static Double dollars(String text) { return find(DOLLAR, text); }

	/** The first amount in the text, with or without "$" (for typed values like "5m"), or null. */
	public static Double parse(String text) {
		Double d = dollars(text);
		return d != null ? d : find(PLAIN, text);
	}

	private static Double find(Pattern p, String text) {
		if (text == null) return null;
		Matcher m = p.matcher(text);
		if (!m.find()) return null;
		try {
			double v = Double.parseDouble(m.group(1).replace(",", ""));
			String unit = m.group(2) == null ? "" : m.group(2).toUpperCase(Locale.ROOT);
			return v * switch (unit) {
				case "K" -> 1e3;
				case "M" -> 1e6;
				case "B" -> 1e9;
				case "T" -> 1e12;
				default -> 1;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 1234 -> "1.2K", 80400000 -> "80.4M", 950 -> "950". */
	public static String format(double v) {
		double a = Math.abs(v);
		String sign = v < 0 ? "-" : "";
		if (a >= 1e12) return sign + trim(a / 1e12) + "T";
		if (a >= 1e9) return sign + trim(a / 1e9) + "B";
		if (a >= 1e6) return sign + trim(a / 1e6) + "M";
		if (a >= 1e3) return sign + trim(a / 1e3) + "K";
		return sign + (a == Math.rint(a) ? String.valueOf((long) a) : String.format(Locale.ROOT, "%.2f", a));
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, v >= 100 ? "%.0f" : v >= 10 ? "%.1f" : "%.2f", v);
		if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}
}
