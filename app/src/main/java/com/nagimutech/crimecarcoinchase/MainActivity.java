package com.nagimutech.crimecarcoinchase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        refreshAwardHud();
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
    public void onWin(GameResult result) {
        saveWin(result);
        showWinDialog(result);
    }

    @Override
    public void onLose(GameResult result) {
        recordCompletedGameTime(result.startedAt);
        updateAwards(result);
        refreshAwardHud();
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

        TextView producer = panelText("\u041f\u0440\u043e\u0438\u0437\u0432\u043e\u0434\u0438\u0442\u0435\u043b\u044c: \u041e\u0441\u043c\u0430\u043d\u043e\u0432\u0430 \u0410\u043c\u0435\u043b\u0438\u044f");
        producer.setTextSize(18f);
        producer.setGravity(Gravity.CENTER);
        producer.setTypeface(null, 1);
        producer.setTextColor(Color.rgb(245, 200, 75));
        panel.addView(producer, new LinearLayout.LayoutParams(-1, -2));

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

        Button history = new Button(this);
        history.setText("История побед");
        history.setAllCaps(false);
        history.setOnClickListener(v -> showHistoryDialog());
        panel.addView(history, new LinearLayout.LayoutParams(-1, dp(46)));

        Button awards = new Button(this);
        awards.setText("Награды");
        awards.setAllCaps(false);
        awards.setOnClickListener(v -> showAwardsDialog());
        panel.addView(awards, new LinearLayout.LayoutParams(-1, dp(46)));

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
                + "\u041d\u0430 \u0432\u0441\u0435\u0445 \u0443\u0440\u043e\u0432\u043d\u044f\u0445, \u043a\u0440\u043e\u043c\u0435 \u00ab\u0414\u0435\u0431\u044e\u0442\u0430\u00bb, \u043d\u0443\u0436\u043d\u043e \u043f\u0440\u043e\u0439\u0442\u0438 3 \u043f\u043e\u043b\u044f. \u041a\u043e\u0433\u0434\u0430 \u0432\u0441\u0435 \u043c\u043e\u043d\u0435\u0442\u044b \u0438 \u0430\u043b\u043c\u0430\u0437\u044b \u0441\u043e\u0431\u0440\u0430\u043d\u044b, \u0432 \u0441\u0442\u0435\u043d\u0435 \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u0441\u0432\u0435\u0440\u043a\u0430\u044e\u0449\u0430\u044f \u0434\u044b\u0440\u0430 \u043d\u0430 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0435 \u043f\u043e\u043b\u0435.\n\n"
                + "\u041c\u043e\u043d\u0435\u0442\u0430: +1 \u043a \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u0443.\n"
                + "\u0410\u043b\u043c\u0430\u0437: +10 \u043a \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u0443.\n\n"
                + "P: \u041f\u043e\u0440\u0442\u0430\u043b, \u043c\u0435\u043d\u044f\u0435\u0442 \u0441\u0432\u044f\u0437\u044c \u0431\u043e\u043a\u043e\u0432\u044b\u0445 \u0434\u044b\u0440: \u0432\u0435\u0440\u0445\u043d\u044f\u044f \u043b\u0435\u0432\u0430\u044f \u043c\u043e\u0436\u0435\u0442 \u0432\u044b\u0432\u0435\u0441\u0442\u0438 \u0432 \u043d\u0438\u0436\u043d\u044e\u044e \u043f\u0440\u0430\u0432\u0443\u044e, \u0438 \u043d\u0430\u043e\u0431\u043e\u0440\u043e\u0442.\n"
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

    private void saveWin(GameResult result) {
        recordCompletedGameTime(result.startedAt);
        int rating = calculateRating(result);
        WinRecord record = new WinRecord(rating, result.difficulty.label, result.difficulty.name(), result.seconds, result.damage, result.wealth);
        List<WinRecord> records = loadWins(result.difficulty);
        if (records.size() < GameConfig.HISTORY_LIMIT || record.betterThan(records.get(records.size() - 1))) {
            records.add(record);
            sortWins(records);
            while (records.size() > GameConfig.HISTORY_LIMIT) {
                records.remove(records.size() - 1);
            }
            saveWins(result.difficulty, records);
        }
        addWealth(result.difficulty, result.wealth);
        updateAwards(result);
        refreshAwardHud();
    }

    private int calculateRating(GameResult result) {
        int timeBonus = Math.max(0, 360 - result.seconds) * 2;
        int healthBonus = (GameConfig.MAX_DAMAGE - result.damage) * 35;
        return Math.round((result.wealth + timeBonus + healthBonus) * result.difficulty.ratingMultiplier);
    }

    private void recordCompletedGameTime(long startedAt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startedAt > 0L ? startedAt : System.currentTimeMillis());
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour < 8) {
            prefs.edit().putInt(GameConfig.PREF_EARLY_GAMES, prefs.getInt(GameConfig.PREF_EARLY_GAMES, 0) + 1).apply();
        } else if (hour >= 20) {
            prefs.edit().putInt(GameConfig.PREF_LATE_GAMES, prefs.getInt(GameConfig.PREF_LATE_GAMES, 0) + 1).apply();
        }
    }

    private void addWealth(Difficulty difficulty, int wealth) {
        prefs.edit()
                .putInt(GameConfig.PREF_TOTAL_WEALTH, prefs.getInt(GameConfig.PREF_TOTAL_WEALTH, 0) + wealth)
                .putInt(levelWealthKey(difficulty), prefs.getInt(levelWealthKey(difficulty), 0) + wealth)
                .apply();
    }

    private List<WinRecord> loadWins(Difficulty difficulty) {
        ArrayList<WinRecord> records = new ArrayList<>();
        String raw = prefs.getString(winsKey(difficulty), "");
        if (raw == null || raw.isEmpty()) {
            raw = prefs.getString(GameConfig.PREF_WINS, "");
        }
        if (raw != null && !raw.isEmpty()) {
            String[] lines = raw.split("\n");
            for (String line : lines) {
                WinRecord record = WinRecord.decode(line);
                if (record != null && matchesDifficulty(record, difficulty)) {
                    records.add(record);
                }
            }
        }
        sortWins(records);
        while (records.size() > GameConfig.HISTORY_LIMIT) {
            records.remove(records.size() - 1);
        }
        return records;
    }

    private boolean matchesDifficulty(WinRecord record, Difficulty difficulty) {
        return difficulty.name().equals(record.difficultyKey) || difficulty.label.equals(record.difficulty);
    }

    private void saveWins(Difficulty difficulty, List<WinRecord> records) {
        StringBuilder encoded = new StringBuilder();
        for (WinRecord win : records) {
            if (encoded.length() > 0) {
                encoded.append("\n");
            }
            encoded.append(win.encode());
        }
        prefs.edit().putString(winsKey(difficulty), encoded.toString()).apply();
    }

    private void sortWins(List<WinRecord> records) {
        Collections.sort(records, new Comparator<WinRecord>() {
            @Override
            public int compare(WinRecord a, WinRecord b) {
                if (a.wealth != b.wealth) {
                    return Integer.compare(b.wealth, a.wealth);
                }
                if (a.seconds != b.seconds) {
                    return Integer.compare(a.seconds, b.seconds);
                }
                return Integer.compare(a.damage, b.damage);
            }
        });
    }

    private String winsKey(Difficulty difficulty) {
        return "wins_v2_" + difficulty.name();
    }

    private String levelWealthKey(Difficulty difficulty) {
        return "wealth_v1_" + difficulty.name();
    }

    private void showHistoryDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(panel);

        for (Difficulty difficulty : Difficulty.values()) {
            TextView title = panelText(difficulty.label);
            title.setTextSize(18f);
            title.setTypeface(null, 1);
            title.setPadding(0, dp(8), 0, dp(4));
            panel.addView(title);

            TableLayout table = new TableLayout(this);
            table.setStretchAllColumns(true);
            TableRow header = new TableRow(this);
            header.addView(tableCell("#", true));
            header.addView(tableCell("Богатство", true));
            header.addView(tableCell("Время", true));
            header.addView(tableCell("Урон", true));
            table.addView(header);

            List<WinRecord> records = loadWins(difficulty);
            for (int row = 0; row < GameConfig.HISTORY_LIMIT; row++) {
                TableRow tableRow = new TableRow(this);
                tableRow.addView(tableCell(String.valueOf(row + 1), false));
                if (row < records.size()) {
                    WinRecord win = records.get(row);
                    tableRow.addView(tableCell(String.valueOf(win.wealth), false));
                    tableRow.addView(tableCell(formatTime(win.seconds), false));
                    tableRow.addView(tableCell(win.damage + "/" + GameConfig.MAX_DAMAGE, false));
                } else {
                    tableRow.addView(tableCell("", false));
                    tableRow.addView(tableCell("", false));
                    tableRow.addView(tableCell("", false));
                }
                table.addView(tableRow);
            }
            panel.addView(table);
        }

        new AlertDialog.Builder(this)
                .setTitle("История побед")
                .setView(scroll)
                .setPositiveButton("Понятно", null)
                .show();
    }

    private TextView tableCell(String text, boolean header) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(header ? 14f : 12f);
        view.setTypeface(null, header ? 1 : 0);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(6), dp(8), dp(6));
        view.setMinWidth(dp(header ? 76 : 64));
        return view;
    }

    private void showAwardsDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        scroll.addView(panel);

        TextView total = panelText("Общее богатство: " + prefs.getInt(GameConfig.PREF_TOTAL_WEALTH, 0));
        total.setTextSize(18f);
        total.setTypeface(null, 1);
        panel.addView(total);

        for (Award award : awards()) {
            boolean unlocked = isAwardUnlocked(award.id);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(new AwardIconView(this, award, unlocked), new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView info = panelText((unlocked ? "Получено: " : "Не получено: ") + award.title + "\n" + award.condition);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            panel.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle("Награды")
                .setView(scroll)
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void updateAwards(GameResult result) {
        HashSet<String> unlocked = new HashSet<>(unlockedAwards());
        if (prefs.getInt(GameConfig.PREF_EARLY_GAMES, 0) >= 10) {
            unlocked.add("early");
        }
        if (prefs.getInt(GameConfig.PREF_LATE_GAMES, 0) >= 10) {
            unlocked.add("late");
        }
        if (result.won && result.fastField) {
            unlocked.add("flash");
        }
        if (result.won) {
            int levelWealth = prefs.getInt(levelWealthKey(result.difficulty), 0);
            for (Award award : awards()) {
                if (result.difficulty.name().equals(award.difficultyKey) && levelWealth >= award.threshold) {
                    unlocked.add(award.id);
                }
            }
        }
        saveUnlockedAwards(unlocked);
    }

    private List<String> unlockedAwards() {
        String raw = prefs.getString(GameConfig.PREF_AWARDS, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private void saveUnlockedAwards(Set<String> awards) {
        StringBuilder encoded = new StringBuilder();
        for (String award : awards) {
            if (encoded.length() > 0) {
                encoded.append(",");
            }
            encoded.append(award);
        }
        prefs.edit().putString(GameConfig.PREF_AWARDS, encoded.toString()).apply();
    }

    private boolean isAwardUnlocked(String id) {
        return unlockedAwards().contains(id);
    }

    private void refreshAwardHud() {
        ArrayList<String> symbols = new ArrayList<>();
        for (Award award : awards()) {
            if (isAwardUnlocked(award.id)) {
                symbols.add(award.id);
            }
        }
        if (gameView != null) {
            gameView.setAwardSymbols(symbols);
        }
    }

    private List<Award> awards() {
        ArrayList<Award> awards = new ArrayList<>();
        awards.add(new Award("debut_1", "Мешок серебряных денег", "Дебют: 2000 богатства", "D", Color.rgb(190, 205, 220), Difficulty.DEBUT.name(), 2000));
        awards.add(new Award("debut_2", "Двойной мешок серебряных денег", "Дебют: 4000 богатства", "D", Color.rgb(210, 220, 232), Difficulty.DEBUT.name(), 4000));
        awards.add(new Award("debut_3", "Двойной мешок с серебряной медалью", "Дебют: 7000 богатства", "D", Color.rgb(235, 238, 246), Difficulty.DEBUT.name(), 7000));
        awards.add(new Award("beginner_1", "Мешок золотых денег", "Начинающий: 6000 богатства", "N", Color.rgb(245, 200, 75), Difficulty.BEGINNER.name(), 6000));
        awards.add(new Award("beginner_2", "Двойной мешок золотых денег", "Начинающий: 9000 богатства", "N", Color.rgb(255, 215, 90), Difficulty.BEGINNER.name(), 9000));
        awards.add(new Award("beginner_3", "Двойной мешок с золотой медалью", "Начинающий: 12000 богатства", "N", Color.rgb(255, 230, 128), Difficulty.BEGINNER.name(), 12000));
        awards.add(new Award("amateur_1", "Мешок платиновых денег", "Любитель: 11000 богатства", "L", Color.rgb(180, 230, 235), Difficulty.AMATEUR.name(), 11000));
        awards.add(new Award("amateur_2", "Двойной мешок платиновых денег", "Любитель: 14000 богатства", "L", Color.rgb(160, 240, 245), Difficulty.AMATEUR.name(), 14000));
        awards.add(new Award("amateur_3", "Двойной мешок с платиновой медалью", "Любитель: 18000 богатства", "L", Color.rgb(130, 245, 255), Difficulty.AMATEUR.name(), 18000));
        awards.add(new Award("pro_1", "3 сияющих золотых слитка", "Профессионал: 17000 богатства", "P", Color.rgb(255, 196, 55), Difficulty.PRO.name(), 17000));
        awards.add(new Award("pro_2", "5 сияющих золотых слитков", "Профессионал: 20000 богатства", "P", Color.rgb(255, 180, 35), Difficulty.PRO.name(), 20000));
        awards.add(new Award("pro_3", "10 слитков и золотой бюст", "Профессионал: 25000 богатства", "P", Color.rgb(255, 160, 20), Difficulty.PRO.name(), 25000));
        awards.add(new Award("early", "Грабитель-жаворонок", "10 игр до 8 утра", "Ж", Color.rgb(255, 210, 95), null, 0));
        awards.add(new Award("late", "Грабитель-сова", "10 игр после 8 вечера", "С", Color.rgb(135, 160, 255), null, 0));
        awards.add(new Award("flash", "Флеш", "Быстро собрать поле: Дебют 1:10, Начинающий 2:00, Любитель и Профессионал 3:00", "F", Color.rgb(255, 80, 60), null, 0));
        return awards;
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

    private void showWinDialog(GameResult result) {
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

        TextView details = panelText("\u0420\u0435\u0439\u0442\u0438\u043d\u0433: " + calculateRating(result)
                + "\n\u0411\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u043e: " + result.wealth
                + "\n\u0412\u0440\u0435\u043c\u044f: " + formatTime(result.seconds)
                + "\n\u0423\u0440\u043e\u043d: " + result.damage + "/" + GameConfig.MAX_DAMAGE);
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

    private static final class Award {
        final String id;
        final String title;
        final String condition;
        final String symbol;
        final int color;
        final String difficultyKey;
        final int threshold;

        Award(String id, String title, String condition, String symbol, int color, String difficultyKey, int threshold) {
            this.id = id;
            this.title = title;
            this.condition = condition;
            this.symbol = symbol;
            this.color = color;
            this.difficultyKey = difficultyKey;
            this.threshold = threshold;
        }
    }

    private static final class AwardIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Award award;
        private final boolean unlocked;

        AwardIconView(Activity activity, Award award, boolean unlocked) {
            super(activity);
            this.award = award;
            this.unlocked = unlocked;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float r = Math.min(w, h) * 0.38f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(unlocked ? award.color : Color.rgb(80, 80, 80));
            canvas.drawCircle(w / 2f, h / 2f, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, r * 0.12f));
            paint.setColor(unlocked ? Color.WHITE : Color.rgb(150, 150, 150));
            canvas.drawCircle(w / 2f, h / 2f, r * 0.78f, paint);
            paint.setStyle(Paint.Style.FILL);
            int detail = unlocked ? Color.rgb(35, 42, 54) : Color.rgb(140, 140, 140);
            if (award.id.startsWith("pro_")) {
                drawIngots(canvas, w / 2f, h / 2f, r, detail);
            } else if ("early".equals(award.id)) {
                drawBird(canvas, w / 2f, h / 2f, r, detail, true);
            } else if ("late".equals(award.id)) {
                drawBird(canvas, w / 2f, h / 2f, r, detail, false);
            } else if ("flash".equals(award.id)) {
                drawLightning(canvas, w / 2f, h / 2f, r, detail);
            } else {
                drawMoneyBag(canvas, w / 2f, h / 2f, r, detail);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(r * 0.48f);
            paint.setColor(unlocked ? Color.WHITE : Color.rgb(190, 190, 190));
            canvas.drawText(award.symbol, w / 2f, h / 2f + r * 0.18f, paint);
            paint.setFakeBoldText(false);
        }

        private void drawMoneyBag(Canvas canvas, float cx, float cy, float r, int color) {
            paint.setColor(color);
            canvas.drawOval(cx - r * 0.52f, cy - r * 0.12f, cx + r * 0.52f, cy + r * 0.52f, paint);
            canvas.drawRect(cx - r * 0.26f, cy - r * 0.46f, cx + r * 0.26f, cy - r * 0.16f, paint);
            paint.setStrokeWidth(r * 0.12f);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx - r * 0.34f, cy - r * 0.18f, cx + r * 0.34f, cy - r * 0.18f, paint);
        }

        private void drawIngots(Canvas canvas, float cx, float cy, float r, int color) {
            paint.setColor(color);
            canvas.drawRect(cx - r * 0.55f, cy + r * 0.02f, cx + r * 0.05f, cy + r * 0.34f, paint);
            canvas.drawRect(cx - r * 0.1f, cy - r * 0.14f, cx + r * 0.5f, cy + r * 0.18f, paint);
            canvas.drawRect(cx - r * 0.34f, cy - r * 0.34f, cx + r * 0.26f, cy - r * 0.02f, paint);
        }

        private void drawBird(Canvas canvas, float cx, float cy, float r, int color, boolean morning) {
            paint.setColor(color);
            canvas.drawOval(cx - r * 0.44f, cy - r * 0.08f, cx + r * 0.38f, cy + r * 0.34f, paint);
            canvas.drawCircle(cx + r * 0.25f, cy - r * 0.24f, r * 0.22f, paint);
            canvas.drawOval(cx - r * 0.65f, cy - r * 0.28f, cx - r * 0.1f, cy + r * 0.12f, paint);
            paint.setColor(morning ? Color.rgb(255, 235, 140) : Color.rgb(210, 220, 255));
            canvas.drawCircle(cx + r * 0.32f, cy - r * 0.3f, r * 0.06f, paint);
        }

        private void drawLightning(Canvas canvas, float cx, float cy, float r, int color) {
            Path path = new Path();
            path.moveTo(cx + r * 0.12f, cy - r * 0.62f);
            path.lineTo(cx - r * 0.34f, cy + r * 0.02f);
            path.lineTo(cx + r * 0.02f, cy + r * 0.02f);
            path.lineTo(cx - r * 0.16f, cy + r * 0.58f);
            path.lineTo(cx + r * 0.44f, cy - r * 0.12f);
            path.lineTo(cx + r * 0.08f, cy - r * 0.12f);
            path.close();
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
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
