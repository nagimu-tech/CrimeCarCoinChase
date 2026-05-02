package com.nagimutech.crimecarcoinchase;

import android.graphics.Color;

final class GameColors {
    int background = Color.rgb(23, 40, 68);
    int road = Color.rgb(49, 71, 106);
    int wall = Color.rgb(143, 61, 112);
    int player = Color.rgb(217, 54, 54);
    int police = Color.rgb(47, 125, 225);

    int lighten(int color, int amount) {
        return Color.rgb(
                clamp(Color.red(color) + amount),
                clamp(Color.green(color) + amount),
                clamp(Color.blue(color) + amount)
        );
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
