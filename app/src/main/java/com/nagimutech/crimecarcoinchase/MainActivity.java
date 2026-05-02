package com.nagimutech.crimecarcoinchase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
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
}
