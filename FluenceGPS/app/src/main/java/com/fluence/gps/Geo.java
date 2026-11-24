package com.fluence.gps;

import java.util.Locale;

/** Cálculos y formatos de coordenadas. Todo local, sin Internet. */
final class Geo {
    private Geo() {}

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
    private static final String[] DIRS_LONG = {"norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"};

    /** Distancia en metros (fórmula del haversine). */
    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Rumbo de 1 hacia 2, en grados (0 = norte, 90 = este). */
    static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    static String dir(double deg) {
        return DIRS[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8];
    }

    static String dirLong(double deg) {
        return DIRS_LONG[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8];
    }

    static String dist(double m) {
        if (m < 1000) return Math.round(m) + " m";
        if (m < 10000) return String.format(Locale.US, "%.1f km", m / 1000);
        return Math.round(m / 1000) + " km";
    }

    static String dec(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    /** 12°02'46.9"S */
    static String dms(double v, boolean lat) {
        String h = lat ? (v < 0 ? "S" : "N") : (v < 0 ? "O" : "E");
        v = Math.abs(v);
        int d = (int) v;
        double minF = (v - d) * 60;
        int m = (int) minF;
        double s = (minF - m) * 60;
        if (s >= 59.95) { s = 0; m++; }
        if (m >= 60) { m = 0; d++; }
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", d, m, s, h);
    }

    static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 5) return "ahora";
        if (s < 60) return "hace " + s + " s";
        long m = s / 60;
        if (m < 60) return "hace " + m + " min";
        long h = m / 60;
        if (h < 48) return "hace " + h + " h";
        return "hace " + (h / 24) + " días";
    }
}
