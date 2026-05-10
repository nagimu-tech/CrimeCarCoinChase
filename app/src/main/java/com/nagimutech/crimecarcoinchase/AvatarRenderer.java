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
    static final int CATEGORY_COUNT = 11;
    static final int[] OPTION_COUNTS = {8, 8, 8, 10, 8, 8, 9, 8, 8, 8, 9};
    static final int[] COLOR_COUNTS = {9, 9, 8, 10, 8, 8, 9, 8, 9, 8, 9};

    private AvatarRenderer() {
    }

    static int[] decode(String encoded) {
        int[] values = new int[CATEGORY_COUNT];
        String[] parts = encoded == null ? new String[0] : encoded.split(",");
        if (parts.length > 10) {
            for (int i = 0; i < CATEGORY_COUNT && i < parts.length; i++) {
                values[i] = parse(parts[i], OPTION_COUNTS[i]);
            }
            return values;
        }
        if (parts.length > 0) values[0] = parse(parts[0], OPTION_COUNTS[0]);
        values[1] = 0;
        if (parts.length > 1) values[2] = parse(parts[1], OPTION_COUNTS[2]);
        if (parts.length > 2) values[3] = parse(parts[2], OPTION_COUNTS[3]);
        if (parts.length > 3) values[4] = parse(parts[3], OPTION_COUNTS[4]);
        if (parts.length > 4) values[5] = parse(parts[4], OPTION_COUNTS[5]);
        values[6] = 0;
        if (parts.length > 6) values[7] = parse(parts[6], OPTION_COUNTS[7]);
        if (parts.length > 7) values[8] = parse(parts[7], OPTION_COUNTS[8]);
        if (parts.length > 8) values[9] = parse(parts[8], OPTION_COUNTS[9]);
        values[10] = 0;
        return values;
    }

    static int[] decodeColors(String encoded) {
        int[] colors = defaultColors();
        String[] parts = encoded == null ? new String[0] : encoded.split(",");
        if (parts.length >= CATEGORY_COUNT + CATEGORY_COUNT) {
            for (int i = 0; i < CATEGORY_COUNT; i++) {
                colors[i] = parse(parts[CATEGORY_COUNT + i], COLOR_COUNTS[i]);
            }
            return colors;
        }
        if (parts.length == 10) {
            colors[6] = parse(parts[5], COLOR_COUNTS[6]);
            colors[10] = parse(parts[9], COLOR_COUNTS[10]);
        }
        return colors;
    }

    static String encode(int[] values) {
        return encode(values, defaultColors());
    }

    static String encode(int[] values, int[] colors) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            if (i > 0) out.append(",");
            int value = values == null || i >= values.length ? 0 : values[i];
            out.append(Math.max(0, value) % OPTION_COUNTS[i]);
        }
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            out.append(",");
            int value = colors == null || i >= colors.length ? defaultColors()[i] : colors[i];
            out.append(Math.max(0, value) % COLOR_COUNTS[i]);
        }
        return out.toString();
    }

    static int colorCount(int category) {
        return COLOR_COUNTS[Math.max(0, Math.min(COLOR_COUNTS.length - 1, category))];
    }

    static int colorFor(int category, int option) {
        int[] palette = palette(category);
        return palette[Math.max(0, option) % palette.length];
    }

    static Bitmap bitmap(String encoded, int size, boolean bust) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(32, size), Math.max(32, size), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas, encoded, 0f, 0f, bitmap.getWidth(), bitmap.getHeight(), bust);
        return bitmap;
    }

    static void draw(Canvas canvas, String encoded, float left, float top, float size, boolean bust) {
        draw(canvas, decode(encoded), decodeColors(encoded), left, top, size, size, bust);
    }

    static void draw(Canvas canvas, int[] values, float left, float top, float size, boolean bust) {
        draw(canvas, values, defaultColors(), left, top, size, size, bust);
    }

    static void draw(Canvas canvas, int[] values, int[] colors, float left, float top, float size, boolean bust) {
        draw(canvas, values, colors, left, top, size, size, bust);
    }

    static void draw(Canvas canvas, int[] v, float left, float top, float width, float height, boolean bust) {
        draw(canvas, v, defaultColors(), left, top, width, height, bust);
    }

    static void draw(Canvas canvas, int[] v, int[] c, float left, float top, float width, float height, boolean bust) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] values = normalizeValues(v);
        int[] colorValues = normalizeColors(c);
        canvas.save();
        canvas.translate(left, top);
        float s = Math.min(width, height);
        float cx = width / 2f;
        int skinColor = colorFor(10, colorValues[10]);
        int chinColor = blend(skinColor, colorFor(1, colorValues[1]), 0.25f);
        int shirt = colorFor(2, colorValues[2]);
        int hair = colorFor(3, colorValues[3]);
        int eyeColor = colorFor(6, colorValues[6]);
        int glasses = values[7] == 0 ? Color.TRANSPARENT : colorFor(7, colorValues[7]);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, height, Color.rgb(74, 74, 74), Color.rgb(56, 56, 56), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        drawBody(canvas, paint, cx, s, height, shirt, values[2], bust);
        paint.setColor(skinColor);
        canvas.drawRoundRect(new RectF(cx - s * 0.07f, s * 0.58f, cx + s * 0.07f, s * 0.76f), s * 0.03f, s * 0.03f, paint);
        drawEars(canvas, paint, cx, s, skinColor, colorFor(9, colorValues[9]), values[9]);
        drawHead(canvas, paint, cx, s, skinColor, values[0]);
        drawChin(canvas, paint, cx, s, chinColor, values[1]);
        drawHair(canvas, paint, cx, s, hair, values[3]);
        drawEyes(canvas, paint, cx, s, eyeColor, values[5], values[6]);
        drawGlasses(canvas, paint, cx, s, glasses, values[7]);
        drawNoseMouth(canvas, paint, cx, s, skinColor, colorFor(4, colorValues[4]), values[4], values[8]);

        paint.setColor(blend(skinColor, Color.WHITE, 0.45f));
        canvas.drawOval(cx - s * 0.055f, s * 0.675f, cx + s * 0.055f, s * 0.77f, paint);
        canvas.restore();
    }

    private static int parse(String value, int modulo) {
        try {
            return Math.max(0, Integer.parseInt(value)) % modulo;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int[] defaultColors() {
        return new int[]{1, 1, 0, 0, 0, 0, 4, 1, 0, 2, 0};
    }

    private static int[] normalizeValues(int[] values) {
        int[] out = new int[CATEGORY_COUNT];
        for (int i = 0; i < out.length; i++) {
            out[i] = values == null || i >= values.length ? 0 : Math.max(0, values[i]) % OPTION_COUNTS[i];
        }
        return out;
    }

    private static int[] normalizeColors(int[] values) {
        int[] defaults = defaultColors();
        int[] out = new int[CATEGORY_COUNT];
        for (int i = 0; i < out.length; i++) {
            out[i] = values == null || i >= values.length ? defaults[i] : Math.max(0, values[i]) % COLOR_COUNTS[i];
        }
        return out;
    }

    private static int[] palette(int category) {
        if (category == 2) {
            return new int[]{Color.rgb(118, 188, 56), Color.rgb(54, 164, 211), Color.rgb(245, 138, 54), Color.rgb(157, 99, 218), Color.rgb(236, 77, 92), Color.rgb(245, 202, 67), Color.rgb(34, 176, 132), Color.rgb(58, 70, 96)};
        }
        if (category == 3) {
            return new int[]{Color.rgb(47, 48, 50), Color.rgb(210, 93, 70), Color.rgb(74, 52, 38), Color.rgb(58, 130, 214), Color.rgb(40, 166, 126), Color.rgb(236, 121, 178), Color.rgb(230, 230, 226), Color.rgb(146, 88, 232), Color.rgb(33, 34, 38), Color.rgb(246, 184, 72)};
        }
        if (category == 6) {
            return new int[]{Color.rgb(55, 55, 55), Color.rgb(192, 91, 0), Color.rgb(180, 145, 0), Color.rgb(74, 149, 0), Color.rgb(24, 150, 155), Color.rgb(78, 130, 225), Color.rgb(98, 62, 170), Color.rgb(28, 122, 86), Color.rgb(215, 95, 118)};
        }
        if (category == 7) {
            return new int[]{Color.TRANSPARENT, Color.rgb(92, 150, 42), Color.rgb(38, 120, 200), Color.rgb(242, 126, 0), Color.rgb(168, 92, 220), Color.rgb(34, 38, 48), Color.rgb(230, 58, 74), Color.rgb(235, 220, 92)};
        }
        if (category == 4) {
            return new int[]{Color.rgb(92, 46, 37), Color.rgb(150, 58, 70), Color.rgb(95, 54, 120), Color.rgb(64, 55, 50), Color.rgb(210, 86, 96), Color.rgb(80, 40, 34), Color.rgb(180, 72, 92), Color.rgb(60, 65, 76)};
        }
        if (category == 9) {
            return new int[]{Color.rgb(210, 225, 238), Color.rgb(255, 194, 58), Color.rgb(95, 208, 225), Color.rgb(255, 130, 175), Color.rgb(230, 230, 90), Color.rgb(190, 130, 255), Color.rgb(255, 255, 255), Color.rgb(90, 230, 170)};
        }
        return new int[]{Color.rgb(236, 176, 124), Color.rgb(139, 76, 62), Color.rgb(172, 92, 47), Color.rgb(162, 85, 68), Color.rgb(198, 118, 58), Color.rgb(244, 200, 154), Color.rgb(106, 62, 49), Color.rgb(219, 143, 88), Color.rgb(184, 112, 88)};
    }

    private static void drawBody(Canvas canvas, Paint paint, float cx, float s, float height, int shirt, int style, boolean bust) {
        float bodyTop = bust ? s * 0.66f : s * 0.72f;
        paint.setColor(shirt);
        Path body = new Path();
        float shoulder = style == 1 || style == 5 ? s * 0.31f : s * 0.25f;
        body.moveTo(cx - shoulder, height);
        body.lineTo(cx - s * (style == 2 ? 0.13f : 0.18f), bodyTop);
        body.quadTo(cx, bodyTop - s * (style == 3 ? 0.13f : 0.08f), cx + s * (style == 4 ? 0.13f : 0.18f), bodyTop);
        body.lineTo(cx + shoulder, height);
        body.close();
        canvas.drawPath(body, paint);
        if (style >= 6) {
            paint.setColor(blend(shirt, Color.WHITE, 0.28f));
            canvas.drawRoundRect(cx - s * 0.04f, bodyTop + s * 0.04f, cx + s * 0.04f, height, s * 0.02f, s * 0.02f, paint);
        }
    }

    private static void drawHead(Canvas canvas, Paint paint, float cx, float s, int skin, int shape) {
        paint.setColor(skin);
        RectF head = new RectF(cx - s * 0.23f, s * 0.28f, cx + s * 0.23f, s * 0.63f);
        float radius = shape == 1 ? s * 0.04f : shape == 2 ? s * 0.16f : shape == 4 ? s * 0.12f : shape == 5 ? s * 0.02f : s * 0.08f;
        canvas.drawRoundRect(head, radius, radius, paint);
        if (shape == 3) {
            canvas.drawOval(cx - s * 0.2f, s * 0.25f, cx + s * 0.2f, s * 0.65f, paint);
        } else if (shape == 6) {
            canvas.drawOval(cx - s * 0.25f, s * 0.3f, cx + s * 0.25f, s * 0.64f, paint);
        } else if (shape == 7) {
            canvas.drawRoundRect(new RectF(cx - s * 0.26f, s * 0.31f, cx + s * 0.26f, s * 0.58f), s * 0.05f, s * 0.05f, paint);
        }
    }

    private static void drawChin(Canvas canvas, Paint paint, float cx, float s, int skin, int style) {
        paint.setColor(skin);
        Path chin = new Path();
        float top = s * 0.56f;
        if (style == 1) {
            canvas.drawRoundRect(new RectF(cx - s * 0.18f, top, cx + s * 0.18f, s * 0.69f), s * 0.03f, s * 0.03f, paint);
        } else if (style == 2) {
            chin.moveTo(cx - s * 0.16f, top);
            chin.lineTo(cx + s * 0.16f, top);
            chin.lineTo(cx, s * 0.72f);
            chin.close();
            canvas.drawPath(chin, paint);
        } else if (style == 3) {
            canvas.drawOval(cx - s * 0.22f, top - s * 0.02f, cx + s * 0.22f, s * 0.69f, paint);
        } else if (style == 4) {
            canvas.drawOval(cx - s * 0.12f, top, cx + s * 0.12f, s * 0.7f, paint);
        } else if (style == 5) {
            canvas.drawRoundRect(new RectF(cx - s * 0.2f, top, cx + s * 0.2f, s * 0.67f), s * 0.1f, s * 0.1f, paint);
        } else if (style == 6) {
            chin.moveTo(cx - s * 0.18f, top);
            chin.lineTo(cx + s * 0.18f, top);
            chin.quadTo(cx + s * 0.12f, s * 0.72f, cx, s * 0.72f);
            chin.quadTo(cx - s * 0.12f, s * 0.72f, cx - s * 0.18f, top);
            chin.close();
            canvas.drawPath(chin, paint);
        } else if (style == 7) {
            canvas.drawRoundRect(new RectF(cx - s * 0.15f, top, cx + s * 0.15f, s * 0.64f), s * 0.04f, s * 0.04f, paint);
        } else {
            canvas.drawRoundRect(new RectF(cx - s * 0.17f, top, cx + s * 0.17f, s * 0.68f), s * 0.07f, s * 0.07f, paint);
        }
    }

    private static void drawEars(Canvas canvas, Paint paint, float cx, float s, int skin, int color, int style) {
        paint.setColor(skin);
        canvas.drawCircle(cx - s * 0.24f, s * 0.47f, s * 0.045f, paint);
        canvas.drawCircle(cx + s * 0.24f, s * 0.47f, s * 0.045f, paint);
        if (style > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.014f);
            paint.setColor(color);
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

    private static void drawEyes(Canvas canvas, Paint paint, float cx, float s, int color, int expression, int eyeStyle) {
        paint.setColor(Color.WHITE);
        float top = eyeStyle == 2 || eyeStyle == 6 ? s * 0.42f : s * 0.40f;
        float bottom = eyeStyle == 3 || eyeStyle == 7 ? s * 0.51f : s * 0.53f;
        float width = eyeStyle == 4 ? s * 0.115f : eyeStyle == 5 ? s * 0.18f : s * 0.14f;
        float gap = eyeStyle == 8 ? s * 0.036f : s * 0.02f;
        if (expression == 1 || expression == 5) {
            top += s * 0.02f;
        } else if (expression == 2 || expression == 6) {
            bottom -= s * 0.03f;
        }
        RectF leftEye = new RectF(cx - gap - width, top, cx - gap, bottom);
        RectF rightEye = new RectF(cx + gap, top, cx + gap + width, bottom);
        if (eyeStyle == 1 || eyeStyle == 6) {
            canvas.drawCircle(leftEye.centerX(), leftEye.centerY(), Math.min(leftEye.width(), leftEye.height()) * 0.46f, paint);
            canvas.drawCircle(rightEye.centerX(), rightEye.centerY(), Math.min(rightEye.width(), rightEye.height()) * 0.46f, paint);
        } else {
            canvas.drawOval(leftEye, paint);
            canvas.drawOval(rightEye, paint);
        }
        paint.setColor(color);
        float py = expression == 3 ? s * 0.48f : s * 0.465f;
        float offset = expression == 4 ? s * 0.025f : expression == 5 ? -s * 0.022f : 0f;
        float pupilRadius = expression == 6 ? s * 0.018f : s * 0.033f;
        float leftPx = Math.max(leftEye.left + pupilRadius, Math.min(leftEye.right - pupilRadius, leftEye.centerX() + offset));
        float rightPx = Math.max(rightEye.left + pupilRadius, Math.min(rightEye.right - pupilRadius, rightEye.centerX() + offset));
        float pupilY = Math.max(leftEye.top + pupilRadius, Math.min(leftEye.bottom - pupilRadius, py));
        canvas.drawCircle(leftPx, pupilY, pupilRadius, paint);
        canvas.drawCircle(rightPx, pupilY, pupilRadius, paint);
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

    private static void drawNoseMouth(Canvas canvas, Paint paint, float cx, float s, int skin, int mouthColor, int mouth, int type) {
        paint.setColor(Color.rgb(Math.max(40, Color.red(skin) - 60), Math.max(30, Color.green(skin) - 55), Math.max(25, Color.blue(skin) - 50)));
        canvas.drawCircle(cx, s * 0.535f, s * 0.038f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.018f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(mouthColor);
        if (mouth == 0) {
            canvas.drawArc(new RectF(cx - s * 0.1f, s * 0.55f, cx + s * 0.06f, s * 0.65f), 20, 140, false, paint);
        } else if (mouth == 1) {
            canvas.drawArc(new RectF(cx - s * 0.1f, s * 0.56f, cx + s * 0.1f, s * 0.68f), 190, 120, false, paint);
        } else if (mouth == 2) {
            canvas.drawLine(cx - s * 0.08f, s * 0.61f, cx + s * 0.08f, s * 0.61f, paint);
        } else if (mouth == 3) {
            canvas.drawArc(new RectF(cx - s * 0.08f, s * 0.56f, cx + s * 0.08f, s * 0.66f), 300, 220, false, paint);
        } else if (mouth == 4) {
            canvas.drawArc(new RectF(cx - s * 0.12f, s * 0.55f, cx + s * 0.12f, s * 0.7f), 25, 130, false, paint);
        } else if (mouth == 5) {
            canvas.drawArc(new RectF(cx - s * 0.12f, s * 0.55f, cx + s * 0.12f, s * 0.68f), 205, 130, false, paint);
        } else if (mouth == 6) {
            canvas.drawCircle(cx, s * 0.62f, s * 0.018f, paint);
        } else {
            canvas.drawLine(cx - s * 0.08f, s * 0.61f, cx - s * 0.02f, s * 0.61f, paint);
            canvas.drawLine(cx + s * 0.02f, s * 0.61f, cx + s * 0.08f, s * 0.61f, paint);
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
            tear.moveTo(cx - s * 0.13f, s * 0.51f);
            tear.quadTo(cx - s * 0.18f, s * 0.59f, cx - s * 0.12f, s * 0.62f);
            tear.quadTo(cx - s * 0.07f, s * 0.59f, cx - s * 0.13f, s * 0.51f);
            canvas.drawPath(tear, paint);
        }
    }

    private static int blend(int a, int b, float amount) {
        float keep = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(a) * keep + Color.red(b) * amount),
                Math.round(Color.green(a) * keep + Color.green(b) * amount),
                Math.round(Color.blue(a) * keep + Color.blue(b) * amount)
        );
    }
}
