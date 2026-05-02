package com.nagimutech.crimecarcoinchase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity implements GameView.Listener {
    private final GameColors colors = new GameColors();
    private GameView gameView;
    private TextView scoreView;
    private TextView damageView;
    private TextView messageView;
    private TextView historyView;
    private LinearLayout settingsPanel;
    private Spinner difficultySpinner;
    private SharedPreferences prefs;
    private Difficulty difficulty = Difficulty.BEGINNER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(GameConfig.PREFS, MODE_PRIVATE);
        prefs.edit().putInt(GameConfig.PREF_SCHEMA, GameConfig.STORAGE_SCHEMA).apply();
        setTitle("Погоня за монетами");
        buildLayout();
        renderHistory();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(colors.background);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("Погоня за монетами");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout hud = new LinearLayout(this);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding(0, dp(8), 0, dp(8));
        hud.setOrientation(LinearLayout.HORIZONTAL);
        scoreView = makeHudText("Богатство: 0 / 0");
        damageView = makeHudText("Урон: 0 / 5");
        hud.addView(scoreView, new LinearLayout.LayoutParams(0, -2, 1f));
        hud.addView(damageView, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(hud, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button menuButton = makeButton("Меню");
        Button exitButton = makeButton("Выход");
        Button restartButton = makeButton("Старт");
        actions.addView(menuButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        actions.addView(exitButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        actions.addView(restartButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(0, dp(8), 0, dp(8));
        settingsPanel.setVisibility(View.GONE);
        buildSettingsPanel();
        root.addView(settingsPanel, new LinearLayout.LayoutParams(-1, -2));

        messageView = new TextView(this);
        messageView.setText("Выбери уровень и нажми Старт");
        messageView.setTextColor(Color.rgb(245, 200, 75));
        messageView.setGravity(Gravity.CENTER);
        messageView.setTextSize(16f);
        messageView.setPadding(0, dp(8), 0, dp(8));
        root.addView(messageView, new LinearLayout.LayoutParams(-1, -2));

        gameView = new GameView(this, colors, this);
        root.addView(gameView, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(makeDpad(), new LinearLayout.LayoutParams(-1, -2));

        menuButton.setOnClickListener(v -> settingsPanel.setVisibility(settingsPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        exitButton.setOnClickListener(v -> {
            gameView.exitToMenu();
            messageView.setText("Выход в меню. Можно изменить настройки и начать заново.");
        });
        restartButton.setOnClickListener(v -> {
            gameView.start(difficulty);
            messageView.setText("Собери все монеты и алмазы");
        });
    }

    private void buildSettingsPanel() {
        TextView version = makePanelText("Версия " + GameConfig.APP_VERSION + ". История и настройки хранятся с версией схемы " + GameConfig.STORAGE_SCHEMA + ".");
        settingsPanel.addView(version, new LinearLayout.LayoutParams(-1, -2));

        difficultySpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, difficultyLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        difficultySpinner.setAdapter(adapter);
        difficultySpinner.setSelection(1);
        difficultySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                difficulty = Difficulty.values()[position];
                if (gameView != null && !gameView.isPlaying()) {
                    gameView.reset(difficulty, false);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        settingsPanel.addView(difficultySpinner, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.addView(colorButton("Фон", "background"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        colorRow.addView(colorButton("Стены", "wall"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        settingsPanel.addView(colorRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout carRow = new LinearLayout(this);
        carRow.setOrientation(LinearLayout.HORIZONTAL);
        carRow.addView(colorButton("Преступник", "player"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        carRow.addView(colorButton("Полиция", "police"), new LinearLayout.LayoutParams(0, dp(44), 1f));
        settingsPanel.addView(carRow, new LinearLayout.LayoutParams(-1, -2));

        TextView historyTitle = makePanelText("История побед");
        historyTitle.setTextSize(18f);
        historyView = makePanelText("Побед пока нет");
        settingsPanel.addView(historyTitle, new LinearLayout.LayoutParams(-1, -2));
        settingsPanel.addView(historyView, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout makeDpad() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(8), 0, 0);

        Button up = dpadButton("▲", Direction.UP);
        Button left = dpadButton("◀", Direction.LEFT);
        Button down = dpadButton("▼", Direction.DOWN);
        Button right = dpadButton("▶", Direction.RIGHT);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        row1.addView(up, new LinearLayout.LayoutParams(dp(72), dp(56)));
        wrapper.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        row2.addView(left, new LinearLayout.LayoutParams(dp(72), dp(56)));
        row2.addView(down, new LinearLayout.LayoutParams(dp(72), dp(56)));
        row2.addView(right, new LinearLayout.LayoutParams(dp(72), dp(56)));
        wrapper.addView(row2, new LinearLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    private Button dpadButton(String text, Direction direction) {
        Button button = makeButton(text);
        button.setTextSize(22f);
        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                gameView.setHeldDirection(direction);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                gameView.setHeldDirection(Direction.NONE);
                return true;
            }
            return true;
        });
        return button;
    }

    private Button colorButton(String label, String target) {
        Button button = makeButton(label);
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
                    getWindow().getDecorView().setBackgroundColor(colors.background);
                    gameView.invalidate();
                })
                .show();
    }

    @Override
    public void onHudChanged(int score, int total, int damage) {
        scoreView.setText("Богатство: " + score + " / " + total);
        damageView.setText("Урон: " + damage + " / " + GameConfig.MAX_DAMAGE);
    }

    @Override
    public void onWin(int rating, int seconds, int damage) {
        saveWin(new WinRecord(rating, difficulty.label, seconds, damage));
        renderHistory();
        messageView.setText("Победа! Рейтинг: " + rating);
    }

    @Override
    public void onLose() {
        messageView.setText("Игра окончена. Полиция нанесла 5 уронов.");
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

    private void renderHistory() {
        if (historyView == null) {
            return;
        }
        List<WinRecord> wins = loadWins();
        if (wins.isEmpty()) {
            historyView.setText("Побед пока нет");
            return;
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
        historyView.setText(text.toString());
    }

    private String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private TextView makeHudText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15f);
        view.setTypeface(null, 1);
        return view;
    }

    private TextView makePanelText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14f);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(16, 20, 27));
        return button;
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
