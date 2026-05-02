package com.nagimutech.crimecarcoinchase;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class GameView extends View {
    interface Listener {
        void onHudChanged(int score, int total, int damage);
        void onWin(int rating, int seconds, int damage);
        void onLose();
        void onMenuRequested();
    }

    private static final int WALL = 0;
    private static final int EMPTY = 1;
    private static final int COIN = 2;
    private static final int DIAMOND = 3;
    private static final int COIN_VALUE = 1;
    private static final int DIAMOND_VALUE = 10;
    private static final int DIAMOND_COUNT = 8;
    private static final float PLAYER_SPEED = 2.4f;
    private static final long INVULNERABLE_MS = 1200L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final GameColors colors;
    private final Listener listener;
    private final float density;

    private int[][] grid = new int[GameConfig.MAP_HEIGHT][GameConfig.MAP_WIDTH];
    private final List<Cell> policeStarts = new ArrayList<>();
    private final List<Car> policeCars = new ArrayList<>();
    private Car player;
    private Difficulty difficulty = Difficulty.BEGINNER;
    private Direction heldDirection = Direction.NONE;
    private int scoreTotal;
    private int scoreCollected;
    private int damage;
    private long startedAt;
    private long lastFrameAt;
    private long pausedAt;
    private boolean paused;
    private boolean playing;
    private boolean roundOver;
    private boolean gestureActive;
    private float gestureStartX;
    private float gestureStartY;
    private RectF menuRect = new RectF();

    GameView(Context context, GameColors colors, Listener listener) {
        super(context);
        this.colors = colors;
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        reset(Difficulty.BEGINNER, false);
    }

    void reset(Difficulty newDifficulty, boolean startPlaying) {
        difficulty = newDifficulty;
        heldDirection = Direction.NONE;
        scoreTotal = 0;
        scoreCollected = 0;
        damage = 0;
        playing = startPlaying;
        paused = false;
        roundOver = false;
        gestureActive = false;
        startedAt = startPlaying ? SystemClock.uptimeMillis() : 0L;
        pausedAt = 0L;
        lastFrameAt = 0L;
        generateMap();
        placeDiamonds();
        createPoliceCars();
        listener.onHudChanged(scoreCollected, scoreTotal, damage);
        invalidate();
    }

    void start(Difficulty newDifficulty) {
        reset(newDifficulty, true);
    }

    void exitToMenu() {
        playing = false;
        heldDirection = Direction.NONE;
        if (player != null) {
            player.dir = Direction.NONE;
            player.nextDir = Direction.NONE;
            player.stopAtCenter = false;
        }
        roundOver = true;
        invalidate();
    }

    void pauseForMenu() {
        if (!paused) {
            paused = true;
            pausedAt = SystemClock.uptimeMillis();
        }
        invalidate();
    }

    void resumeFromMenu() {
        if (paused) {
            if (startedAt > 0L) {
                startedAt += SystemClock.uptimeMillis() - pausedAt;
            }
            paused = false;
            pausedAt = 0L;
            lastFrameAt = 0L;
        }
        invalidate();
    }

    boolean isPlaying() {
        return playing;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (playing && !paused) {
            float delta = lastFrameAt == 0L ? 16.67f : Math.min(34f, now - lastFrameAt);
            update(delta, now);
        }
        lastFrameAt = now;

        drawGame(canvas);
        drawOverlay(canvas);
        if (playing && !paused) {
            postInvalidateOnAnimation();
        }
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
            if (!playing || roundOver) {
                start(difficulty);
            }
            gestureActive = true;
            gestureStartX = x;
            gestureStartY = y;
            setGestureDirection(Direction.NONE);
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE && gestureActive) {
            updateGestureDirection(x, y);
            if (playing && !paused) {
                postInvalidateOnAnimation();
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            gestureActive = false;
            setGestureDirection(Direction.NONE);
            invalidate();
            return true;
        }

        return true;
    }

    private void updateGestureDirection(float x, float y) {
        float dx = x - gestureStartX;
        float dy = y - gestureStartY;
        float deadZone = 24f * density;
        if (Math.hypot(dx, dy) < deadZone) {
            setGestureDirection(Direction.NONE);
            return;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            setGestureDirection(dx > 0 ? Direction.RIGHT : Direction.LEFT);
        } else {
            setGestureDirection(dy > 0 ? Direction.DOWN : Direction.UP);
        }
    }

    private void setGestureDirection(Direction direction) {
        heldDirection = direction;
        if (player == null) {
            return;
        }
        player.nextDir = direction;
        if (direction == Direction.NONE) {
            player.dir = Direction.NONE;
            player.nextDir = Direction.NONE;
            player.stopAtCenter = false;
        } else {
            if (player.dir == Direction.NONE && alignStoppedCarForDirection(player, direction)) {
                player.dir = direction;
            }
            player.stopAtCenter = false;
        }
    }

    private boolean alignStoppedCarForDirection(Car car, Direction direction) {
        float originalX = car.x;
        float originalY = car.y;
        float centerX = Math.round(car.x - 0.5f) + 0.5f;
        float centerY = Math.round(car.y - 0.5f) + 0.5f;
        if (direction.dx != 0) {
            car.y = centerY;
        }
        if (direction.dy != 0) {
            car.x = centerX;
        }
        if (canMove(car, direction)) {
            return true;
        }
        car.x = originalX;
        car.y = originalY;
        return false;
    }

    private void update(float delta, long now) {
        move(player, PLAYER_SPEED, delta);
        updatePolice(delta);
        collectItem();
        checkHits(now);
    }

    private void generateMap() {
        policeStarts.clear();
        for (int y = 0; y < GameConfig.MAP_HEIGHT; y++) {
            for (int x = 0; x < GameConfig.MAP_WIDTH; x++) {
                grid[y][x] = WALL;
            }
        }

        ArrayList<Cell> stack = new ArrayList<>();
        boolean[][] visited = new boolean[GameConfig.MAP_HEIGHT][GameConfig.MAP_WIDTH];
        stack.add(new Cell(1, 1));
        visited[1][1] = true;
        grid[1][1] = COIN;

        while (!stack.isEmpty()) {
            Cell current = stack.get(stack.size() - 1);
            ArrayList<Cell> neighbors = new ArrayList<>();
            addMazeNeighbor(neighbors, visited, current.x + 2, current.y);
            addMazeNeighbor(neighbors, visited, current.x - 2, current.y);
            addMazeNeighbor(neighbors, visited, current.x, current.y + 2);
            addMazeNeighbor(neighbors, visited, current.x, current.y - 2);
            Collections.shuffle(neighbors, random);

            if (neighbors.isEmpty()) {
                stack.remove(stack.size() - 1);
                continue;
            }

            Cell next = neighbors.get(0);
            grid[(current.y + next.y) / 2][(current.x + next.x) / 2] = COIN;
            grid[next.y][next.x] = COIN;
            visited[next.y][next.x] = true;
            stack.add(next);
        }

        addExtraPassages();
        addSideTunnels();
        placeMarkers();
    }

    private void addMazeNeighbor(List<Cell> neighbors, boolean[][] visited, int x, int y) {
        if (x > 0 && x < GameConfig.MAP_WIDTH - 1 && y > 0 && y < GameConfig.MAP_HEIGHT - 1 && !visited[y][x]) {
            neighbors.add(new Cell(x, y));
        }
    }

    private void addExtraPassages() {
        ArrayList<Cell> candidates = new ArrayList<>();
        for (int y = 1; y < GameConfig.MAP_HEIGHT - 1; y++) {
            for (int x = 1; x < GameConfig.MAP_WIDTH - 1; x++) {
                if (grid[y][x] != WALL) {
                    continue;
                }
                boolean horizontal = grid[y][x - 1] != WALL && grid[y][x + 1] != WALL;
                boolean vertical = grid[y - 1][x] != WALL && grid[y + 1][x] != WALL;
                if (horizontal || vertical) {
                    candidates.add(new Cell(x, y));
                }
            }
        }
        Collections.shuffle(candidates, random);
        for (int i = 0; i < Math.min(28, candidates.size()); i++) {
            Cell cell = candidates.get(i);
            grid[cell.y][cell.x] = COIN;
        }
    }

    private void addSideTunnels() {
        addTunnelRow(5);
        addTunnelRow(GameConfig.MAP_HEIGHT - 6);
    }

    private void addTunnelRow(int y) {
        grid[y][0] = EMPTY;
        grid[y][GameConfig.MAP_WIDTH - 1] = EMPTY;
        if (grid[y][1] == WALL) {
            grid[y][1] = COIN;
        }
        if (grid[y][GameConfig.MAP_WIDTH - 2] == WALL) {
            grid[y][GameConfig.MAP_WIDTH - 2] = COIN;
        }
    }

    private void placeMarkers() {
        ArrayList<Cell> open = new ArrayList<>();
        for (int y = 1; y < GameConfig.MAP_HEIGHT - 1; y++) {
            for (int x = 1; x < GameConfig.MAP_WIDTH - 1; x++) {
                if (grid[y][x] != WALL) {
                    open.add(new Cell(x, y));
                    if (grid[y][x] == COIN) {
                        scoreTotal += COIN_VALUE;
                    }
                }
            }
        }

        Cell playerCell = new Cell(1, 1);
        player = new Car(playerCell);
        if (grid[playerCell.y][playerCell.x] == COIN) {
            scoreTotal -= COIN_VALUE;
        }
        grid[playerCell.y][playerCell.x] = EMPTY;

        open.sort(new Comparator<Cell>() {
            @Override
            public int compare(Cell a, Cell b) {
                return Integer.compare(distance(b, playerCell), distance(a, playerCell));
            }
        });

        for (Cell cell : open) {
            if (distance(cell, playerCell) <= 12) {
                continue;
            }
            boolean spacedOut = true;
            for (Cell used : policeStarts) {
                if (distance(cell, used) <= 8) {
                    spacedOut = false;
                    break;
                }
            }
            if (spacedOut) {
                policeStarts.add(cell);
            }
            if (policeStarts.size() == 4) {
                break;
            }
        }

        for (Cell cell : open) {
            if (policeStarts.size() == 4) {
                break;
            }
            if (!policeStarts.contains(cell)) {
                policeStarts.add(cell);
            }
        }

        for (Cell cell : policeStarts) {
            if (grid[cell.y][cell.x] == COIN) {
                grid[cell.y][cell.x] = EMPTY;
                scoreTotal -= COIN_VALUE;
            }
        }
    }

    private void placeDiamonds() {
        ArrayList<Cell> coins = new ArrayList<>();
        for (int y = 1; y < GameConfig.MAP_HEIGHT - 1; y++) {
            for (int x = 1; x < GameConfig.MAP_WIDTH - 1; x++) {
                if (grid[y][x] == COIN) {
                    coins.add(new Cell(x, y));
                }
            }
        }
        Collections.shuffle(coins, random);
        for (int i = 0; i < Math.min(DIAMOND_COUNT, coins.size()); i++) {
            Cell cell = coins.get(i);
            grid[cell.y][cell.x] = DIAMOND;
            scoreTotal += DIAMOND_VALUE - COIN_VALUE;
        }
    }

    private void createPoliceCars() {
        policeCars.clear();
        for (int i = 0; i < Math.min(difficulty.policeCount, policeStarts.size()); i++) {
            policeCars.add(new Car(policeStarts.get(i)));
        }
    }

    private void updatePolice(float delta) {
        for (Car car : policeCars) {
            car.decisionDelay -= delta;
            if (atCenter(car)) {
                snap(car);
                if (car.dir == Direction.NONE || car.decisionDelay <= 0 || !canMove(car, car.dir)) {
                    choosePoliceDirection(car);
                    car.decisionDelay = 40f + random.nextFloat() * 80f;
                }
            }
            move(car, difficulty.policeSpeed, delta);
        }
    }

    private void choosePoliceDirection(Car car) {
        Cell carCell = toCell(car);
        Cell playerCell = toCell(player);
        ArrayList<Direction> candidates = new ArrayList<>();
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT}) {
            if (canMove(car, direction) && direction != car.dir.reverse()) {
                candidates.add(direction);
            }
        }
        if (candidates.isEmpty()) {
            for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT}) {
                if (canMove(car, direction)) {
                    candidates.add(direction);
                }
            }
        }
        if (candidates.isEmpty()) {
            car.dir = Direction.NONE;
            return;
        }
        int bestDistance = Integer.MAX_VALUE;
        ArrayList<Direction> best = new ArrayList<>();
        for (Direction direction : candidates) {
            int dist = Math.abs(carCell.x + direction.dx - playerCell.x) + Math.abs(carCell.y + direction.dy - playerCell.y);
            if (dist < bestDistance) {
                bestDistance = dist;
                best.clear();
            }
            if (dist == bestDistance) {
                best.add(direction);
            }
        }
        car.dir = best.get(random.nextInt(best.size()));
    }

    private void move(Car car, float speed, float delta) {
        float step = speed * (delta / 1000f);
        if (atCenter(car)) {
            snap(car);
            if (car.nextDir != Direction.NONE && canMove(car, car.nextDir)) {
                car.dir = car.nextDir;
            }
            if (car.stopAtCenter && car.dir == car.nextDir) {
                car.dir = Direction.NONE;
                car.stopAtCenter = false;
            }
            if (!canMove(car, car.dir)) {
                car.dir = Direction.NONE;
            }
        }

        float oldX = car.x;
        float oldY = car.y;
        car.x += car.dir.dx * step;
        car.y += car.dir.dy * step;
        wrapTunnel(car);

        if (car.dir.dx != 0) {
            float targetX = nextCenter(oldX, car.dir.dx);
            if ((car.dir.dx > 0 && oldX <= targetX && car.x >= targetX)
                    || (car.dir.dx < 0 && oldX >= targetX && car.x <= targetX)) {
                car.x = targetX;
            }
        }
        if (car.dir.dy != 0) {
            float targetY = nextCenter(oldY, car.dir.dy);
            if ((car.dir.dy > 0 && oldY <= targetY && car.y >= targetY)
                    || (car.dir.dy < 0 && oldY >= targetY && car.y <= targetY)) {
                car.y = targetY;
            }
        }
        if (car.dir != Direction.NONE) {
            car.angle = (float) Math.atan2(car.dir.dy, car.dir.dx);
        }
    }

    private void wrapTunnel(Car car) {
        Cell cell = toCell(car);
        if (!isTunnelRow(cell.y)) {
            return;
        }
        if (car.x < 0.5f) {
            car.x = GameConfig.MAP_WIDTH - 0.5f;
        } else if (car.x > GameConfig.MAP_WIDTH - 0.5f) {
            car.x = 0.5f;
        }
    }

    private float nextCenter(float position, int axisDirection) {
        if (axisDirection > 0) {
            return (float) Math.floor(position + 0.5f) + 0.5f;
        }
        return (float) Math.ceil(position - 0.5f) - 0.5f;
    }

    private void collectItem() {
        Cell cell = toCell(player);
        int item = grid[cell.y][cell.x];
        if (item == COIN || item == DIAMOND) {
            scoreCollected += item == DIAMOND ? DIAMOND_VALUE : COIN_VALUE;
            grid[cell.y][cell.x] = EMPTY;
            listener.onHudChanged(scoreCollected, scoreTotal, damage);
            if (scoreCollected == scoreTotal) {
                playing = false;
                roundOver = true;
                int seconds = Math.max(1, Math.round((SystemClock.uptimeMillis() - startedAt) / 1000f));
                int rating = calculateRating(seconds);
                listener.onWin(rating, seconds, damage);
            }
        }
    }

    private int calculateRating(int seconds) {
        int timeBonus = Math.max(0, 360 - seconds) * 2;
        int healthBonus = (GameConfig.MAX_DAMAGE - damage) * 35;
        return Math.round((scoreCollected + timeBonus + healthBonus) * difficulty.ratingMultiplier);
    }

    private void checkHits(long now) {
        if (now < player.invulnerableUntil) {
            return;
        }
        ArrayList<Car> hits = new ArrayList<>();
        for (Car car : policeCars) {
            if (Math.hypot(car.x - player.x, car.y - player.y) < 0.62f) {
                hits.add(car);
            }
        }
        if (hits.isEmpty()) {
            return;
        }
        damage++;
        player.invulnerableUntil = now + INVULNERABLE_MS;
        for (Car car : hits) {
            Direction reverse = car.dir.reverse();
            car.dir = canMove(car, reverse) ? reverse : Direction.NONE;
            car.decisionDelay = 0f;
        }
        listener.onHudChanged(scoreCollected, scoreTotal, damage);
        if (damage >= GameConfig.MAX_DAMAGE) {
            playing = false;
            roundOver = true;
            listener.onLose();
        }
    }

    private void drawGame(Canvas canvas) {
        float topBarHeight = topBarHeight();
        float playHeight = Math.max(1f, getHeight() - topBarHeight);
        float tile = Math.min(getWidth() / (float) GameConfig.MAP_WIDTH, playHeight / (float) GameConfig.MAP_HEIGHT);
        float originX = (getWidth() - tile * GameConfig.MAP_WIDTH) / 2f;
        float originY = topBarHeight + (playHeight - tile * GameConfig.MAP_HEIGHT) / 2f;
        canvas.drawColor(colors.background);

        for (int y = 0; y < GameConfig.MAP_HEIGHT; y++) {
            for (int x = 0; x < GameConfig.MAP_WIDTH; x++) {
                float px = originX + x * tile;
                float py = originY + y * tile;
                if (grid[y][x] == WALL) {
                    paint.setShader(new LinearGradient(px, py, px, py + tile, colors.lighten(colors.wall, 52), colors.wall, Shader.TileMode.CLAMP));
                    float inset = Math.max(0.5f, tile * 0.025f);
                    canvas.drawRect(px + inset, py + inset, px + tile - inset, py + tile - inset, paint);
                    paint.setShader(null);
                } else {
                    paint.setColor(colors.road);
                    canvas.drawRect(px, py, px + tile, py + tile, paint);
                }
                if (grid[y][x] == COIN) {
                    drawCoin(canvas, px + tile / 2f, py + tile / 2f, tile);
                } else if (grid[y][x] == DIAMOND) {
                    drawDiamond(canvas, px + tile / 2f, py + tile / 2f, tile);
                }
            }
        }

        for (Car car : policeCars) {
            drawCar(canvas, car, colors.police, Color.rgb(201, 223, 255), tile, originX, originY, false);
        }
        boolean blink = playing && SystemClock.uptimeMillis() < player.invulnerableUntil && (SystemClock.uptimeMillis() / 100) % 2 == 0;
        if (!blink) {
            drawCar(canvas, player, colors.player, Color.rgb(243, 176, 71), tile, originX, originY, true);
        }
    }

    private float topBarHeight() {
        return 76f * density;
    }

    private void drawOverlay(Canvas canvas) {
        float barHeight = topBarHeight();
        float safeTop = 8f * density;
        float button = 48f * density;
        float margin = 12f * density;
        menuRect.set(getWidth() - margin - button, safeTop + 6f * density, getWidth() - margin, safeTop + 6f * density + button);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(178, 0, 0, 0));
        canvas.drawRect(0f, 0f, getWidth(), barHeight, paint);

        paint.setColor(Color.argb(130, 0, 0, 0));
        canvas.drawRoundRect(menuRect, 18f * density, 18f * density, paint);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        float lineLeft = menuRect.left + 13f * density;
        float lineRight = menuRect.right - 13f * density;
        for (int i = -1; i <= 1; i++) {
            float ly = menuRect.centerY() + i * 9f * density;
            canvas.drawRoundRect(new RectF(lineLeft, ly - 2f * density, lineRight, ly + 2f * density), 3f * density, 3f * density, paint);
        }

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(230, 255, 255, 255));
        paint.setTextSize(15f * density);
        paint.setFakeBoldText(true);
        canvas.drawText("Богатство: " + scoreCollected + " / " + scoreTotal, margin, 32f * density, paint);
        canvas.drawText("Урон: " + damage + " / " + GameConfig.MAX_DAMAGE, margin, 58f * density, paint);

        if (!playing || roundOver) {
            paint.setColor(Color.argb(235, 245, 200, 75));
            paint.setTextSize(12f * density);
            String text = roundOver ? "Коснись поля для нового заезда" : "Коснись поля и веди палец";
            canvas.drawText(text, getWidth() * 0.38f, 58f * density, paint);
        } else if (paused) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.argb(210, 255, 255, 255));
            paint.setTextSize(20f * density);
            canvas.drawText("Пауза", getWidth() / 2f, getHeight() / 2f, paint);
        }
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
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

    private void drawCar(Canvas canvas, Car car, int body, int accent, float tile, float originX, float originY, boolean isPlayer) {
        float cx = originX + car.x * tile;
        float cy = originY + car.y * tile;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate((float) Math.toDegrees(car.angle));
        paint.setColor(Color.argb(80, 0, 0, 0));
        RectF shadow = new RectF(-tile * 0.38f, -tile * 0.27f, tile * 0.38f, tile * 0.27f);
        canvas.drawRoundRect(shadow, 6f, 6f, paint);
        paint.setColor(body);
        RectF carRect = new RectF(-tile * 0.36f, -tile * 0.24f, tile * 0.36f, tile * 0.24f);
        canvas.drawRoundRect(carRect, 6f, 6f, paint);
        paint.setColor(Color.rgb(16, 20, 27));
        canvas.drawRect(-tile * 0.12f, -tile * 0.22f, tile * 0.08f, tile * 0.22f, paint);
        paint.setColor(accent);
        canvas.drawRoundRect(new RectF(tile * 0.04f, -tile * 0.15f, tile * 0.24f, tile * 0.15f), 4f, 4f, paint);
        paint.setColor(Color.rgb(247, 240, 232));
        canvas.drawRect(tile * 0.24f, -tile * 0.16f, tile * 0.32f, -tile * 0.06f, paint);
        canvas.drawRect(tile * 0.24f, tile * 0.06f, tile * 0.32f, tile * 0.16f, paint);
        if (!isPlayer) {
            paint.setColor(Color.rgb(227, 59, 59));
            canvas.drawRect(-tile * 0.06f, -tile * 0.08f, tile * 0.02f, tile * 0.08f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(tile * 0.03f, -tile * 0.08f, tile * 0.11f, tile * 0.08f, paint);
        }
        canvas.restore();
    }

    private boolean canMove(Car car, Direction direction) {
        if (direction == Direction.NONE) {
            return false;
        }
        Cell cell = toCell(car);
        int x = cell.x + direction.dx;
        int y = cell.y + direction.dy;
        if (isTunnelRow(cell.y) && y == cell.y && (x < 0 || x >= GameConfig.MAP_WIDTH)) {
            return true;
        }
        return x >= 0 && y >= 0 && x < GameConfig.MAP_WIDTH && y < GameConfig.MAP_HEIGHT && grid[y][x] != WALL;
    }

    private boolean isTunnelRow(int y) {
        return y == 5 || y == GameConfig.MAP_HEIGHT - 6;
    }

    private boolean atCenter(Car car) {
        return Math.abs(car.x - (Math.round(car.x - 0.5f) + 0.5f)) < 0.003f
                && Math.abs(car.y - (Math.round(car.y - 0.5f) + 0.5f)) < 0.003f;
    }

    private void snap(Car car) {
        Cell cell = toCell(car);
        car.x = cell.x + 0.5f;
        car.y = cell.y + 0.5f;
    }

    private Cell toCell(Car car) {
        return new Cell(Math.round(car.x - 0.5f), Math.round(car.y - 0.5f));
    }

    private int distance(Cell a, Cell b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private static final class Cell {
        final int x;
        final int y;

        Cell(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Cell)) {
                return false;
            }
            Cell other = (Cell) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return x * 31 + y;
        }
    }

    private static final class Car {
        float x;
        float y;
        float angle;
        float decisionDelay;
        long invulnerableUntil;
        Direction dir = Direction.NONE;
        Direction nextDir = Direction.NONE;
        boolean stopAtCenter;

        Car(Cell cell) {
            x = cell.x + 0.5f;
            y = cell.y + 0.5f;
        }
    }
}
