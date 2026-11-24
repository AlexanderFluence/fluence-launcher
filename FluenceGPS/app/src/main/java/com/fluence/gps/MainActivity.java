package com.fluence.gps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Fluence GPS: muestra dónde está el auto usando solo los satélites.
 * No usa Internet: coordenadas, velocidad, rumbo, recorrido y lugares guardados.
 */
public class MainActivity extends Activity implements LocationListener {

    private static final int REQ = 7;
    private static final int BG = Color.parseColor("#0B0E12");
    private static final int CARD = Color.parseColor("#151A21");
    private static final int ACCENT = Color.parseColor("#2EC4FF");
    private static final int GREEN = Color.parseColor("#3DDC84");
    private static final int AMBER = Color.parseColor("#FFB020");
    private static final int RED = Color.parseColor("#FF5A5F");
    private static final int MUTED = Color.parseColor("#8A94A3");

    private final Handler main = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private GnssStatus.Callback gnss;
    private boolean listening = false;

    private CompassView compass;
    private TrailView trail;
    private TextView status, latText, lonText, dmsText, detail, updated, goalText;

    private double lat = Double.NaN, lon = Double.NaN;
    private long fixTime = 0L;          // hora de la última posición
    private boolean live = false;       // ¿la posición es de ahora mismo?
    private float heading = Float.NaN;
    private float accuracy = -1f;
    private double altitude = Double.NaN;
    private int satUsed = 0, satSeen = 0;
    private long lastGpsAt = 0L;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            // Sin datos por más de 8 s: la posición deja de ser "en vivo"
            if (live && System.currentTimeMillis() - lastGpsAt > 8000) {
                live = false;
                compass.setSpeed(-1f);
                trail.setPosition(lat, lon, heading, false);
            }
            showStatus();
            showUpdated();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Store.loadTrail(this);
        buildUi();

        // Mientras llega el GPS, mostrar la última posición conocida
        double[] last = Store.last(this);
        if (last != null) {
            lat = last[0]; lon = last[1]; fixTime = (long) last[2];
            showPosition();
        }
        refreshPlaces();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGps();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopGps();
        main.removeCallbacks(tick);
        Store.saveTrail(this);
    }

    // ---------- Pantalla ----------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void buildUi() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        boolean landscape = w >= h;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));

        compass = new CompassView(this);
        trail = new TrailView(this);

        // Columna de datos
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setBackground(round(CARD, 16));
        info.setPadding(dp(18), dp(14), dp(18), dp(14));
        info.setGravity(Gravity.CENTER_VERTICAL);

        status = text(15, AMBER, true);
        TextView latLabel = text(12, MUTED, true);
        latLabel.setText("LATITUD");
        latText = text(30, Color.WHITE, false);
        latText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        TextView lonLabel = text(12, MUTED, true);
        lonLabel.setText("LONGITUD");
        lonText = text(30, Color.WHITE, false);
        lonText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        dmsText = text(14, Color.parseColor("#C9D1DB"), false);
        detail = text(14, Color.parseColor("#C9D1DB"), false);
        updated = text(13, MUTED, false);
        goalText = text(15, GREEN, true);
        goalText.setOnClickListener(v -> showPlaces());

        info.addView(status);
        addGap(info, 10);
        info.addView(latLabel);
        info.addView(latText);
        addGap(info, 4);
        info.addView(lonLabel);
        info.addView(lonText);
        addGap(info, 6);
        info.addView(dmsText);
        addGap(info, 6);
        info.addView(detail);
        info.addView(updated);
        addGap(info, 8);
        info.addView(goalText);

        LinearLayout content = new LinearLayout(this);
        if (landscape) {
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.addView(compass, new LinearLayout.LayoutParams(0, -1, 0.9f));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -1, 1.1f);
            ip.setMargins(dp(8), 0, dp(12), 0);
            content.addView(info, ip);
            content.addView(trail, new LinearLayout.LayoutParams(0, -1, 1.2f));
        } else {
            content.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(compass, new LinearLayout.LayoutParams(0, -1, 0.8f));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1.2f);
            ip.gravity = Gravity.CENTER_VERTICAL;
            top.addView(info, ip);
            content.addView(top, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, 0, 1f);
            tp.setMargins(0, dp(12), 0, 0);
            content.addView(trail, tp);
        }
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Botones
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        addButton(bar, "Guardar punto", ACCENT, v -> askSavePoint());
        addButton(bar, "Mis lugares", Color.WHITE, v -> showPlaces());
        addButton(bar, "Abrir en mapa", Color.WHITE, v -> openMap(lat, lon, "Mi auto"));
        addButton(bar, "Borrar recorrido", MUTED, v -> askClearTrail());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54));
        bp.setMargins(0, dp(12), 0, 0);
        root.addView(bar, bp);

        setContentView(root);
        showPosition();
        showStatus();
    }

    private void addGap(LinearLayout l, int dp) {
        l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void addButton(LinearLayout bar, String label, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(CARD, 14));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        if (bar.getChildCount() > 0) p.setMargins(dp(8), 0, 0, 0);
        bar.addView(b, p);
    }

    private void showStatus() {
        if (status == null) return;
        if (!hasPermission()) {
            status.setTextColor(RED);
            status.setText("● Falta el permiso de ubicación (toca aquí)");
            status.setOnClickListener(v -> requestPermission());
            return;
        }
        status.setOnClickListener(null);
        if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.setTextColor(RED);
            status.setText("● El GPS del equipo está apagado");
        } else if (live) {
            status.setTextColor(GREEN);
            status.setText(satUsed > 0 ? "● GPS con señal · " + satUsed + " satélites" : "● GPS con señal");
        } else {
            status.setTextColor(AMBER);
            status.setText(satSeen > 0 ? "● Buscando señal… " + satSeen + " satélites a la vista" : "● Buscando satélites…");
        }
    }

    private void showUpdated() {
        if (updated == null) return;
        if (fixTime == 0L) { updated.setText(""); return; }
        String when = Geo.ago(System.currentTimeMillis() - fixTime);
        updated.setText(live ? "Actualizado " + when : "Última posición conocida, " + when);
    }

    private void showPosition() {
        if (latText == null) return;
        if (Double.isNaN(lat)) {
            latText.setText("--");
            lonText.setText("--");
            dmsText.setText("Sal a cielo abierto para que el GPS encuentre satélites.");
            detail.setText("");
        } else {
            latText.setText(Geo.dec(lat));
            lonText.setText(Geo.dec(lon));
            dmsText.setText(Geo.dms(lat, true) + "   " + Geo.dms(lon, false));
            StringBuilder sb = new StringBuilder();
            if (accuracy >= 0) sb.append("Precisión ±").append(Math.round(accuracy)).append(" m");
            if (!Double.isNaN(altitude)) { if (sb.length() > 0) sb.append(" · "); sb.append("Altitud ").append(Math.round(altitude)).append(" m"); }
            if (!Float.isNaN(heading)) { if (sb.length() > 0) sb.append(" · "); sb.append("Rumbo ").append(Math.round(heading)).append("° ").append(Geo.dir(heading)); }
            detail.setText(sb.toString());
        }
        showUpdated();
        showGoal();
        trail.setPosition(lat, lon, heading, live);
        compass.setHeading(heading);
    }

    private void showGoal() {
        Store.Place g = Store.goalPlace(this);
        if (g == null || Double.isNaN(lat)) {
            compass.setGoalBearing(Float.NaN);
            goalText.setText(g == null ? "" : "Destino: " + g.name);
            return;
        }
        double d = Geo.distance(lat, lon, g.lat, g.lon);
        double b = Geo.bearing(lat, lon, g.lat, g.lon);
        compass.setGoalBearing((float) b);
        goalText.setText("Destino: " + g.name + " · " + Geo.dist(d) + " al " + Geo.dirLong(b));
    }

    private void refreshPlaces() {
        trail.setPlaces(Store.places(this), Store.goalPlace(this));
        showGoal();
    }

    // ---------- GPS ----------

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startGps() {
        if (!hasPermission()) { requestPermission(); return; }
        if (listening || lm == null) return;
        try {
            // Solo el proveedor GPS: funciona sin Internet ni chip
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
            listening = true;
            Location known = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (known != null && known.getTime() > fixTime) {
                lat = known.getLatitude(); lon = known.getLongitude(); fixTime = known.getTime();
                showPosition();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Este equipo no tiene GPS disponible", Toast.LENGTH_LONG).show();
        }
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                if (gnss == null) gnss = new GnssStatus.Callback() {
                    @Override public void onSatelliteStatusChanged(GnssStatus s) {
                        int used = 0;
                        for (int i = 0; i < s.getSatelliteCount(); i++) if (s.usedInFix(i)) used++;
                        satUsed = used;
                        satSeen = s.getSatelliteCount();
                    }
                };
                lm.registerGnssStatusCallback(gnss, main);
            } catch (Exception ignored) { }
        }
    }

    private void stopGps() {
        if (lm == null) return;
        try { lm.removeUpdates(this); } catch (Exception ignored) { }
        if (gnss != null && Build.VERSION.SDK_INT >= 24) {
            try { lm.unregisterGnssStatusCallback(gnss); } catch (Exception ignored) { }
        }
        listening = false;
    }

    @Override
    public void onLocationChanged(Location loc) {
        lat = loc.getLatitude();
        lon = loc.getLongitude();
        fixTime = System.currentTimeMillis();
        lastGpsAt = fixTime;
        live = true;
        accuracy = loc.hasAccuracy() ? loc.getAccuracy() : -1f;
        altitude = loc.hasAltitude() ? loc.getAltitude() : Double.NaN;
        float kmh = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0f;
        // El rumbo del GPS solo es confiable en movimiento
        if (loc.hasBearing() && kmh > 4f) heading = loc.getBearing();
        compass.setSpeed(kmh);
        addTrailPoint(loc);
        Store.saveLast(this, lat, lon, fixTime);
        showPosition();
        showStatus();
    }

    private void addTrailPoint(Location loc) {
        if (loc.hasAccuracy() && loc.getAccuracy() > 40f) return;
        List<double[]> t = Store.trail;
        if (!t.isEmpty()) {
            double[] p = t.get(t.size() - 1);
            if (Geo.distance(p[0], p[1], lat, lon) < 12) return;
        }
        t.add(new double[]{lat, lon});
        while (t.size() > Store.MAX_TRAIL) t.remove(0);
        if (t.size() % 50 == 0) Store.saveTrail(this);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override public void onProviderEnabled(String provider) { showStatus(); }

    @Override public void onProviderDisabled(String provider) {
        live = false;
        showStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ && hasPermission()) startGps();
        showStatus();
    }

    // ---------- Lugares ----------

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    private void askSavePoint() {
        if (Double.isNaN(lat)) {
            Toast.makeText(this, "Todavía no hay posición del GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        final double pLat = lat, pLon = lon;
        final EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(Store.places(this).isEmpty() ? "Casa" : "Lugar " + (Store.places(this).size() + 1));
        name.setSelectAllOnFocus(true);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        dialog()
                .setTitle("Guardar este punto")
                .setMessage(Geo.dec(pLat) + ", " + Geo.dec(pLon))
                .setView(box)
                .setPositiveButton("Guardar", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = "Lugar";
                    Store.addPlace(this, new Store.Place(n, pLat, pLon));
                    refreshPlaces();
                    Toast.makeText(this, "Guardado: " + n, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPlaces() {
        final List<Store.Place> list = Store.places(this);
        if (list.isEmpty()) {
            Toast.makeText(this, "Aún no guardas lugares. Usa \"Guardar punto\".", Toast.LENGTH_LONG).show();
            return;
        }
        int goal = Store.goal(this);
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Store.Place p = list.get(i);
            String where = "";
            if (!Double.isNaN(lat)) {
                double b = Geo.bearing(lat, lon, p.lat, p.lon);
                where = " · " + Geo.dist(Geo.distance(lat, lon, p.lat, p.lon)) + " al " + Geo.dir(b);
            }
            items[i] = (i == goal ? "➤ " : "") + p.name + where;
        }
        AlertDialog.Builder b = dialog().setTitle("Mis lugares").setItems(items, (d, which) -> placeOptions(which, list.get(which)));
        if (goal >= 0) b.setNegativeButton("Quitar destino", (d, w) -> { Store.setGoal(this, -1); refreshPlaces(); });
        b.setPositiveButton("Cerrar", null);
        b.show();
    }

    private void placeOptions(final int index, final Store.Place p) {
        String[] opts = {"Guiarme con la brújula", "Abrir en mapa", "Borrar"};
        dialog()
                .setTitle(p.name)
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        Store.setGoal(this, index);
                        refreshPlaces();
                    } else if (which == 1) {
                        openMap(p.lat, p.lon, p.name);
                    } else {
                        Store.removePlace(this, index);
                        refreshPlaces();
                    }
                })
                .show();
    }

    private void askClearTrail() {
        dialog()
                .setTitle("Borrar recorrido")
                .setMessage("Se borrará la línea del recorrido grabado. Los lugares guardados se mantienen.")
                .setPositiveButton("Borrar", (d, w) -> {
                    Store.clearTrail(this);
                    trail.invalidate();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Abre la ubicación en la app de mapas instalada (Google Maps funciona sin Internet si descargaste la zona). */
    private void openMap(double la, double lo, String label) {
        if (Double.isNaN(la)) {
            Toast.makeText(this, "Todavía no hay posición del GPS", Toast.LENGTH_SHORT).show();
            return;
        }
        String q = String.format(Locale.US, "%.6f,%.6f", la, lo);
        Uri uri = Uri.parse("geo:" + q + "?q=" + q + "(" + Uri.encode(label) + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "No hay una app de mapas instalada", Toast.LENGTH_LONG).show();
        }
    }
}
