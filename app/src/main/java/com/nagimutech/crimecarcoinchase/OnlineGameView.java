package com.nagimutech.crimecarcoinchase;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
    private RectF menuRect = new RectF();
    private int[][] grid = new int[0][0];
    private int mapWidth;
    private int mapHeight;
    private int playerId;
    private int score;
    private int total;
    private int damage;
    private int stage = 1;
    private String status = "Подключение...";
    private String roomCode = "";
    private boolean gestureActive;
    private float gestureStartX;
    private float gestureStartY;
    private Direction lastSent = Direction.NONE;

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

    void applyState(JSONObject state) {
        mapWidth = state.optInt("width", mapWidth);
        mapHeight = state.optInt("height", mapHeight);
        stage = state.optInt("stage", stage);
        score = state.optInt("score", score);
        total = state.optInt("total", total);
        damage = state.optInt("damage", damage);
        status = state.optString("statusText", status);

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

        players.clear();
        readCars(state.optJSONArray("players"), players);
        police.clear();
        readCars(state.optJSONArray("police"), police);
        invalidate();
    }

    private void readCars(JSONArray array, List<RemoteCar> target) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                RemoteCar car = new RemoteCar();
                car.id = item.optInt("id", i + 1);
                car.x = (float) item.optDouble("x", 1.5);
                car.y = (float) item.optDouble("y", 1.5);
                car.angle = (float) item.optDouble("angle", 0.0);
                car.damage = item.optInt("damage", 0);
                target.add(car);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(colors.background);
        drawMap(canvas);
        drawTopBar(canvas);
        postInvalidateDelayed(100);
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
        float topBar = 82f * density;
        if (mapWidth <= 0 || mapHeight <= 0 || grid.length == 0) {
            paint.setColor(Color.WHITE);
            paint.setTextSize(18f * density);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(status, getWidth() / 2f, topBar + 90f * density, paint);
            return;
        }
        float playHeight = Math.max(1f, getHeight() - topBar);
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

        for (RemoteCar car : players) {
            int body = car.id == playerId ? colors.player : Color.rgb(255, 135, 46);
            drawCar(canvas, car, body, Color.rgb(255, 229, 120), tile, originX, originY, true);
        }
        for (RemoteCar car : police) {
            drawCar(canvas, car, colors.police, Color.WHITE, tile, originX, originY, false);
        }
    }

    private void drawTopBar(Canvas canvas) {
        float h = 82f * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(222, 6, 10, 18));
        canvas.drawRect(0f, 0f, getWidth(), h, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(15f * density);
        paint.setColor(Color.WHITE);
        float y = 34f * density;
        canvas.drawText("$ " + score + "/" + total, 14f * density, y, paint);
        canvas.drawText("♥ " + damage + "/" + GameConfig.MAX_DAMAGE, 122f * density, y, paint);
        canvas.drawText("Поле " + stage, 224f * density, y, paint);
        if (!roomCode.isEmpty()) {
            paint.setTextSize(13f * density);
            canvas.drawText("Код: " + roomCode, 14f * density, 62f * density, paint);
        }
        paint.setFakeBoldText(false);
        paint.setTextSize(12f * density);
        paint.setColor(Color.rgb(120, 225, 235));
        canvas.drawText(status, 122f * density, 62f * density, paint);

        float size = 54f * density;
        float right = getWidth() - 12f * density;
        menuRect.set(right - size, 14f * density, right, 14f * density + size);
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

    private void drawCar(Canvas canvas, RemoteCar car, int body, int accent, float tile, float originX, float originY, boolean playerCar) {
        float cx = originX + car.x * tile;
        float cy = originY + car.y * tile;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate((float) Math.toDegrees(car.angle));
        paint.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(new RectF(-tile * 0.38f, -tile * 0.27f, tile * 0.38f, tile * 0.27f), 6f, 6f, paint);
        paint.setColor(body);
        canvas.drawRoundRect(new RectF(-tile * 0.36f, -tile * 0.24f, tile * 0.36f, tile * 0.24f), 6f, 6f, paint);
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
        int damage;
    }
}
