package com.fluence.gps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/**
 * Dibuja el recorrido grabado, los lugares guardados y tu posición,
 * con el norte arriba. No necesita mapa ni Internet.
 * Tocar cambia la escala: Auto → 300 m → 1 km → 5 km → 20 km.
 */
final class TrailView extends View {

    private static final float[] ZOOMS = {0f, 300f, 1000f, 5000f, 20000f};
    private int zoom = 0;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private double lat = Double.NaN, lon = Double.NaN;
    private float heading = Float.NaN;
    private boolean live = false;
    private List<Store.Place> places;
    private Store.Place goal;

    TrailView(Context c) {
        super(c);
        bg.setColor(Color.parseColor("#11161D"));
        grid.setStyle(Paint.Style.STROKE);
        grid.setColor(Color.parseColor("#26303C"));
        line.setStyle(Paint.Style.STROKE);
        line.setColor(Color.parseColor("#2EC4FF"));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(Color.parseColor("#C9D1DB"));
        title.setColor(Color.parseColor("#8A94A3"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        setOnClickListener(v -> { zoom = (zoom + 1) % ZOOMS.length; invalidate(); });
    }

    void setPosition(double lat, double lon, float heading, boolean live) {
        this.lat = lat; this.lon = lon; this.heading = heading; this.live = live;
        invalidate();
    }

    void setPlaces(List<Store.Place> places, Store.Place goal) {
        this.places = places; this.goal = goal;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float rad = 16 * d;
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, rad, rad, bg);

        title.setTextSize(13 * d);
        text.setTextSize(12 * d);
        c.drawText("RECORRIDO", 14 * d, 24 * d, title);

        if (Double.isNaN(lat)) {
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("Esperando la primera posición…", w / 2, h / 2, text);
            text.setTextAlign(Paint.Align.LEFT);
            return;
        }

        float cx = w / 2f, cy = h / 2f + 8 * d;
        float half = Math.min(w, h - 40 * d) / 2f - 10 * d;
        if (half < 20) return;

        // Escala: metros que caben del centro al borde
        float meters = ZOOMS[zoom];
        if (meters == 0f) meters = autoMeters();
        float k = half / meters; // píxeles por metro
        double cosLat = Math.cos(Math.toRadians(lat));

        // Anillos de distancia
        grid.setStrokeWidth(1 * d);
        c.drawCircle(cx, cy, half, grid);
        c.drawCircle(cx, cy, half / 2, grid);
        c.drawLine(cx - half, cy, cx + half, cy, grid);
        c.drawLine(cx, cy - half, cx, cy + half, grid);

        // Recorrido
        List<double[]> t = Store.trail;
        if (t.size() > 1) {
            line.setStrokeWidth(3.5f * d);
            path.reset();
            boolean first = true;
            for (double[] p : t) {
                float x = cx + (float) ((p[1] - lon) * cosLat * 111320.0 * k);
                float y = cy - (float) ((p[0] - lat) * 110540.0 * k);
                if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
            }
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawPath(path, line);
            c.restore();
        }

        // Lugares guardados
        if (places != null) {
            for (Store.Place p : places) {
                boolean isGoal = goal != null && p.name.equals(goal.name) && p.lat == goal.lat && p.lon == goal.lon;
                float x = cx + (float) ((p.lon - lon) * cosLat * 111320.0 * k);
                float y = cy - (float) ((p.lat - lat) * 110540.0 * k);
                if (x < 0 || y < 0 || x > w || y > h) continue;
                dot.setColor(Color.parseColor(isGoal ? "#3DDC84" : "#FFB020"));
                c.drawCircle(x, y, (isGoal ? 7 : 5) * d, dot);
                c.drawText(p.name, x + 9 * d, y + 4 * d, text);
            }
        }

        // Tu posición: flecha si hay rumbo, punto si no
        dot.setColor(live ? Color.WHITE : Color.parseColor("#8A94A3"));
        if (!Float.isNaN(heading)) {
            double a = Math.toRadians(heading);
            float s = 13 * d;
            path.reset();
            path.moveTo(cx + (float) Math.sin(a) * s, cy - (float) Math.cos(a) * s);
            path.lineTo(cx + (float) Math.sin(a + 2.5) * s * 0.8f, cy - (float) Math.cos(a + 2.5) * s * 0.8f);
            path.lineTo(cx + (float) Math.sin(a + Math.PI) * s * 0.35f, cy - (float) Math.cos(a + Math.PI) * s * 0.35f);
            path.lineTo(cx + (float) Math.sin(a - 2.5) * s * 0.8f, cy - (float) Math.cos(a - 2.5) * s * 0.8f);
            path.close();
            c.drawPath(path, dot);
        } else {
            c.drawCircle(cx, cy, 7 * d, dot);
        }

        // Escala y norte
        title.setTextAlign(Paint.Align.RIGHT);
        c.drawText((zoom == 0 ? "Auto · " : "") + "borde " + Geo.dist(meters), w - 14 * d, 24 * d, title);
        title.setTextAlign(Paint.Align.LEFT);
        text.setColor(Color.parseColor("#FF5A5F"));
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("N", cx, cy - half - 4 * d, text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(Color.parseColor("#C9D1DB"));
        title.setTextSize(11 * d);
        c.drawText("Toca para cambiar la escala", 14 * d, h - 12 * d, title);
    }

    /** Escala automática: muestra el último tramo del recorrido y el destino. */
    private float autoMeters() {
        double max = 150;
        List<double[]> t = Store.trail;
        int from = Math.max(0, t.size() - 400);
        for (int i = from; i < t.size(); i++) {
            double[] p = t.get(i);
            max = Math.max(max, Geo.distance(lat, lon, p[0], p[1]));
        }
        if (goal != null) max = Math.max(max, Geo.distance(lat, lon, goal.lat, goal.lon));
        return (float) Math.min(max * 1.15, 50000);
    }
}
