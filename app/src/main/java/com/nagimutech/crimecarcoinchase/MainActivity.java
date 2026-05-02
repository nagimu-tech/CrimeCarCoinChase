package com.nagimutech.crimecarcoinchase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity implements GameView.Listener {
    private final GameColors colors = new GameColors();
    private GameView gameView;
    private SharedPreferences prefs;
    private Difficulty difficulty = Difficulty.BEGINNER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        prefs = getSharedPreferences(GameConfig.PREFS, MODE_PRIVATE);
        prefs.edit().putInt(GameConfig.PREF_SCHEMA, GameConfig.STORAGE_SCHEMA).apply();
        setTitle("Погоня за монетами");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(colors.background);
        gameView = new GameView(this, colors, this);
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onHudChanged(int score, int total, int damage) {
        // HUD is drawn directly by GameView in the full-screen version.
    }

    @Override
    public void onWin(int rating, int seconds, int damage) {
        saveWin(new WinRecord(rating, difficulty.label, seconds, damage));
        showWinDialog(rating, seconds, damage);
    }

    @Override
    public void onLose() {
        // GameView draws the restart hint.
    }

    @Override
    public void onMenuRequested() {
        gameView.pauseForMenu();
        showGameMenu();
    }

    private void showGameMenu() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(10));
        scroll.addView(panel);

        TextView title = panelText("Погоня за монетами");
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView version = panelText("Версия " + GameConfig.APP_VERSION + ". История сохраняется после обновлений.");
        panel.addView(version, new LinearLayout.LayoutParams(-1, -2));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, difficultyLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(difficulty.ordinal());
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                difficulty = Difficulty.values()[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        panel.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(colorButton("Фон", "background"), new LinearLayout.LayoutParams(0, dp(46), 1f));
        row1.addView(colorButton("Стены", "wall"), new LinearLayout.LayoutParams(0, dp(46), 1f));
        panel.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(colorButton("Преступник", "player"), new LinearLayout.LayoutParams(0, dp(46), 1f));
        row2.addView(colorButton("Полиция", "police"), new LinearLayout.LayoutParams(0, dp(46), 1f));
        panel.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        Button help = new Button(this);
        help.setText("\u0421\u043f\u0440\u0430\u0432\u043a\u0430");
        help.setAllCaps(false);
        help.setOnClickListener(v -> showHelpDialog());
        panel.addView(help, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView historyTitle = panelText("История побед");
        historyTitle.setTextSize(18f);
        historyTitle.setTypeface(null, 1);
        panel.addView(historyTitle, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(panelText(historyText()), new LinearLayout.LayoutParams(-1, -2));

        Button restart = new Button(this);
        restart.setText("Новый заезд");
        restart.setAllCaps(false);
        panel.addView(restart, new LinearLayout.LayoutParams(-1, dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .create();
        dialog.setOnDismissListener(d -> gameView.resumeFromMenu());
        restart.setOnClickListener(v -> {
            gameView.start(difficulty);
            dialog.dismiss();
        });
        dialog.show();
    }

    private Button colorButton(String label, String target) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> showColorDialog(target));
        return button;
    }

    private void showColorDialog(String target) {
        String[] labels = {"Синий", "Фиолетовый", "Красный", "Зеленый", "Желтый", "Голубой", "Темный"};
        int[] values = {
                Color.rgb(23, 40, 68),
                Color.rgb(143, 61, 112),
                Color.rgb(217, 54, 54),
                Color.rgb(48, 155, 96),
                Color.rgb(245, 200, 75),
                Color.rgb(47, 125, 225),
                Color.rgb(17, 21, 28)
        };
        new AlertDialog.Builder(this)
                .setTitle("Выбор цвета")
                .setItems(labels, (dialog, which) -> {
                    int color = values[which];
                    if ("background".equals(target)) {
                        colors.background = color;
                        colors.road = colors.lighten(color, 28);
                    } else if ("wall".equals(target)) {
                        colors.wall = color;
                    } else if ("player".equals(target)) {
                        colors.player = color;
                    } else if ("police".equals(target)) {
                        colors.police = color;
                    }
                    gameView.invalidate();
                })
                .show();
    }

    private void showHelpDialog() {
        String text = "\u0426\u0435\u043b\u044c: \u0441\u043e\u0431\u0440\u0430\u0442\u044c \u0432\u0441\u0435 \u043c\u043e\u043d\u0435\u0442\u044b \u0438 \u0430\u043b\u043c\u0430\u0437\u044b, \u043d\u0435 \u043f\u043e\u043b\u0443\u0447\u0438\u0432 5 \u0443\u0440\u043e\u043d\u043e\u0432.\n\n"
                + "\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435: \u043a\u043e\u0441\u043d\u0438\u0441\u044c \u044d\u043a\u0440\u0430\u043d\u0430 \u0438 \u0432\u0435\u0434\u0438 \u043f\u0430\u043b\u0435\u0446 \u0432\u0432\u0435\u0440\u0445, \u0432\u043d\u0438\u0437, \u0432\u043b\u0435\u0432\u043e \u0438\u043b\u0438 \u0432\u043f\u0440\u0430\u0432\u043e. \u0412\u0435\u0440\u043d\u0438 \u043f\u0430\u043b\u0435\u0446 \u043a \u0446\u0435\u043d\u0442\u0440\u0443 \u0436\u0435\u0441\u0442\u0430 \u0438\u043b\u0438 \u043e\u0442\u043f\u0443\u0441\u0442\u0438, \u0447\u0442\u043e\u0431\u044b \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c\u0441\u044f.\n\n"
                + "\u041c\u043e\u043d\u0435\u0442\u0430: +1 \u043a \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u0443.\n"
                + "\u0410\u043b\u043c\u0430\u0437: +10 \u043a \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u0443.\n\n"
                + "F: \u0424\u0440\u0438\u0437\u0435\u0440, \u0437\u0430\u043c\u043e\u0440\u0430\u0436\u0438\u0432\u0430\u0435\u0442 \u043f\u043e\u043b\u0438\u0446\u0438\u044e \u043d\u0430 20 \u0441\u0435\u043a\u0443\u043d\u0434.\n"
                + "S: \u0417\u0430\u0449\u0438\u0442\u0430, \u043d\u0430 20 \u0441\u0435\u043a\u0443\u043d\u0434 \u0443\u0431\u0438\u0440\u0430\u0435\u0442 \u0443\u0440\u043e\u043d \u043e\u0442 \u043f\u043e\u043b\u0438\u0446\u0438\u0438. \u041c\u0430\u0448\u0438\u043d\u043a\u0430 \u0441\u0432\u0435\u0442\u0438\u0442\u0441\u044f \u043f\u0443\u043b\u044c\u0441\u0438\u0440\u0443\u044e\u0449\u0435\u0439 \u043e\u043a\u0430\u043d\u0442\u043e\u0432\u043a\u043e\u0439.\n"
                + "G: \u041f\u0440\u0438\u0437\u0440\u0430\u043a, \u043d\u0430 7 \u0441\u0435\u043a\u0443\u043d\u0434 \u043f\u043e\u0437\u0432\u043e\u043b\u044f\u0435\u0442 \u0435\u0445\u0430\u0442\u044c \u0441\u043a\u0432\u043e\u0437\u044c \u0441\u0442\u0435\u043d\u044b.\n\n"
                + "\u0411\u043e\u043a\u043e\u0432\u044b\u0435 \u0434\u044b\u0440\u044b \u0432 \u0441\u0442\u0435\u043d\u0430\u0445 \u043f\u0435\u0440\u0435\u043d\u043e\u0441\u044f\u0442 \u043c\u0430\u0448\u0438\u043d\u043a\u0443 \u043d\u0430 \u0434\u0440\u0443\u0433\u0443\u044e \u0441\u0442\u043e\u0440\u043e\u043d\u0443 \u043a\u0430\u0440\u0442\u044b.";
        new AlertDialog.Builder(this)
                .setTitle("\u0421\u043f\u0440\u0430\u0432\u043a\u0430")
                .setMessage(text)
                .setPositiveButton("\u041f\u043e\u043d\u044f\u0442\u043d\u043e", null)
                .show();
    }

    private void saveWin(WinRecord record) {
        List<WinRecord> records = loadWins();
        records.add(record);
        Collections.sort(records, new Comparator<WinRecord>() {
            @Override
            public int compare(WinRecord a, WinRecord b) {
                return Integer.compare(b.rating, a.rating);
            }
        });
        while (records.size() > GameConfig.HISTORY_LIMIT) {
            records.remove(records.size() - 1);
        }
        StringBuilder encoded = new StringBuilder();
        for (WinRecord win : records) {
            if (encoded.length() > 0) {
                encoded.append("\n");
            }
            encoded.append(win.encode());
        }
        prefs.edit()
                .putInt(GameConfig.PREF_SCHEMA, GameConfig.STORAGE_SCHEMA)
                .putString(GameConfig.PREF_WINS, encoded.toString())
                .apply();
    }

    private List<WinRecord> loadWins() {
        ArrayList<WinRecord> records = new ArrayList<>();
        String raw = prefs.getString(GameConfig.PREF_WINS, "");
        if (raw == null || raw.isEmpty()) {
            return records;
        }
        String[] lines = raw.split("\n");
        for (String line : lines) {
            WinRecord record = WinRecord.decode(line);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    private String historyText() {
        List<WinRecord> wins = loadWins();
        if (wins.isEmpty()) {
            return "Побед пока нет";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < wins.size(); i++) {
            WinRecord win = wins.get(i);
            text.append(i + 1)
                    .append(". ")
                    .append(win.rating)
                    .append(" очков, ")
                    .append(win.difficulty)
                    .append(", ")
                    .append(formatTime(win.seconds))
                    .append(", урон ")
                    .append(win.damage)
                    .append("/")
                    .append(GameConfig.MAX_DAMAGE);
            if (i < wins.size() - 1) {
                text.append("\n");
            }
        }
        return text.toString();
    }

    private String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private TextView panelText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15f);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private List<String> difficultyLabels() {
        ArrayList<String> labels = new ArrayList<>();
        for (Difficulty value : Difficulty.values()) {
            labels.add(value.label);
        }
        return labels;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showWinDialog(int rating, int seconds, int damage) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(12));

        WinCelebrationView celebration = new WinCelebrationView(this);
        panel.addView(celebration, new LinearLayout.LayoutParams(-1, dp(170)));

        TextView title = panelText("\u041f\u043e\u0431\u0435\u0434\u0430!");
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView details = panelText("\u0420\u0435\u0439\u0442\u0438\u043d\u0433: " + rating
                + "\n\u0412\u0440\u0435\u043c\u044f: " + formatTime(seconds)
                + "\n\u0423\u0440\u043e\u043d: " + damage + "/" + GameConfig.MAX_DAMAGE);
        details.setGravity(Gravity.CENTER);
        panel.addView(details, new LinearLayout.LayoutParams(-1, -2));

        Button restart = new Button(this);
        restart.setAllCaps(false);
        restart.setText("\u041d\u043e\u0432\u044b\u0439 \u0437\u0430\u0435\u0437\u0434");
        panel.addView(restart, new LinearLayout.LayoutParams(-1, dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .create();
        restart.setOnClickListener(v -> {
            gameView.start(difficulty);
            dialog.dismiss();
        });
        dialog.show();
    }

    private static final class WinCelebrationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WinCelebrationView(Activity activity) {
            super(activity);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            long now = System.currentTimeMillis();
            canvas.drawColor(Color.rgb(20, 34, 58));

            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 14; i++) {
                float x = (i * 47f + (now % 1400) * 0.04f) % Math.max(1f, w);
                float y = 18f + (float) Math.sin(now / 220.0 + i) * 10f + (i % 4) * 28f;
                paint.setColor(i % 2 == 0 ? Color.rgb(250, 210, 70) : Color.rgb(120, 235, 255));
                canvas.drawCircle(x, y, 7f + (i % 3), paint);
            }

            float carX = w * 0.5f + (float) Math.sin(now / 260.0) * w * 0.22f;
            float carY = h * 0.62f;
            paint.setColor(Color.argb(90, 0, 0, 0));
            canvas.drawOval(new RectF(carX - 54f, carY + 28f, carX + 54f, carY + 42f), paint);
            paint.setColor(Color.rgb(210, 38, 52));
            canvas.drawRoundRect(new RectF(carX - 48f, carY - 24f, carX + 48f, carY + 26f), 14f, 14f, paint);
            paint.setColor(Color.rgb(20, 28, 38));
            canvas.drawRoundRect(new RectF(carX - 20f, carY - 18f, carX + 24f, carY + 7f), 8f, 8f, paint);
            paint.setColor(Color.rgb(255, 229, 120));
            canvas.drawRect(carX + 30f, carY - 16f, carX + 45f, carY - 5f, paint);
            canvas.drawRect(carX + 30f, carY + 8f, carX + 45f, carY + 19f, paint);
            paint.setColor(Color.rgb(28, 24, 28));
            canvas.drawRoundRect(new RectF(carX - 42f, carY - 34f, carX - 18f, carY + 36f), 10f, 10f, paint);
            canvas.drawRoundRect(new RectF(carX + 18f, carY - 34f, carX + 42f, carY + 36f), 10f, 10f, paint);

            postInvalidateOnAnimation();
        }
    }
}
