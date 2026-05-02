(function () {
  "use strict";

  const canvas = document.getElementById("game");
  const ctx = canvas.getContext("2d");
  const scoreEl = document.getElementById("score");
  const damageEl = document.getElementById("damage");
  const overlay = document.getElementById("overlay");
  const overlayKicker = document.getElementById("overlayKicker");
  const overlayTitle = document.getElementById("overlayTitle");
  const overlayText = document.getElementById("overlayText");
  const startBtn = document.getElementById("startBtn");
  const restartBtn = document.getElementById("restartBtn");
  const menuBtn = document.getElementById("menuBtn");
  const exitBtn = document.getElementById("exitBtn");
  const settingsPanel = document.getElementById("settingsPanel");
  const winsList = document.getElementById("winsList");
  const versionLabel = document.getElementById("versionLabel");
  const colorInputs = {
    bg: document.getElementById("bgColor"),
    wall: document.getElementById("wallColor"),
    player: document.getElementById("playerColor"),
    police: document.getElementById("policeColor")
  };
  const difficultyInputs = document.querySelectorAll("input[name='difficulty']");

  const APP_VERSION = "1.1.0";
  const STORAGE_KEYS = {
    wins: "crime-car-coin-chase:wins:v1",
    schema: "crime-car-coin-chase:schema-version"
  };
  const HISTORY_LIMIT = 10;
  const mapWidth = 24;
  const mapHeight = 21;

  const difficultySettings = {
    debut: { label: "Дебют", policeCount: 1, policeSpeed: 1.65, ratingMultiplier: 0.8 },
    beginner: { label: "Начинающий", policeCount: 1, policeSpeed: 2.45, ratingMultiplier: 1 },
    amateur: { label: "Любитель", policeCount: 2, policeSpeed: 2.45, ratingMultiplier: 1.35 },
    pro: { label: "Профессионал", policeCount: 3, policeSpeed: 2.45, ratingMultiplier: 1.7 }
  };

  const dirs = {
    up: { x: 0, y: -1 },
    down: { x: 0, y: 1 },
    left: { x: -1, y: 0 },
    right: { x: 1, y: 0 }
  };

  const tile = canvas.width / mapWidth;
  const rows = mapHeight;
  const cols = mapWidth;
  const maxDamage = 5;
  const coinValue = 1;
  const diamondValue = 10;
  const diamondCount = 8;
  const playerSpeed = 3.4;
  const defaultDifficulty = "beginner";
  const invulnerabilityMs = 1200;

  let grid;
  let player;
  let policeCars;
  let policeStarts;
  let scoreTotal;
  let scoreCollected;
  let damage;
  let status;
  let lastTime;
  let gameStartedAt;
  let animationId;
  let activeDifficulty = defaultDifficulty;
  let activeKey = null;
  const heldKeys = new Set();

  versionLabel.textContent = `v${APP_VERSION}`;

  function cssVar(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  }

  function clampColor(value) {
    return Math.max(0, Math.min(255, value));
  }

  function adjustHex(hex, amount) {
    const clean = hex.replace("#", "");
    const r = clampColor(parseInt(clean.slice(0, 2), 16) + amount);
    const g = clampColor(parseInt(clean.slice(2, 4), 16) + amount);
    const b = clampColor(parseInt(clean.slice(4, 6), 16) + amount);
    return `#${[r, g, b].map((part) => part.toString(16).padStart(2, "0")).join("")}`;
  }

  function loadWins() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEYS.wins) || "[]");
    } catch (error) {
      return [];
    }
  }

  function saveWins(records) {
    try {
      localStorage.setItem(STORAGE_KEYS.schema, "1");
      localStorage.setItem(STORAGE_KEYS.wins, JSON.stringify(records.slice(0, HISTORY_LIMIT)));
    } catch (error) {
      // The game should keep running even if browser storage is unavailable.
    }
  }

  function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${minutes}:${String(rest).padStart(2, "0")}`;
  }

  function renderWins() {
    const wins = loadWins();
    winsList.innerHTML = "";

    if (wins.length === 0) {
      const empty = document.createElement("li");
      empty.textContent = "Побед пока нет";
      winsList.appendChild(empty);
      return;
    }

    wins.forEach((win) => {
      const item = document.createElement("li");
      item.textContent = `${win.rating} очков, ${win.difficulty}, ${formatTime(win.seconds)}, урон ${win.damage}/${maxDamage}`;
      winsList.appendChild(item);
    });
  }

  function calculateRating(seconds) {
    const difficulty = difficultySettings[activeDifficulty];
    const timeBonus = Math.max(0, 360 - seconds) * 2;
    const healthBonus = (maxDamage - damage) * 35;
    return Math.round((scoreCollected + timeBonus + healthBonus) * difficulty.ratingMultiplier);
  }

  function recordWin() {
    const seconds = Math.max(1, Math.round((performance.now() - gameStartedAt) / 1000));
    const record = {
      version: APP_VERSION,
      rating: calculateRating(seconds),
      difficulty: difficultySettings[activeDifficulty].label,
      difficultyKey: activeDifficulty,
      score: scoreCollected,
      damage,
      seconds,
      wonAt: new Date().toISOString()
    };
    const wins = [record, ...loadWins()].sort((a, b) => b.rating - a.rating);
    saveWins(wins);
    renderWins();
    return record;
  }

  function cellToPos(cell) {
    return {
      x: cell.x * tile + tile / 2,
      y: cell.y * tile + tile / 2
    };
  }

  function posToCell(entity) {
    return {
      x: Math.round((entity.x - tile / 2) / tile),
      y: Math.round((entity.y - tile / 2) / tile)
    };
  }

  function isWall(x, y) {
    return y < 0 || y >= rows || x < 0 || x >= cols || grid[y][x].wall;
  }

  function atCenter(entity) {
    const cell = posToCell(entity);
    const center = cellToPos(cell);
    return Math.abs(entity.x - center.x) < 0.8 && Math.abs(entity.y - center.y) < 0.8;
  }

  function snapToCenter(entity) {
    const cell = posToCell(entity);
    const center = cellToPos(cell);
    entity.x = center.x;
    entity.y = center.y;
  }

  function shuffle(items) {
    for (let i = items.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      [items[i], items[j]] = [items[j], items[i]];
    }
    return items;
  }

  function generateMap() {
    const map = Array.from({ length: rows }, () => Array.from({ length: cols }, () => "#"));
    const stack = [{ x: 1, y: 1 }];
    const visited = new Set(["1,1"]);
    map[1][1] = ".";

    while (stack.length > 0) {
      const current = stack[stack.length - 1];
      const neighbors = shuffle([
        { x: current.x + 2, y: current.y },
        { x: current.x - 2, y: current.y },
        { x: current.x, y: current.y + 2 },
        { x: current.x, y: current.y - 2 }
      ]).filter((cell) => cell.x > 0 && cell.x < cols - 1 && cell.y > 0 && cell.y < rows - 1 && !visited.has(`${cell.x},${cell.y}`));

      if (neighbors.length === 0) {
        stack.pop();
        continue;
      }

      const next = neighbors[0];
      map[(current.y + next.y) / 2][(current.x + next.x) / 2] = ".";
      map[next.y][next.x] = ".";
      visited.add(`${next.x},${next.y}`);
      stack.push(next);
    }

    addExtraPassages(map);
    placeMapMarkers(map);
    return map.map((row) => row.join(""));
  }

  function addExtraPassages(map) {
    const candidates = [];
    for (let y = 1; y < rows - 1; y += 1) {
      for (let x = 1; x < cols - 1; x += 1) {
        if (map[y][x] !== "#") {
          continue;
        }

        const horizontal = map[y][x - 1] !== "#" && map[y][x + 1] !== "#";
        const vertical = map[y - 1][x] !== "#" && map[y + 1][x] !== "#";
        if (horizontal || vertical) {
          candidates.push({ x, y });
        }
      }
    }

    shuffle(candidates).slice(0, 28).forEach((cell) => {
      map[cell.y][cell.x] = ".";
    });
  }

  function placeMapMarkers(map) {
    const openCells = [];
    for (let y = 1; y < rows - 1; y += 1) {
      for (let x = 1; x < cols - 1; x += 1) {
        if (map[y][x] !== "#") {
          openCells.push({ x, y });
        }
      }
    }

    const playerStart = { x: 1, y: 1 };
    map[playerStart.y][playerStart.x] = "P";

    const starts = openCells
      .filter((cell) => !(cell.x === playerStart.x && cell.y === playerStart.y))
      .sort((a, b) => {
        const distA = Math.abs(a.x - playerStart.x) + Math.abs(a.y - playerStart.y);
        const distB = Math.abs(b.x - playerStart.x) + Math.abs(b.y - playerStart.y);
        return distB - distA;
      });

    const policeStarts = [];
    starts.forEach((cell) => {
      const farEnough = Math.abs(cell.x - playerStart.x) + Math.abs(cell.y - playerStart.y) > 12;
      const spacedOut = policeStarts.every((used) => Math.abs(cell.x - used.x) + Math.abs(cell.y - used.y) > 8);
      if (farEnough && spacedOut && policeStarts.length < 4) {
        policeStarts.push(cell);
      }
    });

    starts.forEach((cell) => {
      if (policeStarts.length < 4 && !policeStarts.some((used) => used.x === cell.x && used.y === cell.y)) {
        policeStarts.push(cell);
      }
    });

    policeStarts.slice(0, 4).forEach((cell) => {
      map[cell.y][cell.x] = "X";
    });
  }

  function resetGame(showStart) {
    grid = [];
    policeCars = [];
    policeStarts = [];
    scoreTotal = 0;
    scoreCollected = 0;
    damage = 0;
    status = showStart ? "ready" : "playing";
    lastTime = 0;
    gameStartedAt = showStart ? 0 : performance.now();
    activeKey = null;
    heldKeys.clear();

    const currentMap = generateMap();
    currentMap.forEach((row, y) => {
      const cells = [];
      row.split("").forEach((char, x) => {
        const wall = char === "#";
        const hasCoin = char === ".";
        cells.push({ wall, item: hasCoin ? "coin" : null });

        if (hasCoin) {
          scoreTotal += coinValue;
        }

        if (char === "P") {
          const pos = cellToPos({ x, y });
          player = {
            x: pos.x,
            y: pos.y,
            dir: { x: 0, y: 0 },
            nextDir: { x: 0, y: 0 },
            angle: 0,
            invulnerableUntil: 0,
            stopAtCenter: false
          };
        }

        if (char === "X") {
          policeStarts.push({ x, y });
        }
      });
      grid.push(cells);
    });

    placeDiamonds();
    createPoliceCars();
    updateHud();
    draw();

    if (showStart) {
      showOverlay("Готов к погоне?", "Собери все монетки", "Управляй машинкой преступников стрелками клавиатуры. Полиция наносит урон при столкновении.", "Старт");
    } else {
      hideOverlay();
    }
  }

  function updateHud() {
    scoreEl.textContent = `${scoreCollected} / ${scoreTotal}`;
    damageEl.textContent = `${damage} / ${maxDamage}`;
  }

  function showOverlay(kicker, title, text, buttonText) {
    overlayKicker.textContent = kicker;
    overlayTitle.textContent = title;
    overlayText.textContent = text;
    startBtn.textContent = buttonText;
    overlay.classList.remove("hidden");
  }

  function hideOverlay() {
    overlay.classList.add("hidden");
  }

  function exitToMenu() {
    status = "ready";
    heldKeys.clear();
    activeKey = null;
    player.dir = { x: 0, y: 0 };
    player.nextDir = { x: 0, y: 0 };
    player.stopAtCenter = false;
    showOverlay("Пауза", "Выход в меню", "Выбери уровень или настройки цветов, затем начни новый заезд.", "Старт");
  }

  function applyColors() {
    document.documentElement.style.setProperty("--bg", colorInputs.bg.value);
    document.documentElement.style.setProperty("--road", adjustHex(colorInputs.bg.value, 28));
    document.documentElement.style.setProperty("--wall", colorInputs.wall.value);
    document.documentElement.style.setProperty("--wall-top", adjustHex(colorInputs.wall.value, 52));
    document.documentElement.style.setProperty("--player", colorInputs.player.value);
    document.documentElement.style.setProperty("--police", colorInputs.police.value);
    draw();
  }

  function startPlaying() {
    if (status !== "playing") {
      activeDifficulty = getSelectedDifficulty();
      resetGame(false);
    }
  }

  function getSelectedDifficulty() {
    const selected = document.querySelector("input[name='difficulty']:checked");
    return selected && difficultySettings[selected.value] ? selected.value : defaultDifficulty;
  }

  function placeDiamonds() {
    const available = [];
    for (let y = 0; y < rows; y += 1) {
      for (let x = 0; x < cols; x += 1) {
        if (grid[y][x].item === "coin") {
          available.push({ x, y });
        }
      }
    }

    for (let i = available.length - 1; i > 0; i -= 1) {
      const j = Math.floor(Math.random() * (i + 1));
      [available[i], available[j]] = [available[j], available[i]];
    }

    available.slice(0, diamondCount).forEach((cell) => {
      grid[cell.y][cell.x].item = "diamond";
      scoreTotal += diamondValue - coinValue;
    });
  }

  function createPoliceCars() {
    const count = difficultySettings[activeDifficulty].policeCount;
    policeCars = policeStarts.slice(0, count).map((cell) => {
      const pos = cellToPos(cell);
      return {
        x: pos.x,
        y: pos.y,
        dir: { x: 0, y: 0 },
        angle: Math.PI,
        decisionDelay: 0
      };
    });
  }

  function pressDirection(name) {
    if (!dirs[name]) {
      return;
    }

    if (status === "ready") {
      startPlaying();
    }

    activeKey = name;
    heldKeys.add(name);
    player.nextDir = { ...dirs[name] };
    player.stopAtCenter = false;
  }

  function releaseDirection(name) {
    heldKeys.delete(name);
    if (activeKey !== name) {
      return;
    }

    activeKey = Array.from(heldKeys).pop() || null;
    player.nextDir = activeKey ? { ...dirs[activeKey] } : { x: 0, y: 0 };
    player.stopAtCenter = !activeKey;
  }

  function canMove(entity, dir) {
    const cell = posToCell(entity);
    return !isWall(cell.x + dir.x, cell.y + dir.y);
  }

  function moveEntity(entity, speed, delta) {
    const step = speed * (delta / 16.67);
    if (atCenter(entity)) {
      snapToCenter(entity);

      if (entity.nextDir && canMove(entity, entity.nextDir)) {
        entity.dir = { ...entity.nextDir };
      }

      if (entity.stopAtCenter && entity.dir.x === entity.nextDir.x && entity.dir.y === entity.nextDir.y) {
        entity.dir = { x: 0, y: 0 };
        entity.stopAtCenter = false;
      }

      if (!canMove(entity, entity.dir)) {
        entity.dir = { x: 0, y: 0 };
      }
    }

    entity.x += entity.dir.x * step;
    entity.y += entity.dir.y * step;

    const cell = posToCell(entity);
    const target = cellToPos(cell);
    if (entity.dir.x !== 0 && Math.abs(entity.x - target.x) < step) {
      entity.x = target.x;
    }
    if (entity.dir.y !== 0 && Math.abs(entity.y - target.y) < step) {
      entity.y = target.y;
    }

    if (entity.dir.x !== 0 || entity.dir.y !== 0) {
      entity.angle = Math.atan2(entity.dir.y, entity.dir.x);
    }
  }

  function choosePoliceDirection(car) {
    const carCell = posToCell(car);
    const playerCell = posToCell(player);
    const options = Object.values(dirs)
      .filter((dir) => !isWall(carCell.x + dir.x, carCell.y + dir.y))
      .filter((dir) => !(dir.x === -car.dir.x && dir.y === -car.dir.y));

    const candidates = options.length > 0 ? options : Object.values(dirs).filter((dir) => !isWall(carCell.x + dir.x, carCell.y + dir.y));
    if (candidates.length === 0) {
      car.dir = { x: 0, y: 0 };
      return;
    }

    candidates.sort((a, b) => {
      const distA = Math.abs(carCell.x + a.x - playerCell.x) + Math.abs(carCell.y + a.y - playerCell.y);
      const distB = Math.abs(carCell.x + b.x - playerCell.x) + Math.abs(carCell.y + b.y - playerCell.y);
      return distA - distB;
    });

    const bestDistance = Math.abs(carCell.x + candidates[0].x - playerCell.x) + Math.abs(carCell.y + candidates[0].y - playerCell.y);
    const best = candidates.filter((dir) => Math.abs(carCell.x + dir.x - playerCell.x) + Math.abs(carCell.y + dir.y - playerCell.y) === bestDistance);
    car.dir = { ...best[Math.floor(Math.random() * best.length)] };
  }

  function updatePolice(delta) {
    const policeSpeed = difficultySettings[activeDifficulty].policeSpeed;
    policeCars.forEach((car) => {
      car.decisionDelay -= delta;
      if (atCenter(car)) {
        snapToCenter(car);
        if ((car.dir.x === 0 && car.dir.y === 0) || car.decisionDelay <= 0 || !canMove(car, car.dir)) {
          choosePoliceDirection(car);
          car.decisionDelay = 40 + Math.random() * 80;
        }
      }
      moveEntity(car, policeSpeed, delta);
    });
  }

  function collectItem() {
    const cell = posToCell(player);
    const current = grid[cell.y] && grid[cell.y][cell.x];
    if (current && current.item) {
      scoreCollected += current.item === "diamond" ? diamondValue : coinValue;
      current.item = null;
      updateHud();

      if (scoreCollected === scoreTotal) {
        status = "won";
        const win = recordWin();
        showOverlay("Победа", "Все сокровища собраны", `Рейтинг: ${win.rating}. Преступная машинка ушла от погони с монетами и алмазами.`, "Играть снова");
      }
    }
  }

  function checkPoliceHits(now) {
    if (now < player.invulnerableUntil || status !== "playing") {
      return;
    }

    const hitCars = policeCars.filter((car) => Math.hypot(car.x - player.x, car.y - player.y) < tile * 0.62);
    if (hitCars.length === 0) {
      return;
    }

    damage += 1;
    player.invulnerableUntil = now + invulnerabilityMs;
    hitCars.forEach((car) => {
      const reverse = { x: -car.dir.x, y: -car.dir.y };
      if (canMove(car, reverse)) {
        car.dir = reverse;
      } else {
        choosePoliceDirection(car);
      }
      car.decisionDelay = 0;
    });
    updateHud();

    if (damage >= maxDamage) {
      status = "lost";
      showOverlay("Поймали", "Игра окончена", "Полиция нанесла 5 уронов. Попробуй собрать монетки быстрее.", "Играть снова");
    }
  }

  function update(delta, now) {
    if (status !== "playing") {
      return;
    }

    moveEntity(player, playerSpeed, delta);
    updatePolice(delta);
    collectItem();
    checkPoliceHits(now);
  }

  function drawRoad() {
    ctx.fillStyle = cssVar("--bg");
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < rows; y += 1) {
      for (let x = 0; x < cols; x += 1) {
        const px = x * tile;
        const py = y * tile;
        if (grid[y][x].wall) {
          const grd = ctx.createLinearGradient(px, py, px, py + tile);
          grd.addColorStop(0, cssVar("--wall-top"));
          grd.addColorStop(1, cssVar("--wall"));
          ctx.fillStyle = grd;
          ctx.fillRect(px + 1, py + 1, tile - 2, tile - 2);
          ctx.fillStyle = "rgba(255,255,255,0.12)";
          ctx.fillRect(px + 4, py + 4, tile - 8, 3);
        } else {
          ctx.fillStyle = cssVar("--road");
          ctx.fillRect(px, py, tile, tile);
          ctx.fillStyle = "rgba(255,255,255,0.03)";
          ctx.fillRect(px, py, tile, 1);
        }

        if (grid[y][x].item === "coin") {
          drawCoin(px + tile / 2, py + tile / 2);
        } else if (grid[y][x].item === "diamond") {
          drawDiamond(px + tile / 2, py + tile / 2);
        }
      }
    }
  }

  function drawDiamond(x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = "#77ecff";
    ctx.strokeStyle = "#e6fbff";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(0, -tile * 0.22);
    ctx.lineTo(tile * 0.2, 0);
    ctx.lineTo(0, tile * 0.24);
    ctx.lineTo(-tile * 0.2, 0);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.strokeStyle = "rgba(16,20,27,0.35)";
    ctx.beginPath();
    ctx.moveTo(0, -tile * 0.22);
    ctx.lineTo(0, tile * 0.24);
    ctx.moveTo(-tile * 0.2, 0);
    ctx.lineTo(tile * 0.2, 0);
    ctx.stroke();
    ctx.restore();
  }

  function drawCoin(x, y) {
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = "#f5c84b";
    ctx.beginPath();
    ctx.arc(0, 0, tile * 0.17, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#fff0a8";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(0, 0, tile * 0.1, 0, Math.PI * 2);
    ctx.stroke();
    ctx.restore();
  }

  function roundedRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function drawCar(entity, color, accent, isPlayer) {
    const blink = isPlayer && performance.now() < player.invulnerableUntil && Math.floor(performance.now() / 100) % 2 === 0;
    if (blink) {
      return;
    }

    ctx.save();
    ctx.translate(entity.x, entity.y);
    ctx.rotate(entity.angle);
    ctx.fillStyle = "rgba(0,0,0,0.28)";
    roundedRect(-tile * 0.38, -tile * 0.27, tile * 0.76, tile * 0.54, 5);
    ctx.fill();

    ctx.fillStyle = color;
    roundedRect(-tile * 0.36, -tile * 0.24, tile * 0.72, tile * 0.48, 5);
    ctx.fill();

    ctx.fillStyle = "#10141b";
    ctx.fillRect(-tile * 0.12, -tile * 0.22, tile * 0.2, tile * 0.44);

    ctx.fillStyle = accent;
    roundedRect(tile * 0.04, -tile * 0.15, tile * 0.2, tile * 0.3, 3);
    ctx.fill();

    ctx.fillStyle = "#f7f0e8";
    ctx.fillRect(tile * 0.24, -tile * 0.16, tile * 0.08, tile * 0.1);
    ctx.fillRect(tile * 0.24, tile * 0.06, tile * 0.08, tile * 0.1);

    ctx.fillStyle = "#0b0d12";
    ctx.fillRect(-tile * 0.25, -tile * 0.3, tile * 0.14, tile * 0.1);
    ctx.fillRect(-tile * 0.25, tile * 0.2, tile * 0.14, tile * 0.1);
    ctx.fillRect(tile * 0.15, -tile * 0.3, tile * 0.14, tile * 0.1);
    ctx.fillRect(tile * 0.15, tile * 0.2, tile * 0.14, tile * 0.1);

    if (!isPlayer) {
      ctx.fillStyle = "#e33b3b";
      ctx.fillRect(-tile * 0.06, -tile * 0.08, tile * 0.08, tile * 0.16);
      ctx.fillStyle = "#f1f5ff";
      ctx.fillRect(tile * 0.03, -tile * 0.08, tile * 0.08, tile * 0.16);
    }

    ctx.restore();
  }

  function draw() {
    drawRoad();
    policeCars.forEach((car) => drawCar(car, cssVar("--police"), "#c9dfff", false));
    drawCar(player, cssVar("--player"), "#f3b047", true);
  }

  function loop(now) {
    const delta = lastTime ? Math.min(34, now - lastTime) : 16.67;
    lastTime = now;

    update(delta, now);
    draw();
    animationId = requestAnimationFrame(loop);
  }

  document.addEventListener("keydown", (event) => {
    const keyMap = {
      ArrowUp: "up",
      ArrowDown: "down",
      ArrowLeft: "left",
      ArrowRight: "right"
    };

    if (keyMap[event.key]) {
      event.preventDefault();
      pressDirection(keyMap[event.key]);
    }
  });

  document.addEventListener("keyup", (event) => {
    const keyMap = {
      ArrowUp: "up",
      ArrowDown: "down",
      ArrowLeft: "left",
      ArrowRight: "right"
    };

    if (keyMap[event.key]) {
      event.preventDefault();
      releaseDirection(keyMap[event.key]);
    }
  });

  window.addEventListener("blur", () => {
    heldKeys.clear();
    activeKey = null;
    player.nextDir = { x: 0, y: 0 };
    player.stopAtCenter = true;
  });

  document.querySelectorAll(".control-btn").forEach((button) => {
    button.addEventListener("pointerdown", () => pressDirection(button.dataset.dir));
    button.addEventListener("pointerup", () => releaseDirection(button.dataset.dir));
    button.addEventListener("pointercancel", () => releaseDirection(button.dataset.dir));
    button.addEventListener("pointerleave", () => releaseDirection(button.dataset.dir));
  });

  startBtn.addEventListener("click", startPlaying);
  restartBtn.addEventListener("click", () => resetGame(false));
  menuBtn.addEventListener("click", () => {
    settingsPanel.classList.toggle("hidden");
  });
  exitBtn.addEventListener("click", exitToMenu);
  Object.values(colorInputs).forEach((input) => {
    input.addEventListener("input", applyColors);
  });
  difficultyInputs.forEach((input) => {
    input.addEventListener("change", () => {
      if (status !== "playing") {
        activeDifficulty = getSelectedDifficulty();
        resetGame(true);
      }
    });
  });

  renderWins();
  resetGame(true);
  cancelAnimationFrame(animationId);
  animationId = requestAnimationFrame(loop);
})();
