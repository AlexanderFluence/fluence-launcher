package com.fluence.gps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/**
 * Brújula con el norte arriba: flecha azul = hacia dónde va el auto,
 * flecha verde = hacia el destino elegido. Al centro, la velocidad.
 */
final class CompassView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float heading = Float.NaN;
    private float goalBearing = Float.NaN;
    private float speed = -1f;

    CompassView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.parseColor("#26303C"));
        tick.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        big.setTextAlign(Paint.Align.CENTER);
        big.setColor(Color.WHITE);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        small.setTextAlign(Paint.Align.CENTER);
        small.setColor(Color.parseColor("#8A94A3"));
    }

    void setHeading(float deg) { heading = deg; invalidate(); }

    void setGoalBearing(float deg) { goalBearing = deg; invalidate(); }

    void setSpeed(float kmh) { speed = kmh; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - Math.min(w, h) * 0.06f;
        if (r <= 10) return;

        // Aro
        ring.setStrokeWidth(r * 0.10f);
        c.drawCircle(cx, cy, r * 0.95f, ring);

        // Marcas cada 15°
        for (int a = 0; a < 360; a += 15) {
            boolean major = a % 90 == 0;
            tick.setColor(major ? Color.WHITE : Color.parseColor("#5A6573"));
            tick.setStrokeWidth(major ? r * 0.025f : r * 0.012f);
            double rad = Math.toRadians(a);
            float r1 = r * (major ? 0.80f : 0.84f), r2 = r * 0.90f;
            c.drawLine(cx + (float) Math.sin(rad) * r1, cy - (float) Math.cos(rad) * r1,
                    cx + (float) Math.sin(rad) * r2, cy - (float) Math.cos(rad) * r2, tick);
        }

        // N E S O
        label.setTextSize(r * 0.15f);
        String[] n = {"N", "E", "S", "O"};
        for (int i = 0; i < 4; i++) {
            double rad = Math.toRadians(i * 90);
            float rr = r * 0.66f;
            label.setColor(i == 0 ? Color.parseColor("#FF5A5F") : Color.parseColor("#C9D1DB"));
            c.drawText(n[i], cx + (float) Math.sin(rad) * rr,
                    cy - (float) Math.cos(rad) * rr + label.getTextSize() * 0.36f, label);
        }

        // Flecha al destino (verde, en el borde)
        if (!Float.isNaN(goalBearing)) {
            fill.setColor(Color.parseColor("#3DDC84"));
            drawPointer(c, cx, cy, r, goalBearing, 0.97f, 0.78f, 0.11f);
        }

        // Rumbo del auto (azul, en el borde)
        if (!Float.isNaN(heading)) {
            fill.setColor(Color.parseColor("#2EC4FF"));
            drawPointer(c, cx, cy, r, heading, 1.02f, 0.82f, 0.09f);
        }

        // Velocidad al centro
        big.setTextSize(r * 0.46f);
        String v = speed < 0 ? "--" : String.valueOf(Math.round(speed));
        c.drawText(v, cx, cy + big.getTextSize() * 0.30f, big);
        small.setTextSize(r * 0.12f);
        c.drawText("km/h", cx, cy + big.getTextSize() * 0.30f + small.getTextSize() * 1.5f, small);
    }

    /** Triángulo que apunta hacia afuera, en el ángulo deg. */
    private void drawPointer(Canvas c, float cx, float cy, float r, float deg, float tip, float base, float halfW) {
        double a = Math.toRadians(deg);
        double side = halfW;
        float tx = cx + (float) Math.sin(a) * r * tip, ty = cy - (float) Math.cos(a) * r * tip;
        float lx = cx + (float) Math.sin(a - side) * r * base, ly = cy - (float) Math.cos(a - side) * r * base;
        float rx = cx + (float) Math.sin(a + side) * r * base, ry = cy - (float) Math.cos(a + side) * r * base;
        path.reset();
        path.moveTo(tx, ty);
        path.lineTo(lx, ly);
        path.lineTo(rx, ry);
        path.close();
        c.drawPath(path, fill);
    }
}
