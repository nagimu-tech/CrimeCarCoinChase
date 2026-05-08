package com.nagimutech.crimecarcoinchase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OnlineGameView extends View {
    interface Listener {
        void onDirection(Direction direction);
        void onMenuRequested();
    }

    private static final int WALL = 0;
    private static final int EMPTY = 1;
    private static final int COIN = 2;
    private static final int DIAMOND = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GameColors colors;
    private final Listener listener;
    private final float density;
    private final List<RemoteCar> players = new ArrayList<>();
    private final List<RemoteCar> police = new ArrayList<>();
    private final List<RemoteArtifact> artifacts = new ArrayList<>();
    private final Map<String, Bitmap> bitmapIcons = new HashMap<>();
    private RectF menuRect = new RectF();
    private int[][] grid = new int[0][0];
    private int mapWidth;
    private int mapHeight;
    private int playerId;
    private int score;
    private int total;
    private int damage;
    private int banknotes;
    private int stage = 1;
    private int elapsedSeconds;
    private RemoteArtifact exitPortal;
    private String status = "Подключение...";
    private String roomCode = "";
    private boolean hasServerState;
    private boolean gestureActive;
    private float gestureStartX;
    private float gestureStartY;
    private Direction lastSent = Direction.NONE;
    private List<String> awardSymbols = new ArrayList<>();
    private int otherPlayerColor = Color.rgb(255, 135, 46);

    OnlineGameView(Context context, GameColors colors, Listener listener) {
        super(context);
        this.colors = colors;
        this.listener = listener;
        this.density = getResources().getDisplayMetrics().density;
        setFocusable(true);
    }

    void setRoomCode(String roomCode) {
        this.roomCode = roomCode == null ? "" : roomCode;
        invalidate();
    }

    void setPlayerId(int playerId) {
        this.playerId = playerId;
        invalidate();
    }

    void setStatus(String status) {
        this.status = status == null ? "" : status;
        invalidate();
    }

    void setBanknotes(int banknotes) {
        this.banknotes = Math.max(0, banknotes);
        invalidate();
    }

    void setAwardSymbols(List<String> awardSymbols) {
        this.awardSymbols = awardSymbols == null ? new ArrayList<>() : new ArrayList<>(awardSymbols);
        invalidate();
    }

    void setOtherPlayerColor(int color) {
        this.otherPlayerColor = color;
        invalidate();
    }

    void resetInputState() {
        gestureActive = false;
        lastSent = Direction.NONE;
    }

    int myScore() {
        RemoteCar me = me();
        return me == null ? score : me.score;
    }

    int myTotal() {
        RemoteCar me = me();
        return me == null ? total : me.total;
    }

    int myDamage() {
        RemoteCar me = me();
        return me == null ? damage : me.damage;
    }

    int elapsedSeconds() {
        return elapsedSeconds;
    }

    void applyState(JSONObject state) {
        mapWidth = state.optInt("width", mapWidth);
        mapHeight = state.optInt("height", mapHeight);
        stage = state.optInt("stage", stage);
        score = state.optInt("score", score);
        total = state.optInt("total", total);
        damage = state.optInt("damage", damage);
        elapsedSeconds = state.optInt("elapsedSeconds", elapsedSeconds);
        status = state.optString("statusText", status);
        hasServerState = true;
        JSONObject portal = state.optJSONObject("exitPortal");
        if (portal != null) {
            exitPortal = new RemoteArtifact();
            exitPortal.type = "EXIT";
            exitPortal.x = portal.optInt("x", 0);
            exitPortal.y = portal.optInt("y", 0);
        } else {
            exitPortal = null;
        }

        JSONArray rows = state.optJSONArray("grid");
        if (rows != null && mapWidth > 0 && mapHeight > 0) {
            grid = new int[mapHeight][mapWidth];
            for (int y = 0; y < mapHeight && y < rows.length(); y++) {
                String row = rows.optString(y, "");
                for (int x = 0; x < mapWidth && x < row.length(); x++) {
                    char c = row.charAt(x);
                    grid[y][x] = c == '#' ? WALL : c == 'o' ? COIN : c == 'd' ? DIAMOND : EMPTY;
                }
            }
        }

        updateCars(state.optJSONArray("players"), players);
        updateCars(state.optJSONArray("police"), police);
        artifacts.clear();
        readArtifacts(state.optJSONArray("artifacts"));
        invalidate();
    }

    private void updateCars(JSONArray array, List<RemoteCar> target) {
        if (array == null) {
            target.clear();
            return;
        }
        ArrayList<Integer> seen = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int id = item.optInt("id", i + 1);
            seen.add(id);
            RemoteCar car = findCar(target, id);
            float x = (float) item.optDouble("x", 1.5);
            float y = (float) item.optDouble("y", 1.5);
            float angle = (float) item.optDouble("angle", 0.0);
            if (car == null) {
                car = new RemoteCar();
                car.id = id;
                car.x = x;
                car.y = y;
                car.angle = angle;
                car.targetX = x;
                car.targetY = y;
                car.targetAngle = angle;
                target.add(car);
            } else {
                car.targetX = x;
                car.targetY = y;
                car.targetAngle = angle;
                if (Math.abs(car.x - x) > 1.2f || Math.abs(car.y - y) > 1.2f) {
                    car.x = x;
                    car.y = y;
                    car.angle = angle;
                }
            }
            car.damage = item.optInt("damage", 0);
            car.score = item.optInt("score", 0);
            car.total = item.optInt("total", total);
            car.banknotes = item.optInt("banknotes", 0);
            car.ghostActive = item.optBoolean("ghostActive", false);
            car.shieldActive = item.optBoolean("shieldActive", false);
            car.invulnerableActive = item.optBoolean("invulnerableActive", false);
        }
        for (int i = target.size() - 1; i >= 0; i--) {
            if (!seen.contains(target.get(i).id)) {
                target.remove(i);
            }
        }
    }

    private RemoteCar findCar(List<RemoteCar> target, int id) {
        for (RemoteCar car : target) {
            if (car.id == id) {
                return car;
            }
        }
        return null;
    }

    private void readArtifacts(JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                RemoteArtifact artifact = new RemoteArtifact();
                artifact.type = item.optString("type", "");
                artifact.x = item.optInt("x", 0);
                artifact.y = item.optInt("y", 0);
                artifacts.add(artifact);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        advanceCars();
        canvas.drawColor(colors.background);
        drawMap(canvas);
        drawTopBar(canvas);
        drawBottomPlayerHud(canvas);
        drawRoomCodeFooter(canvas);
        postInvalidateDelayed(16);
    }

    private void advanceCars() {
        for (RemoteCar car : players) {
            advanceCar(car);
        }
        for (RemoteCar car : police) {
            advanceCar(car);
        }
    }

    private void advanceCar(RemoteCar car) {
        float factor = 0.62f;
        car.x += (car.targetX - car.x) * factor;
        car.y += (car.targetY - car.y) * factor;
        float diff = normalizeAngle(car.targetAngle - car.angle);
        car.angle += diff * factor;
        if (Math.abs(car.x - car.targetX) < 0.004f) {
            car.x = car.targetX;
        }
        if (Math.abs(car.y - car.targetY) < 0.004f) {
            car.y = car.targetY;
        }
        if (Math.abs(diff) < 0.01f) {
            car.angle = car.targetAngle;
        }
    }

    private float normalizeAngle(float value) {
        while (value > Math.PI) {
            value -= (float) (Math.PI * 2.0);
        }
        while (value < -Math.PI) {
            value += (float) (Math.PI * 2.0);
        }
        return value;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (menuRect.contains(x, y)) {
                listener.onMenuRequested();
                return true;
            }
            gestureActive = true;
            gestureStartX = x;
            gestureStartY = y;
            sendDirection(Direction.NONE);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && gestureActive) {
            updateGesture(x, y);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            gestureActive = false;
            sendDirection(Direction.NONE);
            return true;
        }
        return true;
    }

    private void updateGesture(float x, float y) {
        float dx = x - gestureStartX;
        float dy = y - gestureStartY;
        float deadZone = 24f * density;
        if (Math.hypot(dx, dy) < deadZone) {
            sendDirection(Direction.NONE);
            return;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            sendDirection(dx > 0 ? Direction.RIGHT : Direction.LEFT);
        } else {
            sendDirection(dy > 0 ? Direction.DOWN : Direction.UP);
        }
    }

    private void sendDirection(Direction direction) {
        if (direction == lastSent) {
            return;
        }
        lastSent = direction;
        listener.onDirection(direction);
    }

    private void drawMap(Canvas canvas) {
        float topBar = topBarHeight();
        float footerSpace = roomCodeFooterHeight();
        float bottomHud = bottomHudHeight();
        if (mapWidth <= 0 || mapHeight <= 0 || grid.length == 0) {
            drawCenteredStatus(canvas, topBar);
            return;
        }
        float playHeight = Math.max(1f, getHeight() - topBar - bottomHud - footerSpace);
        float tile = Math.min(getWidth() / (float) mapWidth, playHeight / (float) mapHeight);
        float originX = (getWidth() - tile * mapWidth) / 2f;
        float originY = topBar + (playHeight - tile * mapHeight) / 2f;

        paint.setStyle(Paint.Style.FILL);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                float left = originX + x * tile;
                float top = originY + y * tile;
                if (grid[y][x] == WALL) {
                    paint.setShader(new LinearGradient(left, top, left, top + tile, colors.lighten(colors.wall, 52), colors.wall, Shader.TileMode.CLAMP));
                    float inset = Math.max(0.5f, tile * 0.025f);
                    canvas.drawRect(left + inset, top + inset, left + tile - inset, top + tile - inset, paint);
                    paint.setShader(null);
                } else {
                    paint.setColor(colors.road);
                    canvas.drawRect(left, top, left + tile, top + tile, paint);
                    if (grid[y][x] == COIN) {
                        drawCoin(canvas, left + tile / 2f, top + tile / 2f, tile);
                    } else if (grid[y][x] == DIAMOND) {
                        drawDiamond(canvas, left + tile / 2f, top + tile / 2f, tile);
                    }
                }
                if (exitPortal != null && exitPortal.x == x && exitPortal.y == y) {
                    drawExitPortal(canvas, left + tile / 2f, top + tile / 2f, tile);
                }
            }
        }

        for (RemoteArtifact artifact : artifacts) {
            drawArtifact(canvas, artifact, tile, originX, originY);
        }
        for (RemoteCar car : police) {
            drawCar(canvas, car, colors.police, Color.rgb(201, 223, 255), tile, originX, originY, false);
        }
        for (RemoteCar car : players) {
            int body = car.id == playerId ? colors.player : otherPlayerColor;
            if (car.ghostActive) {
                body = Color.argb(170, Color.red(body), Color.green(body), Color.blue(body));
            }
            boolean blink = car.invulnerableActive && !car.shieldActive && (SystemClock.uptimeMillis() / 100) % 2 == 0;
            if (!blink) {
                drawCar(canvas, car, body, Color.rgb(243, 176, 71), tile, originX, originY, true);
            }
        }
    }

    private void drawTopBar(Canvas canvas) {
        float h = topBarHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(222, 6, 10, 18));
        canvas.drawRect(0f, 0f, getWidth(), h, paint);

        float size = 44f * density;
        float right = getWidth() - 10f * density;
        menuRect.set(right - size, 8f * density, right, 8f * density + size);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(15f * density);
        paint.setColor(Color.WHITE);
        float baseline = 58f * density;
        if (hasServerState) {
            RemoteCar me = me();
            int meScore = me == null ? score : me.score;
            int meTotal = me == null ? total : me.total;
            int meDamage = me == null ? damage : me.damage;
            float x = 14f * density;
            float icon = 18f * density;
            drawBitmapIcon(canvas, "wealth", x, baseline - icon + 3f * density, icon);
            canvas.drawText(meScore + "/" + meTotal, x + 24f * density, baseline, paint);
            drawBitmapIcon(canvas, "banknote", x + 92f * density, baseline - icon + 3f * density, icon);
            canvas.drawText(String.valueOf(banknotes), x + 115f * density, baseline, paint);
            drawBitmapIcon(canvas, "damage", x + 154f * density, baseline - icon + 3f * density, icon);
            canvas.drawText(meDamage + "/" + GameConfig.MAX_DAMAGE, x + 177f * density, baseline, paint);
            drawBitmapIcon(canvas, "time", x + 216f * density, baseline - icon + 3f * density, icon);
            canvas.drawText(formatTime(elapsedSeconds), x + 239f * density, baseline, paint);
            drawEffectBadges(canvas, me, x, 36f * density, getWidth() - 92f * density);
            drawAwardBadges(canvas);
        } else {
            drawSingleLineEllipsized(canvas, status, 14f * density, baseline, getWidth() - 92f * density);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 0, 0, 0));
        canvas.drawRoundRect(menuRect, 16f * density, 16f * density, paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4f * density);
        float cx = menuRect.centerX();
        float top = menuRect.top + 13f * density;
        for (int i = 0; i < 3; i++) {
            canvas.drawLine(cx - 13f * density, top + i * 10f * density, cx + 13f * density, top + i * 10f * density, paint);
        }
        paint.setFakeBoldText(false);
    }

    private void drawBottomPlayerHud(Canvas canvas) {
        if (!hasServerState) {
            return;
        }
        RemoteCar other = other();
        if (other == null) {
            return;
        }
        float top = getHeight() - roomCodeFooterHeight() - bottomHudHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(214, 6, 10, 18));
        canvas.drawRect(0f, top, getWidth(), top + bottomHudHeight(), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(14f * density);
        paint.setColor(Color.WHITE);
        float baseline = top + 34f * density;
        float x = 14f * density;
        float icon = 18f * density;
        drawBitmapIcon(canvas, "wealth", x, baseline - icon + 3f * density, icon);
        canvas.drawText(other.score + "/" + other.total, x + 24f * density, baseline, paint);
        x += 118f * density;
        drawBitmapIcon(canvas, "banknote", x, baseline - icon + 3f * density, icon);
        canvas.drawText(String.valueOf(other.banknotes), x + 28f * density, baseline, paint);
        x += 96f * density;
        drawBitmapIcon(canvas, "damage", x, baseline - icon + 3f * density, icon);
        canvas.drawText(other.damage + "/" + GameConfig.MAX_DAMAGE, x + 25f * density, baseline, paint);
        drawEffectBadges(canvas, other, x + 86f * density, baseline, getWidth() - 18f * density);
        paint.setFakeBoldText(false);
    }

    private void drawRoomCodeFooter(Canvas canvas) {
        if (roomCode.isEmpty()) {
            return;
        }
        String text = "Код комнаты: " + roomCode;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(15f * density);
        paint.setFakeBoldText(true);
        float textWidth = paint.measureText(text);
        float padX = 16f * density;
        float height = 34f * density;
        float bottom = getHeight() - 6f * density;
        RectF panel = new RectF(
                Math.max(10f * density, getWidth() / 2f - textWidth / 2f - padX),
                bottom - height,
                Math.min(getWidth() - 10f * density, getWidth() / 2f + textWidth / 2f + padX),
                bottom
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(165, 0, 0, 0));
        canvas.drawRoundRect(panel, 14f * density, 14f * density, paint);
        paint.setColor(Color.rgb(255, 232, 178));
        canvas.drawText(text, panel.centerX(), panel.centerY() + 5f * density, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float topBarHeight() {
        return 72f * density;
    }

    private float bottomHudHeight() {
        return hasServerState && other() != null ? 52f * density : 0f;
    }

    private float roomCodeFooterHeight() {
        return roomCode.isEmpty() ? 0f : 46f * density;
    }

    private RemoteCar me() {
        return findCar(players, playerId);
    }

    private RemoteCar other() {
        for (RemoteCar car : players) {
            if (car.id != playerId) {
                return car;
            }
        }
        return null;
    }

    private void drawCenteredStatus(Canvas canvas, float topBar) {
        paint.setColor(Color.WHITE);
        paint.setTextSize(18f * density);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(false);
        String[] lines = wrapText(status, getWidth() - 36f * density);
        float startY = topBar + 92f * density;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], getWidth() / 2f, startY + i * 28f * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private String[] wrapText(String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return new String[]{""};
        }
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }

    private void drawSingleLineEllipsized(Canvas canvas, String text, float x, float y, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) {
            return;
        }
        String value = text;
        while (value.length() > 1 && paint.measureText(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.equals(text)) {
            value += "...";
        }
        canvas.drawText(value, x, y, paint);
    }

    private void drawSmallBanknote(Canvas canvas, float x, float y, float size) {
        RectF body = new RectF(x, y, x + size * 1.45f, y + size * 0.82f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(70, 180, 105));
        canvas.drawRoundRect(body, size * 0.12f, size * 0.12f, paint);
        paint.setColor(Color.rgb(178, 245, 188));
        canvas.drawCircle(body.centerX(), body.centerY(), size * 0.22f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * 0.07f));
        paint.setColor(Color.rgb(36, 120, 70));
        canvas.drawRoundRect(body, size * 0.12f, size * 0.12f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBitmapIcon(Canvas canvas, String type, float x, float y, float size) {
        Bitmap bitmap = bitmapIcon(type, Math.max(24, Math.round(size * 2f)));
        canvas.drawBitmap(bitmap, null, new RectF(x, y, x + size, y + size), paint);
    }

    private Bitmap bitmapIcon(String type, int size) {
        String key = type + "_" + size;
        Bitmap cached = bitmapIcons.get(key);
        if (cached != null) {
            return cached;
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas iconCanvas = new Canvas(bitmap);
        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = size;
        float cx = s / 2f;
        float cy = s / 2f;
        if ("wealth".equals(type)) {
            drawMoneyBagIcon(iconCanvas, iconPaint, cx, cy, s * 0.42f, Color.rgb(248, 205, 78), Color.rgb(86, 61, 28));
        } else if ("banknote".equals(type)) {
            drawBanknoteIcon(iconCanvas, iconPaint, cx, cy, s * 0.42f);
        } else if ("damage".equals(type)) {
            drawCrackedShieldIcon(iconCanvas, iconPaint, cx, cy, s * 0.43f);
        } else if ("time".equals(type)) {
            drawClockIcon(iconCanvas, iconPaint, cx, cy, s * 0.42f);
        } else if ("medkit".equals(type)) {
            drawMedkitIcon(iconCanvas, iconPaint, cx, cy, s * 0.42f);
        } else if ("bank".equals(type)) {
            drawBankIcon(iconCanvas, iconPaint, cx, cy, s * 0.42f);
        } else if (type.startsWith("award_")) {
            drawAwardIconBitmap(iconCanvas, iconPaint, type.substring("award_".length()), cx, cy, s * 0.43f);
        }
        bitmapIcons.put(key, bitmap);
        return bitmap;
    }

    private void drawAwardIconBitmap(Canvas canvas, Paint p, String id, float cx, float cy, float r) {
        int rim = Color.rgb(255, 238, 160);
        int fill = Color.rgb(244, 194, 70);
        if (id.startsWith("debut")) {
            fill = Color.rgb(196, 210, 225);
        } else if (id.startsWith("amateur")) {
            fill = Color.rgb(150, 230, 238);
        } else if (id.startsWith("late")) {
            fill = Color.rgb(126, 150, 245);
        } else if ("flash_beginner".equals(id)) {
            fill = Color.rgb(65, 210, 145);
        } else if ("flash_amateur".equals(id)) {
            fill = Color.rgb(88, 165, 255);
        } else if ("flash_pro".equals(id)) {
            fill = Color.rgb(255, 210, 65);
        } else if (id.startsWith("flash")) {
            fill = Color.rgb(255, 86, 52);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        canvas.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.14f);
        p.setColor(rim);
        canvas.drawCircle(cx, cy, r * 0.82f, p);
        p.setStyle(Paint.Style.FILL);
        if (id.startsWith("pro")) {
            drawIngotsIcon(canvas, p, cx, cy, r, Color.rgb(80, 53, 18));
        } else if (id.startsWith("early")) {
            drawBirdIcon(canvas, p, cx, cy, r, Color.rgb(82, 48, 18), true);
        } else if (id.startsWith("late")) {
            drawBirdIcon(canvas, p, cx, cy, r, Color.rgb(25, 32, 70), false);
        } else if (id.startsWith("flash")) {
            drawFlashAwardIcon(canvas, p, id, cx, cy, r);
        } else {
            drawMoneyBagIcon(canvas, p, cx, cy, r * 0.82f, Color.rgb(70, 48, 24), Color.rgb(70, 48, 24));
        }
    }

    private void drawMoneyBagIcon(Canvas canvas, Paint p, float cx, float cy, float r, int fill, int detail) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        canvas.drawOval(cx - r * 0.68f, cy - r * 0.2f, cx + r * 0.68f, cy + r * 0.72f, p);
        canvas.drawRect(cx - r * 0.28f, cy - r * 0.62f, cx + r * 0.28f, cy - r * 0.2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.12f);
        p.setColor(detail);
        canvas.drawLine(cx - r * 0.46f, cy - r * 0.18f, cx + r * 0.46f, cy - r * 0.18f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r * 0.78f);
        p.setFakeBoldText(true);
        canvas.drawText("$", cx, cy + r * 0.36f, p);
        p.setFakeBoldText(false);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBanknoteIcon(Canvas canvas, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(58, 174, 92));
        RectF rect = new RectF(cx - r * 0.95f, cy - r * 0.58f, cx + r * 0.95f, cy + r * 0.58f);
        canvas.drawRoundRect(rect, r * 0.14f, r * 0.14f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.1f);
        p.setColor(Color.rgb(190, 245, 195));
        canvas.drawRoundRect(new RectF(cx - r * 0.74f, cy - r * 0.39f, cx + r * 0.74f, cy + r * 0.39f), r * 0.1f, r * 0.1f, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r * 0.72f);
        p.setFakeBoldText(true);
        p.setColor(Color.rgb(16, 85, 42));
        canvas.drawText("$", cx, cy + r * 0.25f, p);
        p.setFakeBoldText(false);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCrackedShieldIcon(Canvas canvas, Paint p, float cx, float cy, float r) {
        Path shield = new Path();
        shield.moveTo(cx, cy - r);
        shield.lineTo(cx + r * 0.76f, cy - r * 0.62f);
        shield.lineTo(cx + r * 0.55f, cy + r * 0.45f);
        shield.lineTo(cx, cy + r);
        shield.lineTo(cx - r * 0.55f, cy + r * 0.45f);
        shield.lineTo(cx - r * 0.76f, cy - r * 0.62f);
        shield.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(235, 82, 80));
        canvas.drawPath(shield, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.15f);
        p.setColor(Color.WHITE);
        Path crack = new Path();
        crack.moveTo(cx + r * 0.08f, cy - r * 0.72f);
        crack.lineTo(cx - r * 0.12f, cy - r * 0.18f);
        crack.lineTo(cx + r * 0.12f, cy - r * 0.02f);
        crack.lineTo(cx - r * 0.08f, cy + r * 0.52f);
        canvas.drawPath(crack, p);
    }

    private void drawClockIcon(Canvas canvas, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(88, 180, 232));
        canvas.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.14f);
        p.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, r * 0.74f, p);
        canvas.drawLine(cx, cy, cx, cy - r * 0.46f, p);
        canvas.drawLine(cx, cy, cx + r * 0.38f, cy + r * 0.25f, p);
    }

    private void drawMedkitIcon(Canvas canvas, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(240, 245, 248));
        RectF body = new RectF(cx - r * 0.82f, cy - r * 0.52f, cx + r * 0.82f, cy + r * 0.66f);
        canvas.drawRoundRect(body, r * 0.18f, r * 0.18f, p);
        p.setColor(Color.rgb(210, 42, 52));
        canvas.drawRoundRect(cx - r * 0.18f, cy - r * 0.38f, cx + r * 0.18f, cy + r * 0.5f, r * 0.05f, r * 0.05f, p);
        canvas.drawRoundRect(cx - r * 0.48f, cy - r * 0.08f, cx + r * 0.48f, cy + r * 0.2f, r * 0.05f, r * 0.05f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.12f);
        p.setColor(Color.rgb(210, 42, 52));
        canvas.drawRoundRect(cx - r * 0.36f, cy - r * 0.86f, cx + r * 0.36f, cy - r * 0.4f, r * 0.16f, r * 0.16f, p);
        p.setColor(Color.rgb(90, 95, 105));
        canvas.drawRoundRect(body, r * 0.18f, r * 0.18f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawBankIcon(Canvas canvas, Paint p, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(46, 160, 88));
        RectF body = new RectF(cx - r * 0.78f, cy - r * 0.42f, cx + r * 0.78f, cy + r * 0.64f);
        canvas.drawRoundRect(body, r * 0.14f, r * 0.14f, p);
        p.setColor(Color.rgb(180, 245, 190));
        canvas.drawCircle(cx, cy + r * 0.1f, r * 0.34f, p);
        p.setColor(Color.rgb(18, 92, 45));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r * 0.58f);
        p.setFakeBoldText(true);
        canvas.drawText("$", cx, cy + r * 0.3f, p);
        p.setFakeBoldText(false);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.rgb(35, 120, 68));
        Path roof = new Path();
        roof.moveTo(cx - r * 0.88f, cy - r * 0.42f);
        roof.lineTo(cx, cy - r * 0.92f);
        roof.lineTo(cx + r * 0.88f, cy - r * 0.42f);
        roof.close();
        canvas.drawPath(roof, p);
    }

    private void drawIngotsIcon(Canvas canvas, Paint p, float cx, float cy, float r, int color) {
        p.setColor(color);
        canvas.drawRoundRect(cx - r * 0.72f, cy + r * 0.02f, cx - r * 0.05f, cy + r * 0.42f, r * 0.1f, r * 0.1f, p);
        canvas.drawRoundRect(cx - r * 0.18f, cy - r * 0.16f, cx + r * 0.55f, cy + r * 0.24f, r * 0.1f, r * 0.1f, p);
        canvas.drawRoundRect(cx - r * 0.46f, cy - r * 0.42f, cx + r * 0.26f, cy - r * 0.02f, r * 0.1f, r * 0.1f, p);
    }

    private void drawBirdIcon(Canvas canvas, Paint p, float cx, float cy, float r, int color, boolean morning) {
        p.setColor(color);
        canvas.drawOval(cx - r * 0.55f, cy - r * 0.05f, cx + r * 0.38f, cy + r * 0.44f, p);
        canvas.drawCircle(cx + r * 0.28f, cy - r * 0.26f, r * 0.22f, p);
        canvas.drawOval(cx - r * 0.75f, cy - r * 0.34f, cx - r * 0.1f, cy + r * 0.12f, p);
        p.setColor(morning ? Color.rgb(255, 240, 150) : Color.rgb(225, 230, 255));
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.31f, r * 0.06f, p);
    }

    private void drawLightningIcon(Canvas canvas, Paint p, float cx, float cy, float r, int color) {
        Path path = new Path();
        path.moveTo(cx + r * 0.16f, cy - r * 0.78f);
        path.lineTo(cx - r * 0.44f, cy + r * 0.05f);
        path.lineTo(cx + r * 0.02f, cy + r * 0.05f);
        path.lineTo(cx - r * 0.2f, cy + r * 0.72f);
        path.lineTo(cx + r * 0.56f, cy - r * 0.14f);
        path.lineTo(cx + r * 0.1f, cy - r * 0.14f);
        path.close();
        p.setColor(color);
        canvas.drawPath(path, p);
    }

    private void drawFlashAwardIcon(Canvas canvas, Paint p, String id, float cx, float cy, float r) {
        int boltColor = "flash_pro".equals(id) ? Color.rgb(90, 60, 18) : Color.WHITE;
        drawLightningIcon(canvas, p, cx, cy, r, boltColor);
        if ("flash_amateur".equals(id) || "flash_pro".equals(id)) {
            drawLightningIcon(canvas, p, cx + r * 0.34f, cy - r * 0.08f, r * 0.48f, boltColor);
        }
        if ("flash_pro".equals(id)) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
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
            canvas.drawPath(star, p);
        }
    }

    private void drawMoneyBagIcon(Canvas canvas, float x, float y, float size) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(218, 176, 72));
        canvas.drawOval(x + size * 0.14f, y + size * 0.28f, x + size * 0.92f, y + size, paint);
        paint.setColor(Color.rgb(144, 104, 42));
        canvas.drawRect(x + size * 0.36f, y + size * 0.12f, x + size * 0.72f, y + size * 0.34f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(size * 0.54f);
        paint.setColor(Color.rgb(90, 65, 30));
        canvas.drawText("$", x + size * 0.54f, y + size * 0.76f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDamageIcon(Canvas canvas, float x, float y, float size) {
        Path heart = new Path();
        heart.moveTo(x + size * 0.5f, y + size * 0.96f);
        heart.cubicTo(x - size * 0.15f, y + size * 0.52f, x + size * 0.08f, y + size * 0.04f, x + size * 0.42f, y + size * 0.2f);
        heart.cubicTo(x + size * 0.5f, y + size * 0.02f, x + size * 0.92f, y + size * 0.04f, x + size * 0.92f, y + size * 0.42f);
        heart.cubicTo(x + size * 0.92f, y + size * 0.62f, x + size * 0.72f, y + size * 0.78f, x + size * 0.5f, y + size * 0.96f);
        heart.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(230, 56, 70));
        canvas.drawPath(heart, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, size * 0.08f));
        paint.setColor(Color.rgb(255, 185, 190));
        canvas.drawLine(x + size * 0.52f, y + size * 0.28f, x + size * 0.42f, y + size * 0.52f, paint);
        canvas.drawLine(x + size * 0.42f, y + size * 0.52f, x + size * 0.58f, y + size * 0.62f, paint);
        canvas.drawLine(x + size * 0.58f, y + size * 0.62f, x + size * 0.48f, y + size * 0.82f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawClockIcon(Canvas canvas, float x, float y, float size) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(88, 195, 225));
        canvas.drawCircle(x + size * 0.5f, y + size * 0.5f, size * 0.46f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, size * 0.08f));
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x + size * 0.5f, y + size * 0.5f, size * 0.34f, paint);
        canvas.drawLine(x + size * 0.5f, y + size * 0.5f, x + size * 0.5f, y + size * 0.28f, paint);
        canvas.drawLine(x + size * 0.5f, y + size * 0.5f, x + size * 0.66f, y + size * 0.58f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawEffectBadges(Canvas canvas, RemoteCar car, float x, float baseline, float maxX) {
        if (car == null) {
            return;
        }
        paint.setFakeBoldText(true);
        paint.setTextSize(13f * density);
        if (car.ghostActive && x < maxX - 26f * density) {
            paint.setColor(Color.rgb(190, 145, 255));
            canvas.drawText("G", x, baseline, paint);
            x += 24f * density;
        }
        if (car.shieldActive && x < maxX - 26f * density) {
            paint.setColor(Color.rgb(92, 235, 132));
            canvas.drawText("S", x, baseline, paint);
        }
        paint.setColor(Color.WHITE);
        paint.setFakeBoldText(false);
    }

    private void drawAwardBadges(Canvas canvas) {
        float size = 13f * density;
        float step = 17f * density;
        float y = 16f * density;
        float left = 12f * density;
        float notchHalf = Math.min(58f * density, getWidth() * 0.18f);
        float center = getWidth() / 2f;
        float rightLimit = menuRect.left - 10f * density;
        int index = drawAwardBadgeLane(canvas, 0, left, center - notchHalf, y, size, step);
        drawAwardBadgeLane(canvas, index, center + notchHalf, rightLimit, y, size, step);
    }

    private int drawAwardBadgeLane(Canvas canvas, int index, float x, float maxX, float y, float size, float step) {
        while (index < awardSymbols.size() && x + size <= maxX) {
            drawBitmapIcon(canvas, "award_" + awardSymbols.get(index), x - size * 0.05f, y - size * 0.55f, size * 1.1f);
            x += step;
            index++;
        }
        return index;
    }

    private String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return (safe / 60) + ":" + String.format("%02d", safe % 60);
    }

    private void drawCoin(Canvas canvas, float x, float y, float tile) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 200, 75));
        canvas.drawCircle(x, y, tile * 0.17f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, tile * 0.05f));
        paint.setColor(Color.rgb(255, 240, 168));
        canvas.drawCircle(x, y, tile * 0.1f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDiamond(Canvas canvas, float x, float y, float tile) {
        Path path = new Path();
        path.moveTo(x, y - tile * 0.22f);
        path.lineTo(x + tile * 0.2f, y);
        path.lineTo(x, y + tile * 0.24f);
        path.lineTo(x - tile * 0.2f, y);
        path.close();
        paint.setColor(Color.rgb(119, 236, 255));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, tile * 0.04f));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawArtifact(Canvas canvas, RemoteArtifact artifact, float tile, float originX, float originY) {
        float x = originX + (artifact.x + 0.5f) * tile;
        float y = originY + (artifact.y + 0.5f) * tile;
        if ("MEDKIT".equals(artifact.type)) {
            drawBitmapIcon(canvas, "medkit", x - tile * 0.3f, y - tile * 0.3f, tile * 0.6f);
        } else if ("BANK".equals(artifact.type)) {
            drawBitmapIcon(canvas, "bank", x - tile * 0.3f, y - tile * 0.3f, tile * 0.6f);
        } else if ("FREEZER".equals(artifact.type) || "SHIELD".equals(artifact.type) || "PORTAL".equals(artifact.type)) {
            String label = "FREEZER".equals(artifact.type) ? "F" : "SHIELD".equals(artifact.type) ? "S" : "P";
            int color = "FREEZER".equals(artifact.type) ? Color.rgb(70, 210, 255)
                    : "SHIELD".equals(artifact.type) ? Color.rgb(77, 230, 116)
                    : Color.rgb(255, 174, 78);
            drawLetterArtifact(canvas, x, y, tile, label, color);
        } else if ("GHOST".equals(artifact.type)) {
            drawLetterArtifact(canvas, x, y, tile, "G", Color.rgb(185, 125, 255));
        }
    }

    private void drawLetterArtifact(Canvas canvas, float x, float y, float tile, String label, int color) {
        float pulse = 0.9f + 0.1f * (float) Math.sin(SystemClock.uptimeMillis() / 130.0);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(x, y, tile * 0.28f * pulse, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, tile * 0.05f));
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, tile * 0.2f * pulse, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(tile * 0.34f);
        canvas.drawText(label, x, y + tile * 0.12f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawExitPortal(Canvas canvas, float x, float y, float tile) {
        float pulse = 0.78f + 0.22f * (float) Math.sin(SystemClock.uptimeMillis() / 90.0);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 240, 120));
        canvas.drawCircle(x, y, tile * 0.34f * pulse, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, tile * 0.07f));
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, tile * 0.24f, paint);
        paint.setStrokeWidth(Math.max(2f, tile * 0.04f));
        paint.setColor(Color.rgb(120, 235, 255));
        canvas.drawCircle(x, y, tile * 0.43f * (1.05f - pulse * 0.15f), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawMedkitIcon(Canvas canvas, float x, float y, float tile) {
        float r = tile * 0.28f;
        RectF body = new RectF(x - r, y - r * 0.7f, x + r, y + r * 0.8f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 248, 250));
        canvas.drawRoundRect(body, r * 0.18f, r * 0.18f, paint);
        paint.setColor(Color.rgb(214, 42, 52));
        canvas.drawRect(x - r * 0.16f, y - r * 0.5f, x + r * 0.16f, y + r * 0.6f, paint);
        canvas.drawRect(x - r * 0.55f, y - r * 0.08f, x + r * 0.55f, y + r * 0.22f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, tile * 0.04f));
        paint.setColor(Color.rgb(92, 96, 106));
        canvas.drawRoundRect(body, r * 0.18f, r * 0.18f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBankIcon(Canvas canvas, float x, float y, float tile) {
        float r = tile * 0.3f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(46, 160, 88));
        canvas.drawRoundRect(new RectF(x - r, y - r * 0.55f, x + r, y + r * 0.75f), r * 0.12f, r * 0.12f, paint);
        Path roof = new Path();
        roof.moveTo(x - r * 1.1f, y - r * 0.55f);
        roof.lineTo(x, y - r * 1.1f);
        roof.lineTo(x + r * 1.1f, y - r * 0.55f);
        roof.close();
        paint.setColor(Color.rgb(34, 116, 66));
        canvas.drawPath(roof, paint);
        paint.setColor(Color.rgb(180, 245, 190));
        canvas.drawCircle(x, y + r * 0.12f, r * 0.38f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(r * 0.62f);
        paint.setFakeBoldText(true);
        paint.setColor(Color.rgb(18, 92, 45));
        canvas.drawText("$", x, y + r * 0.34f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCar(Canvas canvas, RemoteCar car, int body, int accent, float tile, float originX, float originY, boolean playerCar) {
        float cx = originX + car.x * tile;
        float cy = originY + car.y * tile;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate((float) Math.toDegrees(car.angle));
        paint.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(new RectF(-tile * 0.38f, -tile * 0.27f, tile * 0.38f, tile * 0.27f), 6f, 6f, paint);
        paint.setColor(body);
        RectF carRect = new RectF(-tile * 0.36f, -tile * 0.24f, tile * 0.36f, tile * 0.24f);
        canvas.drawRoundRect(carRect, 6f, 6f, paint);
        if (playerCar && car.shieldActive) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, tile * 0.09f));
            paint.setColor(Color.argb(165, 120, 245, 145));
            canvas.drawRoundRect(new RectF(carRect.left - tile * 0.08f, carRect.top - tile * 0.08f, carRect.right + tile * 0.08f, carRect.bottom + tile * 0.08f), 8f, 8f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(Color.rgb(16, 20, 27));
        canvas.drawRect(-tile * 0.12f, -tile * 0.22f, tile * 0.08f, tile * 0.22f, paint);
        paint.setColor(accent);
        canvas.drawRoundRect(new RectF(tile * 0.04f, -tile * 0.15f, tile * 0.24f, tile * 0.15f), 4f, 4f, paint);
        paint.setColor(Color.rgb(247, 240, 232));
        canvas.drawRect(tile * 0.24f, -tile * 0.16f, tile * 0.32f, -tile * 0.06f, paint);
        canvas.drawRect(tile * 0.24f, tile * 0.06f, tile * 0.32f, tile * 0.16f, paint);
        if (!playerCar) {
            paint.setColor(Color.rgb(227, 59, 59));
            canvas.drawRect(-tile * 0.06f, -tile * 0.08f, tile * 0.02f, tile * 0.08f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(tile * 0.03f, -tile * 0.08f, tile * 0.11f, tile * 0.08f, paint);
        }
        canvas.restore();
    }

    private static final class RemoteCar {
        int id;
        float x;
        float y;
        float angle;
        float targetX;
        float targetY;
        float targetAngle;
        int damage;
        int score;
        int total;
        int banknotes;
        boolean ghostActive;
        boolean shieldActive;
        boolean invulnerableActive;
    }

    private static final class RemoteArtifact {
        String type;
        int x;
        int y;
    }
}
