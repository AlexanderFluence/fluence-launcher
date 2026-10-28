package com.fluence.lite;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Lista de apps. Con EXTRA_SLOT sirve para elegir la app de un acceso del inicio. */
public class AppsActivity extends Activity {

    static final String EXTRA_SLOT = "slot";
    static final String EXTRA_TITLE = "title";

    private static final class Entry {
        String label;
        String pkg;
        Drawable icon;
    }

    private final List<Entry> entries = new ArrayList<>();
    private int slot = -1;
    private TextView status;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        slot = getIntent().getIntExtra(EXTRA_SLOT, -1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(10), dp(14), 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹  Volver");
        back.setTextColor(Color.WHITE);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        back.setPadding(dp(14), dp(10), dp(14), dp(10));
        back.setBackgroundResource(R.drawable.btn_bg);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        String t = getIntent().getStringExtra(EXTRA_TITLE);
        title.setText(t != null ? t : "Aplicaciones");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setPadding(dp(16), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        if (slot < 0) {
            TextView tip = new TextView(this);
            tip.setText("Toca para abrir · Mantén presionado para ver la app en Ajustes (desactivar o desinstalar)");
            tip.setTextColor(0xFF4A525E);
            tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tip.setPadding(dp(4), dp(8), 0, 0);
            root.addView(tip);
        }

        status = new TextView(this);
        status.setText("Cargando…");
        status.setTextColor(0xFF8A94A3);
        status.setPadding(dp(4), dp(12), 0, 0);
        root.addView(status);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(120));
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(0, dp(10), 0, dp(10));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onPick(entries.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            Apps.openDetails(this, entries.get(pos).pkg);
            return true;
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        load();
    }

    private void onPick(Entry e) {
        if (slot >= 0) {
            Apps.prefs(this).edit().putString("slot_" + slot, e.pkg).apply();
            Toast.makeText(this, "Acceso cambiado a " + e.label, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Apps.launch(this, e.pkg);
        }
    }

    /** Carga nombres e íconos en segundo plano para que la pantalla abra al instante. */
    private void load() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<Entry> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : Apps.launchables(this)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                Entry e = new Entry();
                e.pkg = pkg;
                e.label = String.valueOf(ri.loadLabel(pm));
                try {
                    e.icon = ri.loadIcon(pm);
                } catch (Exception ignored) { }
                list.add(e);
            }
            Collator col = Collator.getInstance();
            list.sort((a, b) -> col.compare(a.label, b.label));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                entries.clear();
                entries.addAll(list);
                status.setText(list.size() + " apps");
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int i) { return entries.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout cell;
            if (convert instanceof LinearLayout) {
                cell = (LinearLayout) convert;
            } else {
                cell = new LinearLayout(AppsActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(4), dp(12), dp(4), dp(12));
                cell.setBackgroundResource(R.drawable.tile_bg);
                ImageView iv = new ImageView(AppsActivity.this);
                cell.addView(iv, new LinearLayout.LayoutParams(dp(56), dp(56)));
                TextView tv = new TextView(AppsActivity.this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setPadding(0, dp(8), 0, 0);
                cell.addView(tv);
            }
            Entry e = entries.get(pos);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(e.icon);
            ((TextView) cell.getChildAt(1)).setText(e.label);
            return cell;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
