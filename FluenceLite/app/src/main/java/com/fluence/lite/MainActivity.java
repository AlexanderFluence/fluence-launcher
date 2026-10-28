package com.fluence.lite;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pantalla de inicio liviana: sin fondo animado, sin mapa, sin animaciones.
 * 4 accesos configurables (mantén presionado para cambiar la app) + Apps + Optimizar.
 */
public class MainActivity extends Activity {

    static final int SLOTS = 4;
    static final String[] SLOT_NAMES = {"CarPlay · AA", "Spotify", "Mapas", "Radio"};

    private final ImageView[] icons = new ImageView[SLOTS];
    private final TextView[] labels = new TextView[SLOTS];
    private final String[] pkgs = new String[SLOTS];
    private TextView ram;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        resolveAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si una app de los accesos se desinstaló, se vuelve a buscar
        for (int i = 0; i < SLOTS; i++) {
            if (pkgs[i] == null || !Apps.isInstalled(this, pkgs[i])) {
                resolveAll();
                break;
            }
        }
        updateRam();
    }

    @Override
    public void onBackPressed() {
        // En el inicio, "atrás" no hace nada
    }

    // ---------- Interfaz ----------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = dp(14);
        root.setPadding(pad, dp(8), pad, pad);

        // Barra superior: hora, fecha, RAM libre, ajustes
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        top.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d 'de' MMMM");
        date.setFormat24Hour("EEEE d 'de' MMMM");
        date.setTextColor(0xFF8A94A3);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setPadding(dp(16), 0, 0, 0);
        top.addView(date);

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ram = new TextView(this);
        ram.setTextColor(0xFF8A94A3);
        ram.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ram.setPadding(dp(12), dp(8), dp(12), dp(8));
        ram.setBackgroundResource(R.drawable.btn_bg);
        ram.setOnClickListener(v -> startActivity(new Intent(this, OptimizeActivity.class)));
        top.addView(ram);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setPadding(dp(10), dp(10), dp(10), dp(10));
        gear.setBackgroundResource(R.drawable.btn_bg);
        gear.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) { }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(48), dp(48));
        glp.leftMargin = dp(10);
        top.addView(gear, glp);

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Cuadrícula 3 x 2
        LinearLayout row1 = row();
        LinearLayout row2 = row();
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            View t = tile(slot);
            t.setOnClickListener(v -> openSlot(slot));
            t.setOnLongClickListener(v -> {
                pick(slot);
                return true;
            });
            (i < 3 ? row1 : row2).addView(t, tileLp());
        }

        View apps = fixedTile(R.drawable.ic_apps, "Apps");
        apps.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        row2.addView(apps, tileLp());

        View opt = fixedTile(R.drawable.ic_bolt, "Optimizar");
        opt.setOnClickListener(v -> startActivity(new Intent(this, OptimizeActivity.class)));
        row2.addView(opt, tileLp());

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rlp.topMargin = dp(10);
        root.addView(row1, rlp);
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rlp2.topMargin = dp(10);
        root.addView(row2, rlp2);

        TextView hint = new TextView(this);
        hint.setText("Mantén presionado un acceso para cambiar su app");
        hint.setTextColor(0xFF4A525E);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, 0);
        root.addView(hint);
        return root;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(5);
        return lp;
    }

    private LinearLayout tileBase() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.tile_bg);
        return t;
    }

    private View tile(int slot) {
        LinearLayout t = tileBase();
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(iv, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView tv = label();
        t.addView(tv);
        icons[slot] = iv;
        labels[slot] = tv;
        return t;
    }

    private View fixedTile(int icon, String text) {
        LinearLayout t = tileBase();
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        t.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView tv = label();
        tv.setText(text);
        t.addView(tv);
        return t;
    }

    private TextView label() {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setPadding(dp(6), dp(10), dp(6), 0);
        return tv;
    }

    // ---------- Accesos ----------

    private String findDefault(int slot) {
        switch (slot) {
            case 0: return Apps.find(this, Apps.ZLINK, Apps.ZLINK_HINTS);
            case 1: return Apps.find(this, Apps.SPOTIFY, null);
            case 2: return Apps.find(this, Apps.MAPS, Apps.MAPS_HINTS);
            default: return Apps.find(this, null, Apps.RADIO_HINTS);
        }
    }

    private void resolveAll() {
        PackageManager pm = getPackageManager();
        for (int i = 0; i < SLOTS; i++) {
            String saved = Apps.prefs(this).getString("slot_" + i, null);
            String pkg = (saved != null && Apps.isInstalled(this, saved)) ? saved : findDefault(i);
            pkgs[i] = pkg;
            if (pkg != null) {
                try {
                    icons[i].setImageDrawable(pm.getApplicationIcon(pkg));
                    icons[i].setAlpha(1f);
                } catch (Exception e) {
                    icons[i].setImageResource(R.drawable.ic_add);
                }
                labels[i].setText(i == 0 ? SLOT_NAMES[0] : String.valueOf(appLabel(pm, pkg, SLOT_NAMES[i])));
            } else {
                icons[i].setImageResource(R.drawable.ic_add);
                icons[i].setAlpha(0.6f);
                labels[i].setText(SLOT_NAMES[i]);
            }
        }
    }

    private CharSequence appLabel(PackageManager pm, String pkg, String fallback) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (Exception e) {
            return fallback;
        }
    }

    private void openSlot(int slot) {
        if (!Apps.launch(this, pkgs[slot])) {
            Toast.makeText(this, "Elige la app para " + SLOT_NAMES[slot], Toast.LENGTH_SHORT).show();
            pick(slot);
        }
    }

    private void pick(int slot) {
        startActivityForResult(new Intent(this, AppsActivity.class)
                .putExtra(AppsActivity.EXTRA_SLOT, slot)
                .putExtra(AppsActivity.EXTRA_TITLE, "Elige la app para " + SLOT_NAMES[slot]), 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) resolveAll();
    }

    // ---------- RAM ----------

    private void updateRam() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long free = mi.availMem / (1024 * 1024);
        long total = mi.totalMem / (1024 * 1024);
        ram.setText("RAM libre " + free + " / " + total + " MB");
        ram.setTextColor(free * 100 / Math.max(total, 1) < 20 ? 0xFFFF8A3D : 0xFF8A94A3);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
