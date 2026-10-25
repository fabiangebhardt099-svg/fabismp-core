package de.fabismp.core;

import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    private static final String[] SUF = {"", "K", "M", "B", "T"};

    public static String num(double v) {
        int i = 0;
        while (v >= 1000 && i < SUF.length - 1) { v /= 1000; i++; }
        String s = String.format(Locale.US, i == 0 ? "%.2f" : "%.1f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + SUF[i];
    }

    public static String money(double v) { return "$" + num(v); }

    /** Parst 1500, 1.5k, 2m, 3b. Gibt -1 bei ungueltig zurueck. */
    public static double parse(String s) {
        try {
            s = s.toLowerCase(Locale.ROOT).replace(",", ".");
            double m = 1;
            switch (s.charAt(s.length() - 1)) {
                case 'k' -> m = 1e3;
                case 'm' -> m = 1e6;
                case 'b' -> m = 1e9;
                case 't' -> m = 1e12;
                default -> {}
            }
            if (m != 1) s = s.substring(0, s.length() - 1);
            double v = Double.parseDouble(s) * m;
            return Double.isFinite(v) && v > 0 ? Math.round(v * 100) / 100.0 : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String time(long ticks) {
        long min = ticks / 20 / 60;
        return (min / 60) + "h " + (min % 60) + "m";
    }
}
