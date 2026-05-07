package com.nagimutech.crimecarcoinchase;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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
    private String status = "Подключение...";
    private String roomCode = "";
    private boolean hasServerState;
    private boolean gestureActive;
    private float gestureStartX;
    private float gestureStartY;
    private Direction lastSent = Direction.NONE;
    private List<String> awardSymbols = new ArrayList<>();

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
                if (Math.hypot(car.x - x, car.y - y) > 1.5) {
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
        float factor = 0.42f;
        car.x += (car.targetX - car.x) * factor;
        car.y += (car.targetY - car.y) * factor;
        float diff = normalizeAngle(car.targetAngle - car.angle);
        car.angle += diff * factor;
        if (Math.abs(car.x - car.targetX) < 0.01f) {
            car.x = car.targetX;
        }
        if (Math.abs(car.y - car.targetY) < 0.01f) {
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
                    paint.setColor(colors.wall);
                    canvas.drawRect(left, top, left + tile, top + tile, paint);
                    paint.setColor(colors.lighten(colors.wall, 36));
                    canvas.drawRect(left + tile * 0.08f, top + tile * 0.08f, left + tile * 0.92f, top + tile * 0.2f, paint);
                } else {
                    paint.setColor(colors.road);
                    canvas.drawRect(left, top, left + tile, top + tile, paint);
                    if (grid[y][x] == COIN) {
                        drawCoin(canvas, left + tile / 2f, top + tile / 2f, tile);
                    } else if (grid[y][x] == DIAMOND) {
                        drawDiamond(canvas, left + tile / 2f, top + tile / 2f, tile);
                    }
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
            int body = car.id == playerId ? colors.player : Color.rgb(255, 135, 46);
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

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(15f * density);
        paint.setColor(Color.WHITE);
        float baseline = 36f * density;
        if (hasServerState) {
            RemoteCar me = me();
            int meScore = me == null ? score : me.score;
            int meTotal = me == null ? total : me.total;
            int meDamage = me == null ? damage : me.damage;
            float x = 14f * density;
            drawMoneyBagIcon(canvas, x, baseline - 16f * density, 18f * density);
            canvas.drawText(meScore + "/" + meTotal, x + 24f * density, baseline, paint);
            x += 118f * density;
            drawDamageIcon(canvas, x, baseline - 18f * density, 19f * density);
            canvas.drawText(meDamage + "/" + GameConfig.MAX_DAMAGE, x + 25f * density, baseline, paint);
            x += 104f * density;
            drawClockIcon(canvas, x, baseline - 17f * density, 18f * density);
            canvas.drawText(formatTime(elapsedSeconds), x + 24f * density, baseline, paint);
            x = 14f * density;
            drawSmallBanknote(canvas, x, 58f * density, 17f * density);
            canvas.drawText(String.valueOf(banknotes), x + 28f * density, 72f * density, paint);
            drawEffectBadges(canvas, me, x + 86f * density, 72f * density, getWidth() - 92f * density);
            drawAwardBadges(canvas, x, 88f * density, getWidth() - 82f * density);
        } else {
            drawSingleLineEllipsized(canvas, status, 14f * density, baseline, getWidth() - 92f * density);
        }

        float size = 54f * density;
        float right = getWidth() - 12f * density;
        menuRect.set(right - size, (h - size) / 2f, right, (h + size) / 2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 0, 0, 0));
        canvas.drawRoundRect(menuRect, 16f * density, 16f * density, paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4f * density);
        float cx = menuRect.centerX();
        float top = menuRect.top + 17f * density;
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
        drawMoneyBagIcon(canvas, x, baseline - 16f * density, 18f * density);
        canvas.drawText(other.score + "/" + other.total, x + 24f * density, baseline, paint);
        x += 118f * density;
        drawSmallBanknote(canvas, x, baseline - 16f * density, 17f * density);
        canvas.drawText(String.valueOf(other.banknotes), x + 28f * density, baseline, paint);
        x += 96f * density;
        drawDamageIcon(canvas, x, baseline - 18f * density, 19f * density);
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
        return 96f * density;
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

    private void drawAwardBadges(Canvas canvas, float x, float y, float maxX) {
        float size = 13f * density;
        float step = 18f * density;
        int count = 0;
        for (String symbol : awardSymbols) {
            if (x + count * step + size > maxX) {
                break;
            }
            float cx = x + count * step + size / 2f;
            float cy = y;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(236, 188, 78));
            canvas.drawCircle(cx, cy, size * 0.55f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, size * 0.08f));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, size * 0.42f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(size * 0.52f);
            paint.setColor(Color.rgb(70, 52, 24));
            String label = symbol == null || symbol.isEmpty() ? "*" : symbol.substring(0, 1).toUpperCase();
            canvas.drawText(label, cx, cy + size * 0.18f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
            count++;
        }
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
            drawMedkitIcon(canvas, x, y, tile);
        } else if ("BANK".equals(artifact.type)) {
            drawBankIcon(canvas, x, y, tile);
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
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(x, y, tile * 0.28f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, tile * 0.05f));
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, tile * 0.2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setTextSize(tile * 0.34f);
        canvas.drawText(label, x, y + tile * 0.12f, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
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
