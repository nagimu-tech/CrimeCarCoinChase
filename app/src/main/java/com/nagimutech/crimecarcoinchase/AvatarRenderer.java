package com.nagimutech.crimecarcoinchase;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

final class AvatarRenderer {
    static final int CATEGORY_COUNT = 10;
    static final int[] OPTION_COUNTS = {4, 4, 6, 4, 4, 5, 4, 4, 4, 5};

    private AvatarRenderer() {
    }

    static int[] decode(String encoded) {
        int[] values = new int[CATEGORY_COUNT];
        String[] parts = encoded == null ? new String[0] : encoded.split(",");
        for (int i = 0; i < values.length && i < parts.length; i++) {
            try {
                values[i] = Math.max(0, Integer.parseInt(parts[i])) % OPTION_COUNTS[i];
            } catch (NumberFormatException ignored) {
                values[i] = 0;
            }
        }
        return values;
    }

    static String encode(int[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            if (i > 0) out.append(",");
            int value = values == null || i >= values.length ? 0 : values[i];
            out.append(Math.max(0, value) % OPTION_COUNTS[i]);
        }
        return out.toString();
    }

    static Bitmap bitmap(String encoded, int size, boolean bust) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(32, size), Math.max(32, size), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas, decode(encoded), 0f, 0f, bitmap.getWidth(), bitmap.getHeight(), bust);
        return bitmap;
    }

    static void draw(Canvas canvas, String encoded, float left, float top, float size, boolean bust) {
        draw(canvas, decode(encoded), left, top, size, size, bust);
    }

    static void draw(Canvas canvas, int[] values, float left, float top, float size, boolean bust) {
        draw(canvas, values, left, top, size, size, bust);
    }

    static void draw(Canvas canvas, int[] v, float left, float top, float width, float height, boolean bust) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.save();
        canvas.translate(left, top);
        float s = Math.min(width, height);
        float cx = width / 2f;
        float skin = 0;
        int skinColor = new int[]{
                Color.rgb(236, 176, 124),
                Color.rgb(139, 76, 62),
                Color.rgb(172, 92, 47),
                Color.rgb(162, 85, 68),
                Color.rgb(198, 118, 58)
        }[v[9] % 5];
        int shirt = new int[]{
                Color.rgb(118, 188, 56),
                Color.rgb(54, 164, 211),
                Color.rgb(245, 138, 54),
                Color.rgb(157, 99, 218)
        }[v[1] % 4];
        int hair = new int[]{
                Color.rgb(47, 48, 50),
                Color.rgb(210, 93, 70),
                Color.rgb(74, 52, 38),
                Color.rgb(58, 130, 214),
                Color.rgb(40, 166, 126),
                Color.rgb(236, 121, 178)
        }[v[2] % 6];
        int eyeColor = new int[]{
                Color.rgb(55, 55, 55),
                Color.rgb(192, 91, 0),
                Color.rgb(180, 145, 0),
                Color.rgb(74, 149, 0),
                Color.rgb(24, 150, 155)
        }[v[5] % 5];
        int glasses = new int[]{
                Color.TRANSPARENT,
                Color.rgb(92, 150, 42),
                Color.rgb(38, 120, 200),
                Color.rgb(242, 126, 0)
        }[v[6] % 4];

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, height, Color.rgb(74, 74, 74), Color.rgb(56, 56, 56), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        float bodyTop = bust ? s * 0.66f : s * 0.72f;
        paint.setColor(shirt);
        Path body = new Path();
        body.moveTo(cx - s * 0.25f, height);
        body.lineTo(cx - s * 0.18f, bodyTop);
        body.quadTo(cx, bodyTop - s * 0.08f, cx + s * 0.18f, bodyTop);
        body.lineTo(cx + s * 0.25f, height);
        body.close();
        canvas.drawPath(body, paint);

        paint.setColor(skinColor);
        canvas.drawRoundRect(new RectF(cx - s * 0.07f, s * 0.58f, cx + s * 0.07f, s * 0.76f), s * 0.03f, s * 0.03f, paint);
        drawEars(canvas, paint, cx, s, skinColor, v[8] % 4);
        drawHead(canvas, paint, cx, s, skinColor, v[0] % 4);
        drawHair(canvas, paint, cx, s, hair, v[2] % 6);
        drawEyes(canvas, paint, cx, s, eyeColor, v[4] % 4);
        drawGlasses(canvas, paint, cx, s, glasses, v[6] % 4);
        drawNoseMouth(canvas, paint, cx, s, skinColor, v[3] % 4, v[7] % 4);

        paint.setColor(Color.rgb(255, 208, 160));
        canvas.drawOval(cx - s * 0.055f, s * 0.675f, cx + s * 0.055f, s * 0.77f, paint);
        canvas.restore();
    }

    private static void drawHead(Canvas canvas, Paint paint, float cx, float s, int skin, int shape) {
        paint.setColor(skin);
        RectF head = new RectF(cx - s * 0.23f, s * 0.28f, cx + s * 0.23f, s * 0.66f);
        float radius = shape == 1 ? s * 0.04f : shape == 2 ? s * 0.16f : s * 0.08f;
        canvas.drawRoundRect(head, radius, radius, paint);
        if (shape == 3) {
            canvas.drawOval(cx - s * 0.2f, s * 0.25f, cx + s * 0.2f, s * 0.68f, paint);
        }
    }

    private static void drawEars(Canvas canvas, Paint paint, float cx, float s, int skin, int style) {
        paint.setColor(skin);
        canvas.drawCircle(cx - s * 0.24f, s * 0.47f, s * 0.045f, paint);
        canvas.drawCircle(cx + s * 0.24f, s * 0.47f, s * 0.045f, paint);
        if (style > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.014f);
            paint.setColor(style == 1 ? Color.rgb(210, 225, 238) : style == 2 ? Color.rgb(255, 194, 58) : Color.rgb(95, 208, 225));
            canvas.drawCircle(cx - s * 0.255f, s * 0.53f, s * 0.035f, paint);
            canvas.drawCircle(cx + s * 0.255f, s * 0.53f, s * 0.035f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static void drawHair(Canvas canvas, Paint paint, float cx, float s, int color, int style) {
        paint.setColor(color);
        if (style == 0) {
            canvas.drawRoundRect(new RectF(cx - s * 0.24f, s * 0.25f, cx + s * 0.24f, s * 0.39f), s * 0.07f, s * 0.07f, paint);
        } else if (style == 1) {
            canvas.drawArc(new RectF(cx - s * 0.29f, s * 0.18f, cx + s * 0.29f, s * 0.69f), 180, 180, true, paint);
            canvas.drawRect(cx - s * 0.29f, s * 0.36f, cx - s * 0.08f, s * 0.67f, paint);
            canvas.drawRect(cx + s * 0.1f, s * 0.36f, cx + s * 0.29f, s * 0.67f, paint);
        } else if (style == 2) {
            canvas.drawRoundRect(new RectF(cx - s * 0.17f, s * 0.18f, cx + s * 0.2f, s * 0.33f), s * 0.08f, s * 0.03f, paint);
        } else if (style == 3) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.035f);
            canvas.drawArc(new RectF(cx - s * 0.06f, s * 0.12f, cx + s * 0.06f, s * 0.28f), 110, 270, false, paint);
            paint.setStyle(Paint.Style.FILL);
        } else if (style == 4) {
            canvas.drawCircle(cx - s * 0.16f, s * 0.22f, s * 0.12f, paint);
            canvas.drawCircle(cx + s * 0.16f, s * 0.22f, s * 0.12f, paint);
        } else {
            canvas.drawArc(new RectF(cx - s * 0.26f, s * 0.18f, cx + s * 0.26f, s * 0.47f), 185, 170, true, paint);
        }
    }

    private static void drawEyes(Canvas canvas, Paint paint, float cx, float s, int color, int expression) {
        paint.setColor(Color.WHITE);
        if (expression == 1) {
            canvas.drawOval(cx - s * 0.15f, s * 0.42f, cx - s * 0.02f, s * 0.52f, paint);
            canvas.drawOval(cx + s * 0.02f, s * 0.42f, cx + s * 0.15f, s * 0.52f, paint);
        } else if (expression == 2) {
            canvas.drawOval(cx - s * 0.16f, s * 0.43f, cx - s * 0.02f, s * 0.50f, paint);
            canvas.drawOval(cx + s * 0.02f, s * 0.43f, cx + s * 0.16f, s * 0.50f, paint);
        } else {
            canvas.drawOval(cx - s * 0.16f, s * 0.40f, cx - s * 0.02f, s * 0.53f, paint);
            canvas.drawOval(cx + s * 0.02f, s * 0.40f, cx + s * 0.16f, s * 0.53f, paint);
        }
        paint.setColor(color);
        float py = expression == 3 ? s * 0.48f : s * 0.465f;
        canvas.drawRoundRect(new RectF(cx - s * 0.09f, py - s * 0.05f, cx - s * 0.04f, py + s * 0.055f), s * 0.02f, s * 0.02f, paint);
        canvas.drawRoundRect(new RectF(cx + s * 0.04f, py - s * 0.05f, cx + s * 0.09f, py + s * 0.055f), s * 0.02f, s * 0.02f, paint);
        if (expression == 2) {
            paint.setColor(Color.rgb(90, 58, 50));
            canvas.drawRect(cx - s * 0.17f, s * 0.405f, cx - s * 0.015f, s * 0.445f, paint);
            canvas.drawRect(cx + s * 0.015f, s * 0.405f, cx + s * 0.17f, s * 0.445f, paint);
        }
    }

    private static void drawGlasses(Canvas canvas, Paint paint, float cx, float s, int color, int style) {
        if (style == 0) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.022f);
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(cx - s * 0.19f, s * 0.38f, cx - s * 0.02f, s * 0.54f), s * 0.04f, s * 0.04f, paint);
        canvas.drawRoundRect(new RectF(cx + s * 0.02f, s * 0.38f, cx + s * 0.19f, s * 0.54f), s * 0.04f, s * 0.04f, paint);
        canvas.drawLine(cx - s * 0.02f, s * 0.46f, cx + s * 0.02f, s * 0.46f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static void drawNoseMouth(Canvas canvas, Paint paint, float cx, float s, int skin, int mouth, int type) {
        paint.setColor(Color.rgb(Math.max(40, Color.red(skin) - 60), Math.max(30, Color.green(skin) - 55), Math.max(25, Color.blue(skin) - 50)));
        canvas.drawCircle(cx, s * 0.535f, s * 0.038f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.018f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(92, 46, 37));
        if (mouth == 0) {
            canvas.drawArc(new RectF(cx - s * 0.1f, s * 0.55f, cx + s * 0.06f, s * 0.65f), 20, 140, false, paint);
        } else if (mouth == 1) {
            canvas.drawArc(new RectF(cx - s * 0.1f, s * 0.56f, cx + s * 0.1f, s * 0.68f), 190, 120, false, paint);
        } else if (mouth == 2) {
            canvas.drawLine(cx - s * 0.08f, s * 0.61f, cx + s * 0.08f, s * 0.61f, paint);
        } else {
            canvas.drawArc(new RectF(cx - s * 0.08f, s * 0.56f, cx + s * 0.08f, s * 0.66f), 300, 220, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (type == 3) {
            paint.setColor(Color.rgb(130, 235, 250));
            Path tear = new Path();
            tear.moveTo(cx - s * 0.26f, s * 0.5f);
            tear.quadTo(cx - s * 0.32f, s * 0.58f, cx - s * 0.25f, s * 0.61f);
            tear.quadTo(cx - s * 0.18f, s * 0.58f, cx - s * 0.26f, s * 0.5f);
            canvas.drawPath(tear, paint);
        }
    }
}
