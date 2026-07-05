package com.revenueaccount.app.utils;

/** Safe number parsing — kabhi crash nahi karta, galat input par 0 deta hai */
public class NumUtils {
    public static double parseDouble(String s) {
        if (s == null) return 0;
        s = s.trim().replace(",", "."); // kuch keyboards/locales comma use karte hain
        if (s.isEmpty()) return 0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    public static int parseInt(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
