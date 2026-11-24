package com.fluence.gps;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/** Guarda en el equipo: lugares, última ubicación y el recorrido. */
final class Store {
    private Store() {}

    static final class Place {
        String name;
        double lat, lon;
        Place(String name, double lat, double lon) { this.name = name; this.lat = lat; this.lon = lon; }
    }

    /** Recorrido en memoria; sobrevive al girar la pantalla. */
    static final ArrayList<double[]> trail = new ArrayList<>();
    static boolean trailLoaded = false;
    static final int MAX_TRAIL = 5000;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gps", Context.MODE_PRIVATE);
    }

    // ---------- Lugares ----------

    static List<Place> places(Context c) {
        ArrayList<Place> out = new ArrayList<>();
        String raw = prefs(c).getString("places", "");
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t");
            if (p.length < 3) continue;
            try { out.add(new Place(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]))); }
            catch (NumberFormatException ignored) { }
        }
        return out;
    }

    static void savePlaces(Context c, List<Place> list) {
        StringBuilder sb = new StringBuilder();
        for (Place p : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.name.replace('\t', ' ').replace('\n', ' ')).append('\t').append(p.lat).append('\t').append(p.lon);
        }
        prefs(c).edit().putString("places", sb.toString()).apply();
    }

    static void addPlace(Context c, Place p) {
        List<Place> list = places(c);
        list.add(p);
        savePlaces(c, list);
    }

    static void removePlace(Context c, int index) {
        List<Place> list = places(c);
        if (index >= 0 && index < list.size()) list.remove(index);
        savePlaces(c, list);
        int goal = goal(c);
        if (goal == index) setGoal(c, -1);
        else if (goal > index) setGoal(c, goal - 1);
    }

    static int goal(Context c) { return prefs(c).getInt("goal", -1); }

    static void setGoal(Context c, int i) { prefs(c).edit().putInt("goal", i).apply(); }

    static Place goalPlace(Context c) {
        int i = goal(c);
        List<Place> list = places(c);
        return (i >= 0 && i < list.size()) ? list.get(i) : null;
    }

    // ---------- Última ubicación ----------

    static void saveLast(Context c, double lat, double lon, long time) {
        prefs(c).edit()
                .putString("last_lat", Double.toString(lat))
                .putString("last_lon", Double.toString(lon))
                .putLong("last_time", time)
                .apply();
    }

    /** {lat, lon, tiempo} o null. */
    static double[] last(Context c) {
        SharedPreferences p = prefs(c);
        String a = p.getString("last_lat", null), b = p.getString("last_lon", null);
        if (a == null || b == null) return null;
        try { return new double[]{Double.parseDouble(a), Double.parseDouble(b), p.getLong("last_time", 0L)}; }
        catch (NumberFormatException e) { return null; }
    }

    // ---------- Recorrido ----------

    private static File trailFile(Context c) { return new File(c.getFilesDir(), "recorrido.txt"); }

    static void loadTrail(Context c) {
        if (trailLoaded) return;
        trailLoaded = true;
        trail.clear();
        File f = trailFile(c);
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 2) continue;
                try { trail.add(new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1])}); }
                catch (NumberFormatException ignored) { }
            }
        } catch (Exception ignored) { }
        while (trail.size() > MAX_TRAIL) trail.remove(0);
    }

    static void saveTrail(Context c) {
        try (FileWriter w = new FileWriter(trailFile(c), false)) {
            for (double[] p : trail) w.write(p[0] + "," + p[1] + "\n");
        } catch (Exception ignored) { }
    }

    static void clearTrail(Context c) {
        trail.clear();
        trailFile(c).delete();
    }
}
