package com.fluence.lite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Utilidades: buscar, abrir y clasificar apps. */
final class Apps {
    private Apps() {}

    // ---- Apps de los accesos directos ----
    static final String[] ZLINK = {"com.zjinnova.zlink", "com.zjinnova.zlink.carplay", "com.zjinnova.zlinkpro"};
    static final String[] ZLINK_HINTS = {"zlink", "carplay", "autokit", "carlink", "tlink"};
    static final String[] SPOTIFY = {"com.spotify.music", "com.spotify.lite"};
    static final String[] MAPS = {"com.waze", "com.google.android.apps.maps", "com.google.android.apps.mapslite"};
    static final String[] MAPS_HINTS = {"waze", "maps", "mapas", "navi", "sygic", "igo"};
    static final String[] RADIO_HINTS = {"radio", "fm"};

    /**
     * Apps que NO se deben desactivar ni cerrar: sistema de la radio (CANbus, MCU, Bluetooth,
     * cámara de retroceso, controles del volante), teclado, servicios de Google, ZLink, Spotify
     * y los launchers. Se comparan contra cada parte del nombre del paquete.
     */
    private static final Set<String> PROTECT_SEGMENTS = new HashSet<>();
    static {
        Collections.addAll(PROTECT_SEGMENTS,
                "systemui", "settings", "inputmethod", "latin", "keyboard", "gms", "gsf", "vending",
                "webview", "canbus", "can", "mcu", "bt", "bluetooth", "phone", "dialer", "telecom",
                "server", "service", "services", "ms", "factory", "ota", "update", "updater",
                "camera", "avm", "reverse", "backcar", "steer", "launcher", "zlink", "carplay",
                "autokit", "carlink", "spotify", "fluence", "syu", "microntek", "mtc",
                "providers", "packageinstaller", "permissioncontroller", "shell", "networkstack",
                "location", "nfc", "documentsui", "externalstorage", "media", "audio", "sound");
    }

    /** Apps de fábrica que se pueden desactivar sin afectar la radio (no se usan en el auto). */
    private static final Set<String> SAFE_BLOAT = new HashSet<>();
    static {
        Collections.addAll(SAFE_BLOAT,
                "com.android.browser", "com.android.email", "com.android.calendar",
                "com.android.music", "com.android.soundrecorder", "com.android.quicksearchbox",
                "com.android.calculator2", "com.android.deskclock", "com.android.gallery3d",
                "com.android.printspooler", "com.android.bips", "com.android.dreams.basic",
                "com.android.wallpaper.livepicker", "com.android.stk", "com.android.egg",
                "com.android.bookmarkprovider", "com.android.htmlviewer", "com.android.wallpapercropper",
                "com.google.android.apps.docs", "com.google.android.apps.photos",
                "com.google.android.gm", "com.google.android.videos", "com.google.android.music",
                "com.google.android.apps.tachyon", "com.google.android.apps.magazines",
                "com.google.android.apps.books", "com.google.android.talk", "com.google.android.feedback",
                "com.facebook.katana", "com.facebook.appmanager", "com.facebook.services",
                "com.facebook.system", "com.netflix.partner.activation", "com.qiyi.video",
                "com.estrongs.android.pop", "com.kugou.android", "cn.kuwo.player");
    }

    static final int KIND_SAFE = 0;        // sugerida para desactivar
    static final int KIND_USER = 1;        // instalada por el usuario
    static final int KIND_SYSTEM = 2;      // de fábrica, sin clasificar
    static final int KIND_PROTECTED = 3;   // no tocar

    static int classify(String pkg, boolean system) {
        if (SAFE_BLOAT.contains(pkg)) return KIND_SAFE;
        if (isProtected(pkg)) return KIND_PROTECTED;
        return system ? KIND_SYSTEM : KIND_USER;
    }

    static boolean isProtected(String pkg) {
        if (pkg.equals("android")) return true;
        if (SAFE_BLOAT.contains(pkg)) return false;
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String seg : p.split("\\.")) {
            if (PROTECT_SEGMENTS.contains(seg)) return true;
        }
        // Proveedores y servicios internos de Android
        return p.startsWith("com.android.providers") || p.startsWith("com.android.server")
                || p.startsWith("com.android.internal") || p.startsWith("com.qualcomm")
                || p.startsWith("com.mediatek") || p.startsWith("com.sprd") || p.startsWith("com.unisoc");
    }

    // ---- Buscar y abrir ----

    static boolean isInstalled(Context c, String pkg) {
        return pkg != null && c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    static List<ResolveInfo> launchables(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            return c.getPackageManager().queryIntentActivities(i, 0);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Primero los paquetes conocidos; si no, una app cuyo nombre contenga alguna pista. */
    static String find(Context c, String[] known, String[] hints) {
        if (known != null) for (String k : known) if (isInstalled(c, k)) return k;
        if (hints == null) return null;
        PackageManager pm = c.getPackageManager();
        for (ResolveInfo ri : launchables(c)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ROOT);
            String lp = pkg.toLowerCase(Locale.ROOT);
            for (String h : hints) {
                if (label.contains(h) || lp.contains(h)) return pkg;
            }
        }
        return null;
    }

    /** Abre (o trae al frente, sin reiniciar) una app. */
    static boolean launch(Context c, String pkg) {
        if (pkg == null) return false;
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            if (c instanceof Activity) ((Activity) c).overridePendingTransition(0, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Página de Ajustes de la app: ahí están DESACTIVAR / DESINSTALAR / FORZAR DETENCIÓN. */
    static void openDetails(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("lite", Context.MODE_PRIVATE);
    }
}
