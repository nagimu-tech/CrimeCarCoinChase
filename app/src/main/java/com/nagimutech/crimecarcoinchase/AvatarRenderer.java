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
    static final int[] OPTION_COUNTS = {8, 8, 10, 8, 8, 9, 8, 8, 8, 9};

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
        int skinColor = new int[]{
                Color.rgb(236, 176, 124),
                Color.rgb(139, 76, 62),
                Color.rgb(172, 92, 47),
                Color.rgb(162, 85, 68),
                Color.rgb(198, 118, 58),
                Color.rgb(244, 200, 154),
                Color.rgb(106, 62, 49),
                Color.rgb(219, 143, 88),
                Color.rgb(184, 112, 88)
        }[v[9] % 9];
        int shirt = new int[]{
                Color.rgb(118, 188, 56),
                Color.rgb(54, 164, 211),
                Color.rgb(245, 138, 54),
                Color.rgb(157, 99, 218),
                Color.rgb(236, 77, 92),
                Color.rgb(245, 202, 67),
                Color.rgb(34, 176, 132),
                Color.rgb(58, 70, 96)
        }[v[1] % 8];
        int hair = new int[]{
                Color.rgb(47, 48, 50),
                Color.rgb(210, 93, 70),
                Color.rgb(74, 52, 38),
                Color.rgb(58, 130, 214),
                Color.rgb(40, 166, 126),
                Color.rgb(236, 121, 178),
                Color.rgb(230, 230, 226),
                Color.rgb(146, 88, 232),
                Color.rgb(33, 34, 38),
                Color.rgb(246, 184, 72)
        }[v[2] % 10];
        int eyeColor = new int[]{
                Color.rgb(55, 55, 55),
                Color.rgb(192, 91, 0),
                Color.rgb(180, 145, 0),
                Color.rgb(74, 149, 0),
                Color.rgb(24, 150, 155),
                Color.rgb(78, 130, 225),
                Color.rgb(98, 62, 170),
                Color.rgb(28, 122, 86),
                Color.rgb(215, 95, 118)
        }[v[5] % 9];
        int glasses = new int[]{
                Color.TRANSPARENT,
                Color.rgb(92, 150, 42),
                Color.rgb(38, 120, 200),
                Color.rgb(242, 126, 0),
                Color.rgb(168, 92, 220),
                Color.rgb(34, 38, 48),
                Color.rgb(230, 58, 74),
                Color.rgb(235, 220, 92)
        }[v[6] % 8];

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
        drawEars(canvas, paint, cx, s, skinColor, v[8] % 8);
        drawHead(canvas, paint, cx, s, skinColor, v[0] % 8);
        drawHair(canvas, paint, cx, s, hair, v[2] % 10);
        drawEyes(canvas, paint, cx, s, eyeColor, v[4] % 8);
        drawGlasses(canvas, paint, cx, s, glasses, v[6] % 8);
        drawNoseMouth(canvas, paint, cx, s, skinColor, v[3] % 8, v[7] % 8);

        paint.setColor(Color.rgb(255, 208, 160));
        canvas.drawOval(cx - s * 0.055f, s * 0.675f, cx + s * 0.055f, s * 0.77f, paint);
        canvas.restore();
    }

    private static void drawHead(Canvas canvas, Paint paint, float cx, float s, int skin, int shape) {
        paint.setColor(skin);
        RectF head = new RectF(cx - s * 0.23f, s * 0.28f, cx + s * 0.23f, s * 0.66f);
        float radius = shape == 1 ? s * 0.04f : shape == 2 ? s * 0.16f : shape == 4 ? s * 0.12f : shape == 5 ? s * 0.02f : s * 0.08f;
        canvas.drawRoundRect(head, radius, radius, paint);
        if (shape == 3) {
            canvas.drawOval(cx - s * 0.2f, s * 0.25f, cx + s * 0.2f, s * 0.68f, paint);
        } else if (shape == 6) {
            canvas.drawOval(cx - s * 0.25f, s * 0.3f, cx + s * 0.25f, s * 0.66f, paint);
        } else if (shape == 7) {
            Path chin = new Path();
            chin.moveTo(cx - s * 0.2f, s * 0.3f);
            chin.lineTo(cx + s * 0.2f, s * 0.3f);
            chin.lineTo(cx + s * 0.23f, s * 0.56f);
            chin.quadTo(cx, s * 0.72f, cx - s * 0.23f, s * 0.56f);
            chin.close();
            canvas.drawPath(chin, paint);
        }
    }

    private static void drawEars(Canvas canvas, Paint paint, float cx, float s, int skin, int style) {
        paint.setColor(skin);
        canvas.drawCircle(cx - s * 0.24f, s * 0.47f, s * 0.045f, paint);
        canvas.drawCircle(cx + s * 0.24f, s * 0.47f, s * 0.045f, paint);
        if (style > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.014f);
            paint.setColor(style == 1 ? Color.rgb(210, 225, 238) : style == 2 ? Color.rgb(255, 194, 58) : style == 3 ? Color.rgb(95, 208, 225) : style == 4 ? Color.rgb(255, 130, 175) : Color.rgb(230, 230, 90));
            if (style == 5 || style == 7) {
                canvas.drawLine(cx - s * 0.255f, s * 0.51f, cx - s * 0.255f, s * 0.58f, paint);
                canvas.drawLine(cx + s * 0.255f, s * 0.51f, cx + s * 0.255f, s * 0.58f, paint);
            } else {
                canvas.drawCircle(cx - s * 0.255f, s * 0.53f, style == 6 ? s * 0.02f : s * 0.035f, paint);
                canvas.drawCircle(cx + s * 0.255f, s * 0.53f, style == 6 ? s * 0.02f : s * 0.035f, paint);
            }
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
        } else if (style == 5) {
            canvas.drawArc(new RectF(cx - s * 0.26f, s * 0.18f, cx + s * 0.26f, s * 0.47f), 185, 170, true, paint);
        } else if (style == 6) {
            canvas.drawCircle(cx, s * 0.2f, s * 0.11f, paint);
            canvas.drawRoundRect(new RectF(cx - s * 0.18f, s * 0.23f, cx + s * 0.18f, s * 0.36f), s * 0.06f, s * 0.06f, paint);
        } else if (style == 7) {
            Path wave = new Path();
            wave.moveTo(cx - s * 0.24f, s * 0.31f);
            wave.cubicTo(cx - s * 0.14f, s * 0.13f, cx + s * 0.03f, s * 0.39f, cx + s * 0.22f, s * 0.21f);
            wave.lineTo(cx + s * 0.23f, s * 0.38f);
            wave.lineTo(cx - s * 0.24f, s * 0.39f);
            wave.close();
            canvas.drawPath(wave, paint);
        } else if (style == 8) {
            canvas.drawRoundRect(new RectF(cx - s * 0.08f, s * 0.12f, cx + s * 0.08f, s * 0.42f), s * 0.04f, s * 0.04f, paint);
            canvas.drawRoundRect(new RectF(cx - s * 0.24f, s * 0.27f, cx + s * 0.24f, s * 0.38f), s * 0.06f, s * 0.06f, paint);
        } else {
            for (int i = -2; i <= 2; i++) {
                canvas.drawCircle(cx + i * s * 0.08f, s * 0.25f + Math.abs(i) * s * 0.012f, s * 0.06f, paint);
            }
        }
    }

    private static void drawEyes(Canvas canvas, Paint paint, float cx, float s, int color, int expression) {
        paint.setColor(Color.WHITE);
        if (expression == 1 || expression == 5) {
            canvas.drawOval(cx - s * 0.15f, s * 0.42f, cx - s * 0.02f, s * 0.52f, paint);
            canvas.drawOval(cx + s * 0.02f, s * 0.42f, cx + s * 0.15f, s * 0.52f, paint);
        } else if (expression == 2 || expression == 6) {
            canvas.drawOval(cx - s * 0.16f, s * 0.43f, cx - s * 0.02f, s * 0.50f, paint);
            canvas.drawOval(cx + s * 0.02f, s * 0.43f, cx + s * 0.16f, s * 0.50f, paint);
        } else {
            canvas.drawOval(cx - s * 0.16f, s * 0.40f, cx - s * 0.02f, s * 0.53f, paint);
            canvas.drawOval(cx + s * 0.02f, s * 0.40f, cx + s * 0.16f, s * 0.53f, paint);
        }
        paint.setColor(color);
        float py = expression == 3 ? s * 0.48f : s * 0.465f;
        float offset = expression == 4 ? s * 0.025f : expression == 5 ? -s * 0.022f : 0f;
        float pupilRadius = expression == 6 ? s * 0.018f : s * 0.033f;
        canvas.drawCircle(cx - s * 0.065f + offset, py, pupilRadius, paint);
        canvas.drawCircle(cx + s * 0.065f + offset, py, pupilRadius, paint);
        if (expression == 2 || expression == 6) {
            paint.setColor(Color.rgb(90, 58, 50));
            canvas.drawRect(cx - s * 0.17f, s * 0.405f, cx - s * 0.015f, s * 0.445f, paint);
            canvas.drawRect(cx + s * 0.015f, s * 0.405f, cx + s * 0.17f, s * 0.445f, paint);
        } else if (expression == 7) {
            paint.setColor(Color.rgb(90, 58, 50));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.012f);
            canvas.drawArc(new RectF(cx - s * 0.17f, s * 0.36f, cx - s * 0.02f, s * 0.45f), 200, 110, false, paint);
            canvas.drawArc(new RectF(cx + s * 0.02f, s * 0.36f, cx + s * 0.17f, s * 0.45f), 230, 110, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static void drawGlasses(Canvas canvas, Paint paint, float cx, float s, int color, int style) {
        if (style == 0) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(style >= 4 ? s * 0.015f : s * 0.022f);
        paint.setColor(color);
        float round = style == 5 ? s * 0.08f : s * 0.04f;
        float top = style == 6 ? s * 0.40f : s * 0.38f;
        float bottom = style == 6 ? s * 0.51f : s * 0.54f;
        canvas.drawRoundRect(new RectF(cx - s * 0.19f, top, cx - s * 0.02f, bottom), round, round, paint);
        canvas.drawRoundRect(new RectF(cx + s * 0.02f, top, cx + s * 0.19f, bottom), round, round, paint);
        canvas.drawLine(cx - s * 0.02f, s * 0.46f, cx + s * 0.02f, s * 0.46f, paint);
        if (style == 7) {
            canvas.drawLine(cx - s * 0.19f, s * 0.45f, cx - s * 0.25f, s * 0.42f, paint);
            canvas.drawLine(cx + s * 0.19f, s * 0.45f, cx + s * 0.25f, s * 0.42f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static void drawNoseMouth(Canvas canvas, Paint paint, float cx, float s, int skin, int mouth, int type) {
        paint.setColor(Color.rgb(Math.max(40, Color.red(skin) - 60), Math.max(30, Color.green(skin) - 55), Math.max(25, Color.blue(skin) - 50)));
        canvas.drawCircle(cx, s * 0.535f, s * 0.038f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.018f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.rgb(92, 46, 37));
        if (mouth == 0 || mouth == 4) {
            canvas.drawArc(new RectF(cx - s * 0.1f, s * 0.55f, cx + s * 0.06f, s * 0.65f), 20, 140, false, paint);
        } else if (mouth == 1 || mouth == 5) {
            canvas.drawArc(new RectF(cx - s * 0.1f, s * 0.56f, cx + s * 0.1f, s * 0.68f), 190, 120, false, paint);
        } else if (mouth == 2 || mouth == 6) {
            canvas.drawLine(cx - s * 0.08f, s * 0.61f, cx + s * 0.08f, s * 0.61f, paint);
        } else {
            canvas.drawArc(new RectF(cx - s * 0.08f, s * 0.56f, cx + s * 0.08f, s * 0.66f), 300, 220, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (type == 1 || type == 5) {
            paint.setColor(Color.rgb(Math.max(30, Color.red(skin) - 80), Math.max(30, Color.green(skin) - 70), Math.max(25, Color.blue(skin) - 55)));
            canvas.drawCircle(cx - s * 0.04f, s * 0.55f, s * 0.012f, paint);
            canvas.drawCircle(cx + s * 0.06f, s * 0.57f, s * 0.01f, paint);
        }
        if (type == 2 || type == 6) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.012f);
            paint.setColor(Color.rgb(120, 66, 52));
            canvas.drawLine(cx - s * 0.09f, s * 0.36f, cx - s * 0.03f, s * 0.38f, paint);
            canvas.drawLine(cx + s * 0.03f, s * 0.38f, cx + s * 0.09f, s * 0.36f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (type == 3 || type == 7) {
            paint.setColor(Color.rgb(130, 235, 250));
            Path tear = new Path();
            tear.moveTo(cx - s * 0.17f, s * 0.5f);
            tear.quadTo(cx - s * 0.22f, s * 0.58f, cx - s * 0.16f, s * 0.61f);
            tear.quadTo(cx - s * 0.10f, s * 0.58f, cx - s * 0.17f, s * 0.5f);
            canvas.drawPath(tear, paint);
        }
    }
}
