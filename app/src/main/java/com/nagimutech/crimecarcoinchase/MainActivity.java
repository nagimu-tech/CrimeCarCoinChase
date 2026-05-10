package com.nagimutech.crimecarcoinchase;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private FrameLayout root;
    private GameView gameView;
    private OnlineGameView onlineView;
    private SimpleWebSocketClient onlineClient;
    private SharedPreferences prefs;
    private Difficulty difficulty = Difficulty.BEGINNER;
    private int lastOnlineBanknoteEventId;
    private int lastOnlineWealthEventId;
    private int pendingBanknotes;
    private String onlineRoomCode = "";
    private int onlinePlayerId;
    private String onlineJoinCode;
    private boolean onlineReconnect;
    private boolean profEnabled;
    private boolean aiAllyEnabled;
    private int onlineOtherPlayerColor = Color.rgb(255, 135, 46);

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
        loadSavedColors();
        profEnabled = prefs.getBoolean(GameConfig.PREF_PROF_ENABLED, false);
        recalculateAwardsFromStoredProgress();
        setTitle("Погоня за монетами");

        root = new FrameLayout(this);
        root.setBackgroundColor(colors.background);
        gameView = new GameView(this, colors, this);
        gameView.setProfEnabled(profEnabled);
        gameView.setAvatar(prefs.getString(GameConfig.PREF_AVATAR, ""));
        gameView.setBanknotes(displayedBanknotes());
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        refreshAwardHud();
        showSplashScreen();
        checkForUpdates();
        refreshProfStatus();
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

    private void checkForUpdates() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(GameConfig.UPDATE_CHECK_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                connection.setRequestMethod("GET");
                if (connection.getResponseCode() != 200) {
                    return;
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }
                JSONObject json = new JSONObject(body.toString());
                int latestCode = json.optInt("versionCode", 0);
                String latestName = json.optString("versionName", "");
                String apkUrl = json.optString("apkUrl", "");
                if (latestCode > GameConfig.APP_VERSION_CODE && !apkUrl.isEmpty()) {
                    runOnUiThread(() -> showUpdateDialog(latestName, apkUrl));
                }
            } catch (Exception ignored) {
                // Update checks must never prevent offline play.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "update-check").start();
    }

    private void showUpdateDialog(String latestName, String apkUrl) {
        if (isFinishing()) {
            return;
        }
        String version = latestName == null || latestName.isEmpty() ? "новая версия" : latestName;
        new AlertDialog.Builder(this)
                .setTitle("Доступно обновление")
                .setMessage("Можно скачать версию " + version + ". Данные игры сохранятся, если установить APK поверх текущей версии.")
                .setPositiveButton("Скачать", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    startActivity(intent);
                })
                .setNegativeButton("Позже", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        disconnectOnline();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (onlineView != null && !onlineRoomCode.isEmpty() && onlinePlayerId > 0) {
            onlineReconnect = true;
            startOnlineSession(GameConfig.MULTIPLAYER_WS_URL, onlineJoinCode);
        }
    }

    @Override
    public void onHudChanged(int score, int total, int damage) {
        // HUD is drawn directly by GameView in the full-screen version.
    }

    @Override
    public void onRoundStarted(Difficulty difficulty) {
        pendingBanknotes = 0;
        updateBanknoteHud();
    }

    @Override
    public void onFieldCompleted(Difficulty difficulty) {
        addPendingBanknotes(banknotesFor(difficulty));
    }

    @Override
    public void onBankBonus(Difficulty difficulty, int banknotes) {
        addPendingBanknotes(banknotes);
    }

    @Override
    public void onWin(GameResult result) {
        commitPendingBanknotes();
        saveWin(result);
        showWinDialog(result);
    }

    @Override
    public void onLose(GameResult result) {
        recordCompletedGameTime(result.startedAt);
        pendingBanknotes = 0;
        updateBanknoteHud();
        updateAwards(result);
        refreshAwardHud();
        showLoseDialog(result);
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
        spinner.setSelection(Math.max(0, visibleDifficulties().indexOf(difficulty)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                difficulty = visibleDifficulties().get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        panel.addView(spinner, new LinearLayout.LayoutParams(-1, dp(52)));

        Switch prof = new Switch(this);
        prof.setText("ПРОФ-режим");
        prof.setTextColor(Color.WHITE);
        prof.setTextSize(16f);
        prof.setChecked(profEnabled);
        prof.setOnCheckedChangeListener((buttonView, isChecked) -> setProfMode(isChecked));
        panel.addView(prof, new LinearLayout.LayoutParams(-1, dp(48)));

        Button avatar = new Button(this);
        avatar.setText("Аватар");
        avatar.setAllCaps(false);
        avatar.setEnabled(profEnabled);
        avatar.setOnClickListener(v -> showAvatarDialog());
        panel.addView(avatar, new LinearLayout.LayoutParams(-1, dp(46)));

        Switch ally = new Switch(this);
        ally.setText("Играть вдвоём с компьютером");
        ally.setTextColor(Color.WHITE);
        ally.setTextSize(16f);
        ally.setEnabled(profEnabled);
        ally.setChecked(aiAllyEnabled);
        ally.setOnCheckedChangeListener((buttonView, isChecked) -> {
            aiAllyEnabled = isChecked && profEnabled;
            gameView.setAiAllyEnabled(aiAllyEnabled);
        });
        panel.addView(ally, new LinearLayout.LayoutParams(-1, dp(48)));

        if (System.currentTimeMillis() < 0L) {
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
        }

        Button shop = new Button(this);
        shop.setText("Магазин");
        shop.setAllCaps(false);
        shop.setOnClickListener(v -> showShopDialog());
        panel.addView(shop, new LinearLayout.LayoutParams(-1, dp(46)));

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

    private void setProfMode(boolean enabled) {
        syncProfStatus(enabled);
    }

    private void refreshProfStatus() {
        new Thread(() -> {
            try {
                String installId = ensureInstallId();
                JSONObject result = getJson(GameConfig.PROF_STATUS_URL + "?installId=" + URLEncoder.encode(installId, "UTF-8"));
                boolean enabled = result.optBoolean("enabled", profEnabled);
                runOnUiThread(() -> applyProfMode(enabled));
            } catch (Exception ignored) {
            }
        }, "prof-status").start();
    }

    private void applyProfMode(boolean enabled) {
        profEnabled = enabled;
        prefs.edit().putBoolean(GameConfig.PREF_PROF_ENABLED, enabled).apply();
        if (!profEnabled && difficulty == Difficulty.LEGEND) {
            difficulty = Difficulty.BEGINNER;
        }
        if (!profEnabled) {
            aiAllyEnabled = false;
        }
        gameView.setProfEnabled(profEnabled);
        gameView.setAiAllyEnabled(aiAllyEnabled);
        if (System.currentTimeMillis() >= 0L) {
            return;
        }
        Toast.makeText(this, profEnabled ? "ПРОФ-режим включён" : "ПРОФ-режим выключен", Toast.LENGTH_SHORT).show();
    }

    private void syncProfStatus(boolean requestedEnabled) {
        new Thread(() -> {
            try {
                String installId = ensureInstallId();
                JSONObject result = postJson(GameConfig.PROF_SET_TEST_URL, "{\"installId\":\"" + installId + "\",\"enabled\":" + requestedEnabled + "}");
                boolean enabled = result.optBoolean("enabled", requestedEnabled);
                runOnUiThread(() -> applyProfMode(enabled));
            } catch (Exception e) {
                runOnUiThread(() -> applyProfMode(requestedEnabled || profEnabled));
            }
        }, "prof-sync").start();
    }

    private String ensureInstallId() throws Exception {
        String existing = prefs.getString(GameConfig.PREF_PROF_INSTALL_ID, "");
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        JSONObject json = postJson(GameConfig.PROF_REGISTER_URL, "{}");
        String installId = json.optString("installId", "");
        if (installId.isEmpty()) {
            throw new IllegalStateException("installId empty");
        }
        prefs.edit().putString(GameConfig.PREF_PROF_INSTALL_ID, installId).apply();
        return installId;
    }

    private JSONObject getJson(String rawUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(3500);
        connection.setRequestMethod("GET");
        try {
            return readJson(connection);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject postJson(String rawUrl, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(rawUrl).openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(3500);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try {
            return readJson(connection);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject readJson(HttpURLConnection connection) throws Exception {
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new IllegalStateException("HTTP " + connection.getResponseCode());
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return new JSONObject(body.toString());
    }

    private void showAvatarDialog() {
        showAvatarEditorDialog();
    }

    private void showAvatarEditorDialog() {
        int[] values = AvatarRenderer.decode(prefs.getString(GameConfig.PREF_AVATAR, "0,0,0,0,0,0,0,0,0,0"));
        int[] selected = {0};
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dialogWidth = Math.min(metrics.widthPixels - dp(32), dp(540));
        int optionWidth = Math.max(dp(116), (dialogWidth - dp(66)) / 2);
        LinearLayout rootPanel = new LinearLayout(this);
        rootPanel.setOrientation(LinearLayout.VERTICAL);
        rootPanel.setBackgroundColor(Color.rgb(12, 28, 32));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackgroundColor(Color.rgb(9, 24, 30));
        TextView close = panelText("×");
        close.setTextSize(34f);
        close.setGravity(Gravity.CENTER);
        TextView title = panelText("Изменить аватар");
        title.setTextSize(19f);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        TextView done = panelText("ГОТОВО");
        done.setTextSize(17f);
        done.setTypeface(null, 1);
        done.setTextColor(Color.rgb(80, 205, 255));
        done.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(56), dp(52)));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
        header.addView(done, new LinearLayout.LayoutParams(dp(96), dp(52)));
        rootPanel.addView(header);

        AvatarView preview = new AvatarView(this, values);
        rootPanel.addView(preview, new LinearLayout.LayoutParams(-1, dp(250)));

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(8), 0, dp(8), 0);
        tabsScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, dp(58)));
        rootPanel.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView optionsScroll = new ScrollView(this);
        optionsScroll.setFillViewport(false);
        optionsScroll.setClipToPadding(false);
        LinearLayout optionsPanel = new LinearLayout(this);
        optionsPanel.setOrientation(LinearLayout.VERTICAL);
        optionsPanel.setPadding(dp(14), dp(10), dp(14), dp(34));
        optionsScroll.addView(optionsPanel);
        rootPanel.addView(optionsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        String[] tabIcons = {"♟", "◡", "〰", "☻", "◉", "●", "▢", "◌", "○", "▣"};
        String[] sectionTitles = {"Форма головы", "Тело", "Причёска", "Рот", "Выражение лица", "Цвет глаз", "Очки", "Тип лица", "Серьги", "Цвет кожи"};
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            tabs.removeAllViews();
            for (int i = 0; i < AvatarRenderer.CATEGORY_COUNT; i++) {
                final int index = i;
                TextView tab = panelText(tabIcons[i]);
                tab.setTextSize(25f);
                tab.setGravity(Gravity.CENTER);
                tab.setTextColor(index == selected[0] ? Color.rgb(83, 210, 255) : Color.rgb(91, 111, 118));
                tab.setOnClickListener(v -> {
                    selected[0] = index;
                    rebuild[0].run();
                });
                tabs.addView(tab, new LinearLayout.LayoutParams(dp(58), dp(54)));
            }
            optionsPanel.removeAllViews();
            TextView section = panelText(sectionTitles[selected[0]]);
            section.setTextSize(19f);
            section.setTypeface(null, 1);
            section.setTextColor(Color.WHITE);
            optionsPanel.addView(section, new LinearLayout.LayoutParams(-1, dp(44)));
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(2);
            int count = AvatarRenderer.OPTION_COUNTS[selected[0]];
            for (int option = 0; option < count; option++) {
                final int value = option;
                AvatarOptionView optionView = new AvatarOptionView(this, values, selected[0], value);
                optionView.setChosen(value == values[selected[0]]);
                optionView.setOnClickListener(v -> {
                    values[selected[0]] = value;
                    preview.setValues(values);
                    String encoded = AvatarRenderer.encode(values);
                    prefs.edit().putString(GameConfig.PREF_AVATAR, encoded).apply();
                    gameView.setAvatar(encoded);
                    rebuild[0].run();
                });
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = optionWidth;
                lp.height = dp(126);
                lp.setMargins(dp(5), dp(6), dp(5), dp(10));
                grid.addView(optionView, lp);
            }
            optionsPanel.addView(grid);
        };
        rebuild[0].run();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(rootPanel).create();
        close.setOnClickListener(v -> dialog.dismiss());
        done.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(dialogWidth, Math.min(metrics.heightPixels - dp(42), dp(760)));
        }
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

    private void showSplashScreen() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Color.BLACK);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.splash_image);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splash.addView(image, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(70, 0, 0, 0));
        splash.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(34), dp(24), dp(42));
        if (profEnabled) {
            TextView prof = panelText("ПРОФ");
            prof.setGravity(Gravity.CENTER);
            prof.setTextSize(17f);
            prof.setTypeface(null, 1);
            prof.setTextColor(Color.rgb(125, 224, 255));
            prof.setShadowLayer(9f, 0f, 2f, Color.rgb(5, 12, 24));
            prof.setBackground(glassBackground(Color.argb(50, 4, 22, 34), Color.argb(110, 110, 220, 255), dp(15), 1));
            prof.setPadding(dp(16), dp(4), dp(16), dp(5));
            LinearLayout.LayoutParams profParams = new LinearLayout.LayoutParams(-2, -2);
            profParams.topMargin = dp(4);
            profParams.bottomMargin = dp(8);
            content.addView(prof, profParams);
        }
        TextView title = panelText("Погоня за монетами");
        title.setGravity(Gravity.CENTER);
        title.setTextSize(28f);
        title.setTypeface(null, 1);
        title.setTextColor(Color.rgb(255, 232, 178));
        title.setShadowLayer(10f, 0f, 3f, Color.rgb(5, 16, 35));
        title.setBackground(glassBackground(Color.argb(82, 8, 20, 38), Color.argb(130, 255, 212, 120), dp(18), 1));
        title.setPadding(dp(14), dp(8), dp(14), dp(10));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(16);
        content.addView(title, titleParams);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button play = new Button(this);
        play.setText("Играть одному");
        play.setAllCaps(false);
        styleSplashButton(play);
        content.addView(play, new LinearLayout.LayoutParams(-1, dp(54)));

        Button multiplayer = new Button(this);
        multiplayer.setText("Играть вдвоём");
        multiplayer.setAllCaps(false);
        styleSplashButton(multiplayer);
        LinearLayout.LayoutParams multiplayerParams = new LinearLayout.LayoutParams(-1, dp(54));
        multiplayerParams.topMargin = dp(10);
        content.addView(multiplayer, multiplayerParams);
        splash.addView(content, new FrameLayout.LayoutParams(-1, -1));

        play.setOnClickListener(v -> root.removeView(splash));
        multiplayer.setOnClickListener(v -> showMultiplayerChoice(splash));
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
    }

    private void styleSplashButton(Button button) {
        button.setTextColor(Color.rgb(255, 232, 178));
        button.setTextSize(18f);
        button.setTypeface(null, 1);
        button.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        button.setBackground(glassBackground(Color.argb(138, 6, 18, 34), Color.argb(165, 120, 206, 255), dp(14), 1));
    }

    private GradientDrawable glassBackground(int fill, int stroke, int radius, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(strokeWidthDp), stroke);
        return drawable;
    }

    private void showMultiplayerChoice(FrameLayout splash) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(8));

        TextView hint = panelText("Подключение к серверу Beget настроено автоматически. Создайте игру или войдите по 5-значному коду второго игрока.");
        panel.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        Button create = new Button(this);
        create.setText("Создать игру");
        create.setAllCaps(false);
        panel.addView(create, new LinearLayout.LayoutParams(-1, dp(48)));

        Button join = new Button(this);
        join.setText("Войти по коду");
        join.setAllCaps(false);
        panel.addView(join, new LinearLayout.LayoutParams(-1, dp(48)));

        Button palette = new Button(this);
        palette.setText("Палитра перед игрой");
        palette.setAllCaps(false);
        panel.addView(palette, new LinearLayout.LayoutParams(-1, dp(48)));

        Button aiAlly = new Button(this);
        aiAlly.setText("Играть вдвоём с компьютером (ПРОФ)");
        aiAlly.setAllCaps(false);
        panel.addView(aiAlly, new LinearLayout.LayoutParams(-1, dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Играть вдвоём")
                .setView(panel)
                .setNegativeButton("Отмена", null)
                .create();
        create.setOnClickListener(v -> {
            dialog.dismiss();
            root.removeView(splash);
            startOnlineSession(GameConfig.MULTIPLAYER_WS_URL, null);
        });
        palette.setOnClickListener(v -> showOnlinePaletteDialog());
        aiAlly.setOnClickListener(v -> {
            if (!profEnabled) {
                Toast.makeText(this, "Режим с компьютером доступен в ПРОФ", Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            root.removeView(splash);
            pendingBanknotes = 0;
            updateBanknoteHud();
            aiAllyEnabled = true;
            gameView.setProfEnabled(true);
            gameView.setAiAllyEnabled(true);
            gameView.start(difficulty);
        });
        join.setOnClickListener(v -> {
            dialog.dismiss();
            showJoinCodeDialog(splash, GameConfig.MULTIPLAYER_WS_URL);
        });
        dialog.show();
    }

    private void showOnlinePaletteDialog() {
        String[] items = {"Фон", "Стены", "Моя машинка", "Машинка второго игрока", "Полиция"};
        new AlertDialog.Builder(this)
                .setTitle("Палитра онлайн-игры")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showColorDialog("background");
                    } else if (which == 1) {
                        showColorDialog("wall");
                    } else if (which == 2) {
                        showColorDialog("player");
                    } else if (which == 3) {
                        showOtherPlayerColorDialog();
                    } else {
                        showColorDialog("police");
                    }
                })
                .show();
    }

    private void showOtherPlayerColorDialog() {
        String[] labels = {"Оранжевый", "Синий", "Фиолетовый", "Красный", "Зеленый", "Желтый"};
        int[] values = {
                Color.rgb(255, 135, 46),
                Color.rgb(47, 125, 225),
                Color.rgb(143, 61, 112),
                Color.rgb(217, 54, 54),
                Color.rgb(48, 155, 96),
                Color.rgb(245, 200, 75)
        };
        new AlertDialog.Builder(this)
                .setTitle("Машинка второго игрока")
                .setItems(labels, (dialog, which) -> {
                    onlineOtherPlayerColor = values[which];
                    if (onlineView != null) {
                        onlineView.setOtherPlayerColor(onlineOtherPlayerColor);
                    }
                })
                .show();
    }

    private void showJoinCodeDialog(FrameLayout splash, String url) {
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setHint("5 цифр");
        code.setPadding(dp(18), dp(8), dp(18), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Введите код игры")
                .setView(code)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Войти", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = code.getText().toString().trim();
            if (!value.matches("\\d{5}")) {
                code.setError("Нужно ровно 5 цифр");
                return;
            }
            dialog.dismiss();
            root.removeView(splash);
            startOnlineSession(url, value);
        }));
        dialog.show();
    }

    private void startOnlineSession(String url, String joinCode) {
        boolean reconnecting = onlineReconnect && onlineView != null && !onlineRoomCode.isEmpty() && onlinePlayerId > 0;
        onlineReconnect = false;
        if (!reconnecting) {
            disconnectOnline();
        } else if (onlineClient != null) {
            onlineClient.close();
            onlineClient = null;
        }
        onlineJoinCode = joinCode;
        if (!reconnecting) {
            pendingBanknotes = 0;
            updateBanknoteHud();
        }
        if (onlineView == null) {
            onlineView = new OnlineGameView(this, colors, new OnlineGameView.Listener() {
                @Override
                public void onDirection(Direction direction) {
                    sendOnlineAsync("{\"type\":\"input\",\"direction\":\"" + direction.name() + "\"}");
                }

                @Override
                public void onMenuRequested() {
                    showOnlineMenu();
                }
            });
        }
        onlineView.setOtherPlayerColor(onlineOtherPlayerColor);
        onlineView.resetInputState();
        onlineView.setStatus(reconnecting ? "Повторное подключение..." : joinCode == null ? "Создание комнаты..." : "Вход по коду...");
        lastOnlineBanknoteEventId = 0;
        lastOnlineWealthEventId = 0;
        onlineView.setBanknotes(displayedBanknotes());
        refreshAwardHud();
        if (!reconnecting) {
            root.removeAllViews();
            root.addView(onlineView, new FrameLayout.LayoutParams(-1, -1));
        }

        onlineClient = new SimpleWebSocketClient(url, new SimpleWebSocketClient.Listener() {
            @Override
            public void onOpen() {
                runOnUiThread(() -> {
                    if (onlineView != null) {
                        onlineView.setStatus("Соединение установлено");
                    }
                });
                if (reconnecting && !onlineRoomCode.isEmpty() && onlinePlayerId > 0) {
                    sendOnline("{\"type\":\"resumeRoom\",\"code\":\"" + onlineRoomCode + "\",\"playerId\":" + onlinePlayerId + ",\"avatar\":" + JSONObject.quote(prefs.getString(GameConfig.PREF_AVATAR, "")) + "}");
                } else if (joinCode == null) {
                    sendOnline("{\"type\":\"createRoom\",\"difficulty\":\"" + difficulty.name() + "\",\"profEnabled\":" + profEnabled + ",\"avatar\":" + JSONObject.quote(prefs.getString(GameConfig.PREF_AVATAR, "")) + "}");
                } else {
                    sendOnline("{\"type\":\"joinRoom\",\"code\":\"" + joinCode + "\",\"avatar\":" + JSONObject.quote(prefs.getString(GameConfig.PREF_AVATAR, "")) + "}");
                }
            }

            @Override
            public void onText(String text) {
                handleOnlineMessage(text);
            }

            @Override
            public void onClosed(String reason) {
                runOnUiThread(() -> {
                    onlineClient = null;
                    if (onlineView != null) {
                        onlineView.setStatus("Соединение закрыто");
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    onlineClient = null;
                    if (onlineView != null) {
                        onlineView.setStatus("Ошибка сети: " + message);
                    }
                    Toast.makeText(MainActivity.this, "Мультиплеер: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
        onlineClient.connect();
    }

    private void handleOnlineMessage(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            JSONObject message = new JSONObject(text);
            String type = message.optString("type");
            runOnUiThread(() -> {
                try {
                    if (onlineView == null) {
                        return;
                    }
                    if ("roomCreated".equals(type)) {
                        String code = message.optString("code");
                        onlineRoomCode = code;
                        onlinePlayerId = message.optInt("playerId", 1);
                        onlineView.setRoomCode(code);
                        onlineView.setPlayerId(onlinePlayerId);
                        onlineView.setStatus("Ожидание второго игрока");
                        showRoomCodeDialog(code);
                    } else if ("joined".equals(type)) {
                        onlineRoomCode = message.optString("code");
                        onlinePlayerId = message.optInt("playerId", 2);
                        onlineView.setRoomCode(onlineRoomCode);
                        onlineView.setPlayerId(onlinePlayerId);
                        onlineView.setStatus("Игрок подключился");
                    } else if ("state".equals(type)) {
                        JSONObject state = message.getJSONObject("state");
                        onlineView.applyState(state);
                    } else if ("bankBonus".equals(type) || "fieldBonus".equals(type)) {
                        int eventId = message.optInt("eventId", 0);
                        int reward = message.optInt("banknotes", 0);
                        if (eventId > lastOnlineBanknoteEventId && reward > 0) {
                            lastOnlineBanknoteEventId = eventId;
                            addPendingBanknotes(reward);
                        }
                    } else if ("wealthBonus".equals(type)) {
                        int eventId = message.optInt("eventId", 0);
                        int wealth = message.optInt("wealth", 0);
                        if (eventId > lastOnlineWealthEventId && wealth > 0) {
                            lastOnlineWealthEventId = eventId;
                        }
                    } else if ("gameOver".equals(type)) {
                        onlineView.setStatus(message.optString("message", "Игра завершена"));
                        GameResult result = onlineResult(message.optBoolean("won", false), message.optJSONObject("result"));
                        if (result.won) {
                            commitPendingBanknotes();
                            saveWin(result);
                            showWinDialog(result);
                        } else {
                            onLose(result);
                        }
                    } else if ("peerPaused".equals(type)) {
                        onlineView.setStatus(message.optString("message", "Второй игрок временно отключился"));
                    } else if ("error".equals(type)) {
                        onlineView.setStatus("Ошибка: " + message.optString("message"));
                        Toast.makeText(this, message.optString("message"), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    onlineView.setStatus("Ошибка данных сервера");
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                if (onlineView != null) {
                    onlineView.setStatus("Непонятный ответ сервера");
                }
            });
        }
    }

    private GameResult onlineResult(boolean won, JSONObject raw) {
        Difficulty resultDifficulty = raw == null ? difficulty : parseDifficulty(raw.optString("difficulty"), difficulty);
        int seconds = raw == null ? onlineView.elapsedSeconds() : raw.optInt("seconds", onlineView.elapsedSeconds());
        boolean fastField = raw != null && raw.optBoolean("fastField", false);
        return new GameResult(resultDifficulty, onlineView.myScore(), onlineView.myTotal(), Math.max(1, seconds), onlineView.myDamage(), System.currentTimeMillis() - Math.max(1, seconds) * 1000L, fastField, won);
    }

    private void showRoomCodeDialog(String code) {
        new AlertDialog.Builder(this)
                .setTitle("Код игры")
                .setMessage("Скажите второму игроку код: " + code)
                .setPositiveButton("Ждать", null)
                .show();
    }

    private void showOnlineMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Онлайн-игра")
                .setItems(new String[]{"Продолжить", "Отключиться и вернуться на заставку"}, (dialog, which) -> {
                    if (which == 1) {
                        disconnectOnline();
                        root.removeAllViews();
                        gameView = new GameView(this, colors, this);
                        gameView.setBanknotes(banknotes());
                        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
                        refreshAwardHud();
                        showSplashScreen();
                    }
                })
                .show();
    }

    private void sendOnline(String json) {
        if (onlineClient != null) {
            onlineClient.sendText(json);
        }
    }

    private void sendOnlineAsync(String json) {
        new Thread(() -> sendOnline(json), "crime-car-online-send").start();
    }

    private void disconnectOnline() {
        if (onlineClient != null) {
            onlineClient.close();
            onlineClient = null;
        }
        onlineView = null;
    }

    private void showShopDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        scroll.addView(panel);

        TextView money = panelText("Банкноты: " + banknotes());
        money.setTextSize(20f);
        money.setTypeface(null, 1);
        panel.addView(money);

        final AlertDialog[] holder = new AlertDialog[1];
        addShopCategory(panel, "Фон", "background", holder);
        addShopCategory(panel, "Стены", "wall", holder);
        addShopCategory(panel, "Преступник", "player", holder);
        addShopCategory(panel, "Полиция", "police", holder);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Магазин")
                .setView(scroll)
                .setPositiveButton("Понятно", null)
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    private void addShopCategory(LinearLayout panel, String title, String category, AlertDialog[] holder) {
        TextView heading = panelText(title);
        heading.setTextSize(18f);
        heading.setTypeface(null, 1);
        heading.setPadding(0, dp(12), 0, dp(4));
        panel.addView(heading);

        for (ShopItem item : shopItems(category)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View swatch = new View(this);
            swatch.setBackgroundColor(item.color);
            row.addView(swatch, new LinearLayout.LayoutParams(dp(34), dp(34)));

            TextView label = panelText(item.name + (item.cost > 0 ? " - " + item.cost : " - бесплатно"));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

            Button action = new Button(this);
            action.setAllCaps(false);
            boolean owned = isShopItemOwned(item);
            boolean selected = item.id.equals(selectedShopId(item.category));
            if (selected) {
                action.setText("Выбрано");
                action.setEnabled(false);
            } else if (owned) {
                action.setText("Выбрать");
                action.setOnClickListener(v -> {
                    selectShopItem(item);
                    holder[0].dismiss();
                    showShopDialog();
                });
            } else if (banknotes() >= item.cost) {
                action.setText("Купить");
                action.setOnClickListener(v -> {
                    buyShopItem(item);
                    holder[0].dismiss();
                    showShopDialog();
                });
            } else {
                action.setText("Не хватает");
                action.setEnabled(false);
            }
            row.addView(action, new LinearLayout.LayoutParams(dp(112), dp(44)));
            panel.addView(row);
        }
    }

    private List<ShopItem> shopItems(String category) {
        ArrayList<ShopItem> items = new ArrayList<>();
        if ("background".equals(category)) {
            items.add(new ShopItem("background_default", category, "Стандарт", colors.defaultBackground(), 0));
            items.add(new ShopItem("background_purple", category, "Фиолетовый", Color.rgb(93, 55, 138), 5));
            items.add(new ShopItem("background_red", category, "Красный", Color.rgb(132, 43, 48), 8));
            items.add(new ShopItem("background_green", category, "Зеленый", Color.rgb(33, 112, 70), 13));
            items.add(new ShopItem("background_yellow", category, "Желтый", Color.rgb(128, 110, 34), 16));
            items.add(new ShopItem("background_cyan", category, "Голубой", Color.rgb(37, 104, 142), 18));
            items.add(new ShopItem("background_dark", category, "Тёмный", Color.rgb(17, 21, 28), 22));
        } else if ("wall".equals(category)) {
            items.add(new ShopItem("wall_default", category, "Стандарт", colors.defaultWall(), 0));
            items.add(new ShopItem("wall_blue", category, "Синий", Color.rgb(52, 98, 190), 5));
            items.add(new ShopItem("wall_red", category, "Красный", Color.rgb(190, 56, 64), 8));
            items.add(new ShopItem("wall_green", category, "Зеленый", Color.rgb(55, 165, 98), 13));
            items.add(new ShopItem("wall_yellow", category, "Желтый", Color.rgb(215, 183, 62), 16));
            items.add(new ShopItem("wall_cyan", category, "Голубой", Color.rgb(64, 178, 210), 18));
            items.add(new ShopItem("wall_dark", category, "Тёмный", Color.rgb(45, 48, 58), 22));
        } else if ("player".equals(category)) {
            items.add(new ShopItem("player_default", category, "Стандарт", colors.defaultPlayer(), 0));
            items.add(new ShopItem("player_blue", category, "Синий", Color.rgb(45, 120, 225), 5));
            items.add(new ShopItem("player_purple", category, "Фиолетовый", Color.rgb(150, 75, 210), 10));
            items.add(new ShopItem("player_green", category, "Зеленый", Color.rgb(42, 174, 92), 14));
            items.add(new ShopItem("player_yellow", category, "Желтый", Color.rgb(236, 197, 54), 19));
            items.add(new ShopItem("player_cyan", category, "Голубой", Color.rgb(62, 194, 230), 24));
            items.add(new ShopItem("player_dark", category, "Тёмный", Color.rgb(35, 38, 48), 27));
        } else if ("police".equals(category)) {
            items.add(new ShopItem("police_default", category, "Стандарт", colors.defaultPolice(), 0));
            items.add(new ShopItem("police_blue", category, "Синий", Color.rgb(47, 125, 225), 5));
            items.add(new ShopItem("police_purple", category, "Фиолетовый", Color.rgb(145, 82, 215), 10));
            items.add(new ShopItem("police_red", category, "Красный", Color.rgb(215, 55, 60), 14));
            items.add(new ShopItem("police_green", category, "Зеленый", Color.rgb(52, 165, 94), 19));
            items.add(new ShopItem("police_yellow", category, "Желтый", Color.rgb(230, 190, 56), 24));
            items.add(new ShopItem("police_dark", category, "Тёмный", Color.rgb(32, 36, 46), 27));
        }
        return items;
    }

    private void showHelpDialog() {
        showRichHelpDialog();
        if (System.currentTimeMillis() >= 0L) {
            return;
        }
        String text = "\u0426\u0435\u043b\u044c: \u0441\u043e\u0431\u0440\u0430\u0442\u044c \u0432\u0441\u0435 \u043c\u043e\u043d\u0435\u0442\u044b \u0438 \u0430\u043b\u043c\u0430\u0437\u044b, \u043d\u0435 \u043f\u043e\u043b\u0443\u0447\u0438\u0432 5 \u0443\u0440\u043e\u043d\u043e\u0432.\n\n"
                + "\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435: \u043a\u043e\u0441\u043d\u0438\u0441\u044c \u044d\u043a\u0440\u0430\u043d\u0430 \u0438 \u0432\u0435\u0434\u0438 \u043f\u0430\u043b\u0435\u0446 \u0432\u0432\u0435\u0440\u0445, \u0432\u043d\u0438\u0437, \u0432\u043b\u0435\u0432\u043e \u0438\u043b\u0438 \u0432\u043f\u0440\u0430\u0432\u043e. \u0412\u0435\u0440\u043d\u0438 \u043f\u0430\u043b\u0435\u0446 \u043a \u0446\u0435\u043d\u0442\u0440\u0443 \u0436\u0435\u0441\u0442\u0430 \u0438\u043b\u0438 \u043e\u0442\u043f\u0443\u0441\u0442\u0438, \u0447\u0442\u043e\u0431\u044b \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c\u0441\u044f.\n\n"
                + "\u041d\u0430 \u0432\u0441\u0435\u0445 \u0443\u0440\u043e\u0432\u043d\u044f\u0445, \u043a\u0440\u043e\u043c\u0435 \u00ab\u0414\u0435\u0431\u044e\u0442\u0430\u00bb, \u043d\u0443\u0436\u043d\u043e \u043f\u0440\u043e\u0439\u0442\u0438 3 \u043f\u043e\u043b\u044f. \u041a\u043e\u0433\u0434\u0430 \u0432\u0441\u0435 \u043c\u043e\u043d\u0435\u0442\u044b \u0438 \u0430\u043b\u043c\u0430\u0437\u044b \u0441\u043e\u0431\u0440\u0430\u043d\u044b, \u0432 \u0441\u0442\u0435\u043d\u0435 \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u0441\u0432\u0435\u0440\u043a\u0430\u044e\u0449\u0430\u044f \u0434\u044b\u0440\u0430 \u043d\u0430 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0435\u0435 \u043f\u043e\u043b\u0435.\n\n"
                + "\u041c\u043e\u043d\u0435\u0442\u0430: +1 \u043a \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u0443.\n"
                + "\u0410\u043b\u043c\u0430\u0437: +10 \u043a \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u0443.\n\n"
                + "P: \u041f\u043e\u0440\u0442\u0430\u043b, \u043c\u0435\u043d\u044f\u0435\u0442 \u0441\u0432\u044f\u0437\u044c \u0431\u043e\u043a\u043e\u0432\u044b\u0445 \u0434\u044b\u0440: \u0432\u0435\u0440\u0445\u043d\u044f\u044f \u043b\u0435\u0432\u0430\u044f \u043c\u043e\u0436\u0435\u0442 \u0432\u044b\u0432\u0435\u0441\u0442\u0438 \u0432 \u043d\u0438\u0436\u043d\u044e\u044e \u043f\u0440\u0430\u0432\u0443\u044e, \u0438 \u043d\u0430\u043e\u0431\u043e\u0440\u043e\u0442.\n"
                + "F: \u0424\u0440\u0438\u0437\u0435\u0440, \u0437\u0430\u043c\u043e\u0440\u0430\u0436\u0438\u0432\u0430\u0435\u0442 \u043f\u043e\u043b\u0438\u0446\u0438\u044e \u043d\u0430 20 \u0441\u0435\u043a\u0443\u043d\u0434.\n"
                + "S: \u0417\u0430\u0449\u0438\u0442\u0430, \u043d\u0430 20 \u0441\u0435\u043a\u0443\u043d\u0434 \u0443\u0431\u0438\u0440\u0430\u0435\u0442 \u0443\u0440\u043e\u043d \u043e\u0442 \u043f\u043e\u043b\u0438\u0446\u0438\u0438. \u041c\u0430\u0448\u0438\u043d\u043a\u0430 \u0441\u0432\u0435\u0442\u0438\u0442\u0441\u044f \u043f\u0443\u043b\u044c\u0441\u0438\u0440\u0443\u044e\u0449\u0435\u0439 \u043e\u043a\u0430\u043d\u0442\u043e\u0432\u043a\u043e\u0439.\n"
                + "G: \u041f\u0440\u0438\u0437\u0440\u0430\u043a, \u043d\u0430 7 \u0441\u0435\u043a\u0443\u043d\u0434 \u043f\u043e\u0437\u0432\u043e\u043b\u044f\u0435\u0442 \u0435\u0445\u0430\u0442\u044c \u0441\u043a\u0432\u043e\u0437\u044c \u0441\u0442\u0435\u043d\u044b. \u041c\u0430\u0448\u0438\u043d\u043a\u0430 \u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0441\u044f \u043f\u043e\u043b\u0443\u043f\u0440\u043e\u0437\u0440\u0430\u0447\u043d\u043e\u0439.\n"
                + "\u0410\u043f\u0442\u0435\u0447\u043a\u0430: \u043f\u043e\u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u043f\u0440\u0438 3 \u0438 \u0431\u043e\u043b\u0435\u0435 \u0443\u0440\u043e\u043d\u0430\u0445 \u0438 \u0443\u0431\u0438\u0440\u0430\u0435\u0442 1 \u0443\u0440\u043e\u043d.\n"
                + "\u0411\u0430\u043d\u043a: \u043d\u0430 30 \u0441\u0435\u043a\u0443\u043d\u0434 \u043f\u043e\u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f \u0432 \u0441\u0442\u0435\u043d\u0435, \u0434\u0430\u0435\u0442 \u0431\u043e\u0433\u0430\u0442\u0441\u0442\u0432\u043e \u0438 \u0431\u0430\u043d\u043a\u043d\u043e\u0442\u044b, \u043d\u043e \u0432\u044b\u0437\u044b\u0432\u0430\u0435\u0442 \u0434\u043e\u043f\u043e\u043b\u043d\u0438\u0442\u0435\u043b\u044c\u043d\u0443\u044e \u043f\u043e\u043b\u0438\u0446\u0438\u044e.\n\n"
                + "ПРОФ: открывает уровень «Легенда», аватар, игру с компьютерным напарником и редкие артефакты.\n"
                + "K: Убийца, 30 секунд позволяет уничтожить полицейскую машинку касанием.\n"
                + "C: Изменение мира, 20 секунд полиция убегает от преступников.\n"
                + "D: Двойное богатство, 20 секунд монеты дают 2 богатства.\n"
                + "I: Лёд для полиции. Его берёт только полиция; после этого она скользит до поворота.\n\n"
                + "\u0411\u043e\u043a\u043e\u0432\u044b\u0435 \u0434\u044b\u0440\u044b \u0432 \u0441\u0442\u0435\u043d\u0430\u0445 \u043f\u0435\u0440\u0435\u043d\u043e\u0441\u044f\u0442 \u043c\u0430\u0448\u0438\u043d\u043a\u0443 \u043d\u0430 \u0434\u0440\u0443\u0433\u0443\u044e \u0441\u0442\u043e\u0440\u043e\u043d\u0443 \u043a\u0430\u0440\u0442\u044b.";
        new AlertDialog.Builder(this)
                .setTitle("\u0421\u043f\u0440\u0430\u0432\u043a\u0430")
                .setMessage(text)
                .setPositiveButton("\u041f\u043e\u043d\u044f\u0442\u043d\u043e", null)
                .show();
    }

    private void showRichHelpDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(16), dp(18), dp(16));
        page.setBackgroundColor(Color.rgb(15, 26, 32));
        scroll.addView(page);

        TextView title = panelText("Справка");
        title.setTextSize(24f);
        title.setTypeface(null, 1);
        title.setTextColor(Color.WHITE);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));

        addHelpCard(page, "Цель", "Собрать все монеты и алмазы, не получив 5 уронов. На всех уровнях, кроме «Дебюта», нужно пройти 3 поля и въехать в сверкающую дыру после сбора богатства.");
        addHelpCard(page, "Управление", "Коснись экрана и веди палец в сторону движения. Верни палец к центру жеста или отпусти, чтобы остановиться.");
        addHelpHeader(page, "Богатство");
        addHelpArtifact(page, "coin", "Монета", "+1 к богатству. С артефактом D даёт +2.");
        addHelpArtifact(page, "diamond", "Алмаз", "+10 к богатству.");
        addHelpArtifact(page, "bank", "Банк", "Появляется в стене, даёт богатство и банкноты, но вызывает дополнительную полицию.");
        addHelpHeader(page, "Артефакты");
        addHelpArtifact(page, "FREEZER", "Фризер", "Замораживает полицейские машинки на 20 секунд.");
        addHelpArtifact(page, "SHIELD", "Защита", "На 20 секунд блокирует урон от полиции.");
        addHelpArtifact(page, "GHOST", "Призрак", "На 7 секунд позволяет ехать сквозь стены.");
        addHelpArtifact(page, "PORTAL", "Портал", "Меняет связь боковых дыр в стенах.");
        addHelpArtifact(page, "medkit", "Аптечка", "Появляется при 3+ урона и лечит 1 урон.");
        addHelpHeader(page, "ПРОФ");
        addHelpArtifact(page, "KILLER", "K: Убийца", "30 секунд уничтожает полицейскую машинку при касании, даже во время мигания после урона.");
        addHelpArtifact(page, "CHAOS", "C: Изменение мира", "20 секунд полиция убегает от преступников.");
        addHelpArtifact(page, "DOUBLE", "D: Двойное богатство", "20 секунд монеты дают 2 богатства.");
        addHelpArtifact(page, "ICE", "I: Лёд", "Берёт только полиция: машинка скользит до поворота.");
        addHelpArtifact(page, "DECOY", "L: Ложный след", "12 секунд полиция гонится за приманкой вместо преступника.");
        addHelpArtifact(page, "TURBO", "T: Турбо", "5 секунд машинка преступника едет быстрее.");

        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void addHelpHeader(LinearLayout page, String text) {
        TextView header = panelText(text);
        header.setTextSize(19f);
        header.setTypeface(null, 1);
        header.setTextColor(Color.rgb(120, 220, 255));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(38));
        params.topMargin = dp(8);
        page.addView(header, params);
    }

    private void addHelpCard(LinearLayout page, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(glassBackground(Color.rgb(27, 43, 50), Color.rgb(55, 78, 88), dp(12), 1));
        TextView heading = panelText(title);
        heading.setTextSize(17f);
        heading.setTypeface(null, 1);
        TextView content = panelText(body);
        content.setTextSize(15f);
        content.setTextColor(Color.rgb(220, 232, 235));
        card.addView(heading);
        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        page.addView(card, params);
    }

    private void addHelpArtifact(LinearLayout page, String icon, String title, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(glassBackground(Color.rgb(22, 37, 44), Color.rgb(47, 66, 74), dp(12), 1));
        ArtifactHelpIconView iconView = new ArtifactHelpIconView(this, icon);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, 0, 0);
        TextView heading = panelText(title);
        heading.setTextSize(16f);
        heading.setTypeface(null, 1);
        TextView content = panelText(body);
        content.setTextSize(14f);
        content.setTextColor(Color.rgb(216, 229, 232));
        text.addView(heading);
        text.addView(content);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        page.addView(row, params);
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

    private Difficulty parseDifficulty(String value, Difficulty fallback) {
        try {
            return Difficulty.valueOf(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int banknotes() {
        return prefs.getInt(GameConfig.PREF_BANKNOTES, 0);
    }

    private int displayedBanknotes() {
        return banknotes() + pendingBanknotes;
    }

    private void addPendingBanknotes(int amount) {
        pendingBanknotes += Math.max(0, amount);
        updateBanknoteHud();
    }

    private void commitPendingBanknotes() {
        if (pendingBanknotes > 0) {
            addBanknotes(pendingBanknotes);
            pendingBanknotes = 0;
            updateBanknoteHud();
        }
    }

    private void updateBanknoteHud() {
        if (gameView != null) {
            gameView.setBanknotes(displayedBanknotes());
        }
        if (onlineView != null) {
            onlineView.setBanknotes(displayedBanknotes());
        }
    }

    private void addBanknotes(int amount) {
        prefs.edit().putInt(GameConfig.PREF_BANKNOTES, banknotes() + amount).apply();
        updateBanknoteHud();
    }

    private int banknotesFor(Difficulty difficulty) {
        if (difficulty == Difficulty.DEBUT) {
            return 5;
        }
        if (difficulty == Difficulty.BEGINNER) {
            return 10;
        }
        if (difficulty == Difficulty.AMATEUR) {
            return 15;
        }
        if (difficulty == Difficulty.PRO) {
            return 20;
        }
        return 30;
    }

    private void buyShopItem(ShopItem item) {
        if (isShopItemOwned(item) || banknotes() < item.cost) {
            return;
        }
        HashSet<String> owned = new HashSet<>(ownedShopItems());
        owned.add(item.id);
        prefs.edit()
                .putInt(GameConfig.PREF_BANKNOTES, banknotes() - item.cost)
                .putString(GameConfig.PREF_OWNED_SHOP_ITEMS, joinIds(owned))
                .apply();
        selectShopItem(item);
    }

    private void selectShopItem(ShopItem item) {
        prefs.edit().putString(selectedKey(item.category), item.id).apply();
        applyShopItem(item);
        if (gameView != null) {
            gameView.setBanknotes(banknotes());
            gameView.invalidate();
        }
    }

    private void loadSavedColors() {
        applySelectedShopItem("background");
        applySelectedShopItem("wall");
        applySelectedShopItem("player");
        applySelectedShopItem("police");
    }

    private void applySelectedShopItem(String category) {
        String selected = selectedShopId(category);
        for (ShopItem item : shopItems(category)) {
            if (item.id.equals(selected)) {
                applyShopItem(item);
                return;
            }
        }
    }

    private void applyShopItem(ShopItem item) {
        if ("background".equals(item.category)) {
            colors.background = item.color;
            colors.road = colors.lighten(item.color, 28);
            if (root != null) {
                root.setBackgroundColor(colors.background);
            }
        } else if ("wall".equals(item.category)) {
            colors.wall = item.color;
        } else if ("player".equals(item.category)) {
            colors.player = item.color;
        } else if ("police".equals(item.category)) {
            colors.police = item.color;
        }
    }

    private boolean isShopItemOwned(ShopItem item) {
        return item.cost == 0 || ownedShopItems().contains(item.id);
    }

    private List<String> ownedShopItems() {
        String raw = prefs.getString(GameConfig.PREF_OWNED_SHOP_ITEMS, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private String joinIds(Set<String> ids) {
        StringBuilder encoded = new StringBuilder();
        for (String id : ids) {
            if (encoded.length() > 0) {
                encoded.append(",");
            }
            encoded.append(id);
        }
        return encoded.toString();
    }

    private String selectedShopId(String category) {
        return prefs.getString(selectedKey(category), category + "_default");
    }

    private String selectedKey(String category) {
        if ("background".equals(category)) {
            return GameConfig.PREF_SELECTED_BACKGROUND;
        }
        if ("wall".equals(category)) {
            return GameConfig.PREF_SELECTED_WALL;
        }
        if ("player".equals(category)) {
            return GameConfig.PREF_SELECTED_PLAYER;
        }
        return GameConfig.PREF_SELECTED_POLICE;
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
        recalculateAwardsFromStoredProgress();
        refreshAwardHud();
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(10), dp(14), dp(10));
        scroll.addView(panel);

        TextView total = panelText("Общее богатство: " + prefs.getInt(GameConfig.PREF_TOTAL_WEALTH, 0));
        total.setTextSize(18f);
        total.setTypeface(null, 1);
        panel.addView(total);

        for (Difficulty value : Difficulty.values()) {
            panel.addView(panelText(value.label + ": " + prefs.getInt(levelWealthKey(value), 0)));
        }

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
        migrateLegacyFlash(unlocked);
        if (result.won && result.fastField) {
            unlocked.add(flashAwardId(result.difficulty));
        }
        addStoredProgressAwards(unlocked);
        saveUnlockedAwards(unlocked);
    }

    private void recalculateAwardsFromStoredProgress() {
        HashSet<String> unlocked = new HashSet<>(unlockedAwards());
        if (prefs.getInt(GameConfig.PREF_EARLY_GAMES, 0) >= 10) {
            unlocked.add("early");
        }
        if (prefs.getInt(GameConfig.PREF_LATE_GAMES, 0) >= 10) {
            unlocked.add("late");
        }
        migrateLegacyFlash(unlocked);
        addStoredProgressAwards(unlocked);
        saveUnlockedAwards(unlocked);
    }

    private void migrateLegacyFlash(Set<String> unlocked) {
        if (unlocked.contains("flash")) {
            unlocked.add("flash_debut");
            unlocked.remove("flash");
        }
    }

    private String flashAwardId(Difficulty difficulty) {
        if (difficulty == Difficulty.DEBUT) {
            return "flash_debut";
        }
        if (difficulty == Difficulty.BEGINNER) {
            return "flash_beginner";
        }
        if (difficulty == Difficulty.AMATEUR) {
            return "flash_amateur";
        }
        return "flash_pro";
    }

    private void addStoredProgressAwards(Set<String> unlocked) {
        for (Award award : awards()) {
            if (award.difficultyKey != null) {
                try {
                    Difficulty value = Difficulty.valueOf(award.difficultyKey);
                    if (prefs.getInt(levelWealthKey(value), 0) >= award.threshold) {
                        unlocked.add(award.id);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy award keys.
                }
            }
        }
    }

    private List<String> unlockedAwards() {
        String raw = prefs.getString(GameConfig.PREF_AWARDS, "");
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<String> values = new ArrayList<>(Arrays.asList(raw.split(",")));
        if (values.contains("flash") && !values.contains("flash_debut")) {
            values.add("flash_debut");
        }
        values.remove("flash");
        return values;
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
        if (onlineView != null) {
            onlineView.setAwardSymbols(symbols);
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
        awards.add(new Award("flash_debut", "Флеш дебют", "Дебют: быстро собрать поле за 1:40", "FD", Color.rgb(255, 95, 60), null, 0));
        awards.add(new Award("flash_beginner", "Флеш начинающий", "Начинающий: быстро собрать поле за 3:00", "FN", Color.rgb(70, 215, 150), null, 0));
        awards.add(new Award("flash_amateur", "Флеш любитель", "Любитель: быстро собрать поле за 4:30", "FL", Color.rgb(100, 175, 255), null, 0));
        awards.add(new Award("flash_pro", "Флеш профессионал", "Профессионал: быстро собрать поле за 5:30", "FP", Color.rgb(255, 214, 70), null, 0));
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
        for (Difficulty value : visibleDifficulties()) {
            labels.add(value.label);
        }
        return labels;
    }

    private List<Difficulty> visibleDifficulties() {
        ArrayList<Difficulty> values = new ArrayList<>();
        for (Difficulty value : Difficulty.values()) {
            if (value != Difficulty.LEGEND || profEnabled) {
                values.add(value);
            }
        }
        return values;
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

    private void showLoseDialog(GameResult result) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(14));

        TextView title = panelText("Поражение");
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView details = panelText("Полиция нанесла " + result.damage + "/" + GameConfig.MAX_DAMAGE
                + " уронов.\nБогатство за этот заезд не добавлено."
                + "\nСобрано в заезде: " + result.wealth
                + "\nВремя: " + formatTime(result.seconds));
        details.setGravity(Gravity.CENTER);
        panel.addView(details, new LinearLayout.LayoutParams(-1, -2));

        Button action = new Button(this);
        action.setAllCaps(false);
        action.setText(onlineView == null ? "Новый заезд" : "Понятно");
        panel.addView(action, new LinearLayout.LayoutParams(-1, dp(48)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .create();
        action.setOnClickListener(v -> {
            if (onlineView == null) {
                gameView.start(difficulty);
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    private static final class ShopItem {
        final String id;
        final String category;
        final String name;
        final int color;
        final int cost;

        ShopItem(String id, String category, String name, int color, int cost) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.color = color;
            this.cost = cost;
        }
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

    private static final class AvatarView extends View {
        private int[] values;

        AvatarView(Activity activity, int[] values) {
            super(activity);
            setValues(values);
        }

        void setValues(int[] values) {
            this.values = values == null ? new int[AvatarRenderer.CATEGORY_COUNT] : Arrays.copyOf(values, AvatarRenderer.CATEGORY_COUNT);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            AvatarRenderer.draw(canvas, values, 0f, 0f, getWidth(), getHeight(), true);
        }
    }

    private static final class ArtifactHelpIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String icon;

        ArtifactHelpIconView(Activity activity, String icon) {
            super(activity);
            this.icon = icon;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) * 0.36f;
            paint.setStyle(Paint.Style.FILL);
            if ("coin".equals(icon)) {
                paint.setColor(Color.rgb(245, 200, 75));
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(Color.rgb(255, 240, 168));
                canvas.drawCircle(cx, cy, r * 0.45f, paint);
                return;
            }
            if ("diamond".equals(icon)) {
                Path path = new Path();
                path.moveTo(cx, cy - r);
                path.lineTo(cx + r * 0.78f, cy);
                path.lineTo(cx, cy + r);
                path.lineTo(cx - r * 0.78f, cy);
                path.close();
                paint.setColor(Color.rgb(119, 236, 255));
                canvas.drawPath(path, paint);
                return;
            }
            if ("medkit".equals(icon)) {
                paint.setColor(Color.WHITE);
                canvas.drawRoundRect(new RectF(cx - r, cy - r * 0.7f, cx + r, cy + r * 0.75f), r * 0.22f, r * 0.22f, paint);
                paint.setColor(Color.rgb(230, 68, 72));
                canvas.drawRect(cx - r * 0.18f, cy - r * 0.48f, cx + r * 0.18f, cy + r * 0.52f, paint);
                canvas.drawRect(cx - r * 0.55f, cy - r * 0.12f, cx + r * 0.55f, cy + r * 0.2f, paint);
                return;
            }
            if ("bank".equals(icon)) {
                paint.setColor(Color.rgb(58, 174, 92));
                canvas.drawRoundRect(new RectF(cx - r, cy - r * 0.5f, cx + r, cy + r * 0.75f), r * 0.12f, r * 0.12f, paint);
                paint.setColor(Color.rgb(210, 255, 220));
                canvas.drawRect(cx - r * 0.7f, cy - r * 0.15f, cx + r * 0.7f, cy + r * 0.02f, paint);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setFakeBoldText(true);
                paint.setTextSize(r * 0.72f);
                canvas.drawText("Б", cx, cy + r * 0.48f, paint);
                paint.setFakeBoldText(false);
                return;
            }
            String label = "FREEZER".equals(icon) ? "F" : "SHIELD".equals(icon) ? "S" : "GHOST".equals(icon) ? "G"
                    : "PORTAL".equals(icon) ? "P" : "KILLER".equals(icon) ? "K" : "CHAOS".equals(icon) ? "C"
                    : "DOUBLE".equals(icon) ? "D" : "DECOY".equals(icon) ? "L" : "TURBO".equals(icon) ? "T" : "I";
            int color = "FREEZER".equals(icon) ? Color.rgb(70, 210, 255) : "SHIELD".equals(icon) ? Color.rgb(77, 230, 116)
                    : "GHOST".equals(icon) ? Color.rgb(185, 125, 255) : "PORTAL".equals(icon) ? Color.rgb(255, 174, 78)
                    : "KILLER".equals(icon) ? Color.rgb(245, 70, 76) : "CHAOS".equals(icon) ? Color.rgb(255, 178, 62)
                    : "DOUBLE".equals(icon) ? Color.rgb(72, 225, 116) : "DECOY".equals(icon) ? Color.rgb(255, 210, 105)
                    : "TURBO".equals(icon) ? Color.rgb(255, 95, 60) : Color.rgb(150, 230, 255);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(r * 1.05f);
            paint.setColor(Color.WHITE);
            canvas.drawText(label, cx, cy + r * 0.36f, paint);
            paint.setFakeBoldText(false);
        }
    }

    private static final class AvatarOptionView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] values;
        private final int category;
        private final int option;
        private boolean chosen;

        AvatarOptionView(Activity activity, int[] baseValues, int category, int option) {
            super(activity);
            this.values = Arrays.copyOf(baseValues, AvatarRenderer.CATEGORY_COUNT);
            this.category = category;
            this.option = option;
            this.values[category] = option;
        }

        void setChosen(boolean chosen) {
            this.chosen = chosen;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(14, 32, 38));
            canvas.drawRoundRect(new RectF(6f, 6f, w - 6f, h - 6f), 18f, 18f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(chosen ? 5f : 3f);
            paint.setColor(chosen ? Color.rgb(76, 195, 240) : Color.rgb(52, 72, 80));
            canvas.drawRoundRect(new RectF(6f, 6f, w - 6f, h - 6f), 18f, 18f, paint);
            float size = Math.min(w * 0.82f, h * 0.9f);
            AvatarRenderer.draw(canvas, values, (w - size) / 2f, (h - size) / 2f, size, false);
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
            } else if (award.id.startsWith("flash")) {
                drawFlash(canvas, award.id, w / 2f, h / 2f, r, detail);
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

        private void drawFlash(Canvas canvas, String id, float cx, float cy, float r, int color) {
            drawLightning(canvas, cx, cy, r, color);
            if ("flash_amateur".equals(id) || "flash_pro".equals(id)) {
                drawLightning(canvas, cx + r * 0.3f, cy - r * 0.08f, r * 0.52f, color);
            }
            if ("flash_pro".equals(id)) {
                paint.setColor(Color.WHITE);
                Path star = new Path();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = i % 2 == 0 ? r * 0.22f : r * 0.09f;
                    float x = cx - r * 0.45f + (float) Math.cos(a) * rr;
                    float y = cy - r * 0.42f + (float) Math.sin(a) * rr;
                    if (i == 0) {
                        star.moveTo(x, y);
                    } else {
                        star.lineTo(x, y);
                    }
                }
                star.close();
                canvas.drawPath(star, paint);
            }
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
