package com.fluence.lite;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/**
 * Optimizar: muestra la RAM libre, cierra apps en segundo plano y lista las apps que se
 * pueden desactivar (sugeridas, instaladas, de fábrica) marcando en rojo las que NO se tocan.
 */
public class OptimizeActivity extends Activity {

    private static final class Row {
        String label;
        String pkg;
        Drawable icon;
        int kind;
        boolean enabled;
        boolean autostart;
        boolean system;
    }

    private final List<Row> all = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private boolean showSystem = false;

    private TextView ram;
    private TextView status;
    private TextView toggle;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(10), dp(14), 0);

        // Barra superior
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(button("‹  Volver", v -> finish()));
        TextView title = new TextView(this);
        title.setText("Optimizar radio");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ram = new TextView(this);
        ram.setTextColor(0xFF2EC4FF);
        ram.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bar.addView(ram);
        root.addView(bar);

        // Acciones
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(button("⚡ Liberar memoria ahora", v -> freeMemory()));
        actions.addView(gap());
        actions.addView(button("Quitar animaciones", v -> animationsHelp()));
        actions.addView(gap());
        toggle = button("Mostrar apps del sistema", v -> {
            showSystem = !showSystem;
            toggle.setText(showSystem ? "Ocultar apps del sistema" : "Mostrar apps del sistema");
            filter();
        });
        actions.addView(toggle);
        root.addView(actions);

        TextView help = new TextView(this);
        help.setText("Toca una app → se abre su página en Ajustes → pulsa DESACTIVAR (apps de fábrica) "
                + "o DESINSTALAR (apps que instalaste). Desactivar se puede deshacer desde Ajustes → Apps.");
        help.setTextColor(0xFF8A94A3);
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        help.setPadding(dp(2), dp(10), 0, 0);
        root.addView(help);

        status = new TextView(this);
        status.setText("Revisando apps…");
        status.setTextColor(0xFF4A525E);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        status.setPadding(dp(2), dp(6), 0, dp(4));
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0xFF161A20));
        list.setDividerHeight(dp(1));
        list.setSelector(new ColorDrawable(0x302EC4FF));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Row r = shown.get(pos);
            if (r.kind == Apps.KIND_PROTECTED && r.enabled) {
                Toast.makeText(this, "Mejor no tocar " + r.label + ": la radio la necesita", Toast.LENGTH_LONG).show();
            } else {
                Apps.openDetails(this, r.pkg);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRam();
        load(); // se recarga al volver de Ajustes, para ver lo que desactivaste
    }

    // ---------- Lista de apps ----------

    private void load() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> infos;
            try {
                infos = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
            } catch (Exception e) {
                infos = new ArrayList<>();
            }
            List<Row> rows = new ArrayList<>();
            for (ApplicationInfo ai : infos) {
                if (ai.packageName.equals(getPackageName())) continue;
                Row r = new Row();
                r.pkg = ai.packageName;
                r.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                r.enabled = ai.enabled;
                r.kind = Apps.classify(r.pkg, r.system);
                // Las apps de sistema sin ícono de lanzador son servicios internos: no se listan
                if (r.system && r.enabled && r.kind != Apps.KIND_SAFE && pm.getLaunchIntentForPackage(r.pkg) == null) continue;
                r.label = String.valueOf(ai.loadLabel(pm));
                try {
                    r.icon = ai.loadIcon(pm);
                } catch (Exception ignored) { }
                r.autostart = startsOnBoot(pm, r.pkg);
                rows.add(r);
            }
            Collator col = Collator.getInstance();
            rows.sort((a, b) -> {
                int ra = rank(a), rb = rank(b);
                if (ra != rb) return ra - rb;
                return col.compare(a.label, b.label);
            });
            runOnUiThread(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(rows);
                filter();
            });
        }).start();
    }

    private static int rank(Row r) {
        if (!r.enabled) return 9;
        switch (r.kind) {
            case Apps.KIND_SAFE: return 0;
            case Apps.KIND_USER: return r.autostart ? 1 : 2;
            case Apps.KIND_SYSTEM: return 3;
            default: return 4;
        }
    }

    private static boolean startsOnBoot(PackageManager pm, String pkg) {
        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions == null) return false;
            for (String p : pi.requestedPermissions) {
                if (Manifest.permission.RECEIVE_BOOT_COMPLETED.equals(p)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void filter() {
        shown.clear();
        int safe = 0, auto = 0;
        for (Row r : all) {
            boolean visible = showSystem || !r.enabled || r.kind == Apps.KIND_SAFE || r.kind == Apps.KIND_USER;
            if (!visible) continue;
            shown.add(r);
            if (r.enabled && r.kind == Apps.KIND_SAFE) safe++;
            if (r.enabled && r.autostart && r.kind != Apps.KIND_PROTECTED) auto++;
        }
        status.setText(shown.size() + " apps · " + safe + " sugeridas para desactivar · "
                + auto + " se inician solas al encender");
        adapter.notifyDataSetChanged();
    }

    // ---------- Memoria ----------

    private long availMb() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    private void updateRam() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        ram.setText("RAM libre: " + (mi.availMem >> 20) + " de " + (mi.totalMem >> 20) + " MB");
    }

    /** Cierra procesos en segundo plano, sin tocar las apps protegidas (ZLink, Spotify, CANbus…). */
    private void freeMemory() {
        Toast.makeText(this, "Liberando memoria…", Toast.LENGTH_SHORT).show();
        final long before = availMb();
        new Thread(() -> {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            PackageManager pm = getPackageManager();
            int n = 0;
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                String pkg = ai.packageName;
                if (pkg.equals(getPackageName()) || Apps.isProtected(pkg)) continue;
                try {
                    am.killBackgroundProcesses(pkg);
                    n++;
                } catch (Exception ignored) { }
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) { }
            final long gained = Math.max(0, availMb() - before);
            final int count = n;
            runOnUiThread(() -> {
                updateRam();
                Toast.makeText(this, "Listo: +" + gained + " MB libres (" + count + " apps revisadas)",
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void animationsHelp() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Quitar animaciones")
                .setMessage("Hace que todo se sienta más rápido.\n\n"
                        + "En Opciones de desarrollador busca:\n"
                        + "• Escala de animación de ventana\n"
                        + "• Escala de animación de transición\n"
                        + "• Escala de duración de animador\n\n"
                        + "y ponlas en «Animación desactivada» (o 0.5x).\n\n"
                        + "Si no aparecen las Opciones de desarrollador: Ajustes → Acerca de → "
                        + "toca 7 veces «Número de compilación». Algunas radios piden una clave.")
                .setPositiveButton("Abrir", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                        } catch (Exception ignored) { }
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ---------- Vistas ----------

    private TextView button(String text, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackgroundResource(R.drawable.btn_bg);
        b.setOnClickListener(l);
        return b;
    }

    private View gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 1));
        return v;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row;
            if (convert instanceof LinearLayout) {
                row = (LinearLayout) convert;
            } else {
                row = new LinearLayout(OptimizeActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(10), dp(8), dp(10));

                ImageView iv = new ImageView(OptimizeActivity.this);
                row.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));

                LinearLayout texts = new LinearLayout(OptimizeActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(14), 0, dp(8), 0);
                TextView name = new TextView(OptimizeActivity.this);
                name.setTextColor(Color.WHITE);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                name.setSingleLine(true);
                texts.addView(name);
                TextView pkg = new TextView(OptimizeActivity.this);
                pkg.setTextColor(0xFF5A6370);
                pkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                pkg.setSingleLine(true);
                texts.addView(pkg);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView auto = new TextView(OptimizeActivity.this);
                auto.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                auto.setTextColor(0xFFFFB13D);
                auto.setPadding(0, 0, dp(12), 0);
                row.addView(auto);

                TextView tag = new TextView(OptimizeActivity.this);
                tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tag.setPadding(dp(10), dp(4), dp(10), dp(4));
                row.addView(tag);
            }
            Row r = shown.get(pos);
            LinearLayout texts = (LinearLayout) row.getChildAt(1);
            ((ImageView) row.getChildAt(0)).setImageDrawable(r.icon);
            ((TextView) texts.getChildAt(0)).setText(r.label);
            ((TextView) texts.getChildAt(1)).setText(r.pkg);
            ((TextView) row.getChildAt(2)).setText(r.enabled && r.autostart && r.kind != Apps.KIND_PROTECTED
                    ? "Se inicia sola" : "");

            TextView tag = (TextView) row.getChildAt(3);
            if (!r.enabled) {
                tag.setText("Desactivada");
                tag.setTextColor(0xFF5A6370);
            } else if (r.kind == Apps.KIND_SAFE) {
                tag.setText("Se puede desactivar ›");
                tag.setTextColor(0xFF3DDC84);
            } else if (r.kind == Apps.KIND_USER) {
                tag.setText("Si no la usas ›");
                tag.setTextColor(0xFF2EC4FF);
            } else if (r.kind == Apps.KIND_SYSTEM) {
                tag.setText("De fábrica · con cuidado ›");
                tag.setTextColor(0xFFFFB13D);
            } else {
                tag.setText("No tocar");
                tag.setTextColor(0xFFFF5A5A);
            }
            row.setAlpha(r.enabled ? 1f : 0.55f);
            return row;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
