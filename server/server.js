const crypto = require("crypto");
const http = require("http");

const PORT = Number(process.env.PORT || 8080);
const WIDTH = 17;
const HEIGHT = 29;
const MAX_DAMAGE = 5;
const PLAYER_SPEED = 3.0;
const POLICE_SPEED = 1.35;
const TICK_MS = 50;
const BANK_SPAWN_MS = 10000;
const BANK_VISIBLE_MS = 30000;
const MEDKIT_VISIBLE_MS = 15000;
const ARTIFACT_INTERVAL_MS = 15000;
const ARTIFACT_VISIBLE_MS = 15000;
const GHOST_MS = 7000;

const rooms = new Map();

const server = http.createServer((req, res) => {
  if (req.url === "/health") {
    res.writeHead(200, { "content-type": "text/plain; charset=utf-8" });
    res.end("ok");
    return;
  }
  res.writeHead(404);
  res.end();
});

server.on("upgrade", (req, socket) => {
  if (!req.url.startsWith("/game")) {
    socket.destroy();
    return;
  }
  const key = req.headers["sec-websocket-key"];
  if (!key) {
    socket.destroy();
    return;
  }
  const accept = crypto
    .createHash("sha1")
    .update(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
    .digest("base64");
  socket.write([
    "HTTP/1.1 101 Switching Protocols",
    "Upgrade: websocket",
    "Connection: Upgrade",
    `Sec-WebSocket-Accept: ${accept}`,
    "",
    "",
  ].join("\r\n"));
  attachClient(socket);
});

server.listen(PORT, () => {
  console.log(`Crime Car Coin Chase server listening on ${PORT}`);
});

setInterval(() => {
  const now = Date.now();
  for (const room of rooms.values()) {
    updateRoom(room, TICK_MS / 1000, now);
    broadcast(room, { type: "state", state: serializeRoom(room) });
    if (room.clients.length === 0 || now - room.updatedAt > 30 * 60 * 1000) {
      rooms.delete(room.code);
    }
  }
}, TICK_MS);

function attachClient(socket) {
  const client = { socket, room: null, playerId: 0, buffer: Buffer.alloc(0), alive: true };
  socket.on("data", (chunk) => {
    client.buffer = Buffer.concat([client.buffer, chunk]);
    readFrames(client);
  });
  socket.on("close", () => removeClient(client));
  socket.on("error", () => removeClient(client));
}

function readFrames(client) {
  while (client.buffer.length >= 2) {
    const first = client.buffer[0];
    const second = client.buffer[1];
    const opcode = first & 0x0f;
    let offset = 2;
    let length = second & 0x7f;
    if (length === 126) {
      if (client.buffer.length < 4) return;
      length = client.buffer.readUInt16BE(2);
      offset = 4;
    } else if (length === 127) {
      if (client.buffer.length < 10) return;
      length = Number(client.buffer.readBigUInt64BE(2));
      offset = 10;
    }
    const masked = (second & 0x80) !== 0;
    const maskOffset = offset;
    if (masked) offset += 4;
    if (client.buffer.length < offset + length) return;
    let payload = client.buffer.subarray(offset, offset + length);
    if (masked) {
      const mask = client.buffer.subarray(maskOffset, maskOffset + 4);
      payload = Buffer.from(payload.map((byte, index) => byte ^ mask[index % 4]));
    }
    client.buffer = client.buffer.subarray(offset + length);
    if (opcode === 0x8) {
      client.socket.end();
      return;
    }
    if (opcode === 0x1) {
      handleMessage(client, payload.toString("utf8"));
    }
  }
}

function handleMessage(client, raw) {
  let message;
  try {
    message = JSON.parse(raw);
  } catch {
    send(client, { type: "error", message: "Некорректный JSON" });
    return;
  }
  if (message.type === "createRoom") {
    const room = createRoom(message.difficulty || "BEGINNER");
    joinRoom(client, room, 1);
    send(client, { type: "roomCreated", code: room.code, playerId: 1 });
    return;
  }
  if (message.type === "joinRoom") {
    const code = String(message.code || "");
    const room = rooms.get(code);
    if (!room) {
      send(client, { type: "error", message: "Комната с таким кодом не найдена" });
      return;
    }
    if (room.clients.length >= 2) {
      send(client, { type: "error", message: "Комната уже заполнена" });
      return;
    }
    joinRoom(client, room, 2);
    send(client, { type: "joined", code: room.code, playerId: 2 });
    broadcast(room, { type: "state", state: serializeRoom(room) });
    return;
  }
  if (message.type === "input" && client.room) {
    const player = client.room.players.find((item) => item.id === client.playerId);
    if (player) {
      player.nextDir = normalizeDirection(message.direction);
      client.room.updatedAt = Date.now();
    }
  }
}

function createRoom(difficulty) {
  let code;
  do {
    code = String(Math.floor(10000 + Math.random() * 90000));
  } while (rooms.has(code));
  const room = {
    code,
    difficulty,
    clients: [],
    grid: generateMap(),
    players: [
      car(1, 1.5, 1.5, "NONE"),
      car(2, WIDTH - 2.5, HEIGHT - 2.5, "NONE"),
    ],
    police: [car(100, WIDTH - 2.5, 1.5, "LEFT"), car(101, 1.5, HEIGHT - 2.5, "RIGHT")],
    artifacts: [],
    bankCell: null,
    bankSpawnAt: Date.now() + BANK_SPAWN_MS,
    bankExpiresAt: 0,
    bankRestorePending: false,
    banknoteEventId: 0,
    nextArtifactAt: Date.now() + ARTIFACT_INTERVAL_MS,
    nextPoliceId: 200,
    score: 0,
    total: 0,
    stage: 1,
    started: false,
    over: false,
    updatedAt: Date.now(),
  };
  placeDiamonds(room.grid);
  room.total = countWealth(room.grid);
  rooms.set(code, room);
  return room;
}

function joinRoom(client, room, playerId) {
  removeClient(client);
  client.room = room;
  client.playerId = playerId;
  room.clients.push(client);
  room.started = room.clients.length === 2;
  if (room.started && !room.startedAt) {
    room.startedAt = Date.now();
    room.bankSpawnAt = room.startedAt + BANK_SPAWN_MS;
    room.nextArtifactAt = room.startedAt + ARTIFACT_INTERVAL_MS;
  }
  room.updatedAt = Date.now();
}

function removeClient(client) {
  if (!client.room) return;
  const room = client.room;
  room.clients = room.clients.filter((item) => item !== client);
  broadcast(room, { type: "gameOver", message: "Второй игрок отключился" });
  client.room = null;
  client.playerId = 0;
  if (room.clients.length === 0) {
    rooms.delete(room.code);
  }
}

function updateRoom(room, delta, now) {
  if (!room.started || room.over) return;
  updateArtifacts(room, now);
  for (const player of room.players) {
    updatePlayer(room, player, delta);
    collect(room, player);
  }
  for (const police of room.police) {
    updatePolice(room, police, delta);
  }
  for (const player of room.players) {
    for (const police of room.police) {
      if (Math.hypot(player.x - police.x, player.y - police.y) < 0.55 && now > (player.invulnerableUntil || 0)) {
        player.damage += 1;
        player.invulnerableUntil = now + 1200;
      }
    }
  }
  if (room.players.some((player) => player.damage >= MAX_DAMAGE)) {
    room.over = true;
    broadcast(room, { type: "gameOver", message: "Поражение: полиция догнала преступников" });
  } else if (room.score >= room.total) {
    room.over = true;
    broadcast(room, { type: "gameOver", message: "Победа: все богатство собрано" });
  }
}

function updateArtifacts(room, now) {
  if (room.bankRestorePending && room.bankCell && !room.players.some((player) => sameCell(player, room.bankCell))) {
    restoreBankWall(room);
  }
  for (let i = room.artifacts.length - 1; i >= 0; i--) {
    const artifact = room.artifacts[i];
    if (now >= artifact.expiresAt) {
      if (artifact.type === "BANK") {
        if (room.bankCell && room.players.some((player) => sameCell(player, room.bankCell))) {
          room.bankRestorePending = true;
        } else {
          restoreBankWall(room);
        }
      }
      room.artifacts.splice(i, 1);
    }
  }
  if (!room.artifacts.some((item) => item.type === "MEDKIT") && room.players.some((player) => player.damage >= 3)) {
    spawnMedkit(room, now);
  }
  if (room.nextArtifactAt > 0 && now >= room.nextArtifactAt) {
    spawnGhost(room, now);
    room.nextArtifactAt = now + ARTIFACT_INTERVAL_MS;
  }
  if (!room.bankCell && room.bankSpawnAt > 0 && now >= room.bankSpawnAt) {
    spawnBank(room, now);
  }
}

function updatePlayer(room, player, delta) {
  if (player.nextDir === "NONE") {
    player.dir = "NONE";
    return;
  }
  if (canMove(room.grid, player, player.nextDir)) {
    player.dir = player.nextDir;
  }
  move(room.grid, player, PLAYER_SPEED, delta);
}

function updatePolice(room, police, delta) {
  const target = nearestPlayer(room, police);
  if (atCenter(police)) {
    police.x = Math.round(police.x - 0.5) + 0.5;
    police.y = Math.round(police.y - 0.5) + 0.5;
    const options = ["UP", "DOWN", "LEFT", "RIGHT"].filter((dir) => canMove(room.grid, police, dir));
    options.sort((a, b) => distanceAfter(police, a, target) - distanceAfter(police, b, target));
    police.dir = options[0] || police.dir;
  }
  move(room.grid, police, POLICE_SPEED, delta);
}

function move(grid, actor, speed, delta) {
  if (actor.dir === "NONE" || !canMove(grid, actor, actor.dir)) return;
  const d = vector(actor.dir);
  actor.x += d.dx * speed * delta;
  actor.y += d.dy * speed * delta;
  actor.angle = Math.atan2(d.dy, d.dx);
}

function canMove(grid, actor, dir) {
  const d = vector(dir);
  if (!d) return false;
  const cell = { x: Math.round(actor.x - 0.5), y: Math.round(actor.y - 0.5) };
  const x = cell.x + d.dx;
  const y = cell.y + d.dy;
  if (Date.now() < (actor.ghostUntil || 0)) {
    return x > 0 && y > 0 && x < WIDTH - 1 && y < HEIGHT - 1;
  }
  return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT && grid[y][x] !== "#";
}

function collect(room, player) {
  const x = Math.round(player.x - 0.5);
  const y = Math.round(player.y - 0.5);
  for (let i = room.artifacts.length - 1; i >= 0; i--) {
    const artifact = room.artifacts[i];
    if (artifact.x === x && artifact.y === y) {
      applyArtifact(room, player, artifact);
      room.artifacts.splice(i, 1);
    }
  }
  const item = room.grid[y] && room.grid[y][x];
  if (item === "o" || item === "d") {
    room.score += item === "d" ? 10 : 1;
    room.grid[y][x] = ".";
  }
}

function applyArtifact(room, player, artifact) {
  if (artifact.type === "MEDKIT") {
    player.damage = Math.max(0, player.damage - 1);
    return;
  }
  if (artifact.type === "BANK") {
    if (room.bankRestorePending || !room.bankCell) {
      return;
    }
    room.bankRestorePending = true;
    const reward = bankReward(room.difficulty);
    room.score += reward.wealth;
    room.total += reward.wealth;
    room.banknoteEventId += 1;
    broadcast(room, { type: "bankBonus", eventId: room.banknoteEventId, banknotes: reward.banknotes });
    addPolice(room, reward.police);
    room.bankExpiresAt = 0;
    return;
  }
  if (artifact.type === "GHOST") {
    player.ghostUntil = Date.now() + GHOST_MS;
  }
}

function spawnMedkit(room, now) {
  const target = room.players.reduce((best, player) => player.damage > best.damage ? player : best, room.players[0]);
  const candidates = artifactCandidates(room, target, false);
  if (candidates.length === 0) return;
  const cell = candidates[Math.floor(Math.random() * candidates.length)];
  room.artifacts.push({ type: "MEDKIT", x: cell.x, y: cell.y, expiresAt: now + MEDKIT_VISIBLE_MS });
}

function spawnBank(room, now) {
  const candidates = [];
  for (let y = 2; y < HEIGHT - 2; y++) {
    for (let x = 2; x < WIDTH - 2; x++) {
      if (room.grid[y][x] === "#" && hasOpenNeighbor(room.grid, x, y)) {
        candidates.push({ x, y });
      }
    }
  }
  if (candidates.length === 0) {
    room.bankSpawnAt = now + BANK_SPAWN_MS;
    return;
  }
  const cell = candidates[Math.floor(Math.random() * candidates.length)];
  room.bankCell = cell;
  room.grid[cell.y][cell.x] = ".";
  room.bankExpiresAt = now + BANK_VISIBLE_MS;
  room.bankSpawnAt = 0;
  room.artifacts.push({ type: "BANK", x: cell.x, y: cell.y, expiresAt: room.bankExpiresAt });
}

function spawnGhost(room, now) {
  const target = room.players[Math.floor(Math.random() * room.players.length)];
  const candidates = artifactCandidates(room, target, true);
  if (candidates.length === 0) return;
  const cell = candidates[Math.floor(Math.random() * candidates.length)];
  room.artifacts.push({ type: "GHOST", x: cell.x, y: cell.y, expiresAt: now + ARTIFACT_VISIBLE_MS });
}

function restoreBankWall(room) {
  if (room.bankCell) {
    room.grid[room.bankCell.y][room.bankCell.x] = "#";
  }
  room.bankCell = null;
  room.bankExpiresAt = 0;
  room.bankRestorePending = false;
}

function artifactCandidates(room, target, includeDiamonds) {
  const cell = { x: Math.round(target.x - 0.5), y: Math.round(target.y - 0.5) };
  const right = cell.x >= WIDTH / 2;
  const bottom = cell.y >= HEIGHT / 2;
  const minX = right ? Math.floor(WIDTH / 2) : 1;
  const maxX = right ? WIDTH - 2 : Math.floor(WIDTH / 2);
  const minY = bottom ? Math.floor(HEIGHT / 2) : 1;
  const maxY = bottom ? HEIGHT - 2 : Math.floor(HEIGHT / 2);
  const candidates = [];
  for (let y = minY; y <= maxY; y++) {
    for (let x = minX; x <= maxX; x++) {
      const item = room.grid[y][x];
      if ((item === "o" || (includeDiamonds && item === "d")) && Math.abs(x - cell.x) + Math.abs(y - cell.y) >= 2) {
        candidates.push({ x, y });
      }
    }
  }
  return candidates;
}

function hasOpenNeighbor(grid, x, y) {
  return ["UP", "DOWN", "LEFT", "RIGHT"].some((dir) => {
    const d = vector(dir);
    const nx = x + d.dx;
    const ny = y + d.dy;
    return nx >= 0 && ny >= 0 && nx < WIDTH && ny < HEIGHT && grid[ny][nx] !== "#";
  });
}

function sameCell(actor, cell) {
  return Math.round(actor.x - 0.5) === cell.x && Math.round(actor.y - 0.5) === cell.y;
}

function bankReward(difficulty) {
  if (difficulty === "AMATEUR") return { banknotes: 20, wealth: 100, police: 3 };
  if (difficulty === "PRO") return { banknotes: 20, wealth: 100, police: 4 };
  return { banknotes: 10, wealth: 50, police: 2 };
}

function addPolice(room, count) {
  const open = [];
  for (let y = 1; y < HEIGHT - 1; y++) {
    for (let x = 1; x < WIDTH - 1; x++) {
      if (room.grid[y][x] !== "#") {
        open.push({ x, y });
      }
    }
  }
  for (let i = 0; i < count && open.length > 0; i++) {
    const index = Math.floor(Math.random() * open.length);
    const cell = open.splice(index, 1)[0];
    room.police.push(car(room.nextPoliceId++, cell.x + 0.5, cell.y + 0.5, "LEFT"));
  }
}

function nearestPlayer(room, police) {
  return room.players.reduce((best, player) => {
    return Math.hypot(player.x - police.x, player.y - police.y) < Math.hypot(best.x - police.x, best.y - police.y) ? player : best;
  }, room.players[0]);
}

function distanceAfter(actor, dir, target) {
  const d = vector(dir);
  return Math.abs(actor.x + d.dx - target.x) + Math.abs(actor.y + d.dy - target.y);
}

function atCenter(actor) {
  return Math.abs(actor.x - (Math.round(actor.x - 0.5) + 0.5)) < 0.08
    && Math.abs(actor.y - (Math.round(actor.y - 0.5) + 0.5)) < 0.08;
}

function vector(dir) {
  return {
    UP: { dx: 0, dy: -1 },
    DOWN: { dx: 0, dy: 1 },
    LEFT: { dx: -1, dy: 0 },
    RIGHT: { dx: 1, dy: 0 },
  }[dir] || null;
}

function normalizeDirection(direction) {
  return ["UP", "DOWN", "LEFT", "RIGHT", "NONE"].includes(direction) ? direction : "NONE";
}

function generateMap() {
  const grid = Array.from({ length: HEIGHT }, (_, y) =>
    Array.from({ length: WIDTH }, (_, x) => (x === 0 || y === 0 || x === WIDTH - 1 || y === HEIGHT - 1 ? "#" : "o"))
  );
  for (let y = 2; y < HEIGHT - 2; y += 4) {
    for (let x = 2; x < WIDTH - 2; x += 4) {
      const w = 1 + Math.floor(Math.random() * 2);
      const h = 1 + Math.floor(Math.random() * 2);
      for (let yy = y; yy < Math.min(HEIGHT - 1, y + h); yy++) {
        for (let xx = x; xx < Math.min(WIDTH - 1, x + w); xx++) {
          grid[yy][xx] = "#";
        }
      }
    }
  }
  grid[1][1] = ".";
  grid[1][2] = ".";
  grid[2][1] = ".";
  grid[HEIGHT - 2][WIDTH - 2] = ".";
  grid[HEIGHT - 2][WIDTH - 3] = ".";
  grid[HEIGHT - 3][WIDTH - 2] = ".";
  return grid;
}

function placeDiamonds(grid) {
  let placed = 0;
  while (placed < 8) {
    const x = 1 + Math.floor(Math.random() * (WIDTH - 2));
    const y = 1 + Math.floor(Math.random() * (HEIGHT - 2));
    if (grid[y][x] === "o") {
      grid[y][x] = "d";
      placed++;
    }
  }
}

function countWealth(grid) {
  return grid.flat().reduce((sum, item) => sum + (item === "d" ? 10 : item === "o" ? 1 : 0), 0);
}

function car(id, x, y, dir) {
  return { id, x, y, dir, nextDir: "NONE", angle: 0, damage: 0, invulnerableUntil: 0, ghostUntil: 0 };
}

function serializeRoom(room) {
  return {
    width: WIDTH,
    height: HEIGHT,
    stage: room.stage,
    grid: room.grid.map((row) => row.join("")),
    players: room.players.map((player) => ({
      id: player.id,
      x: player.x,
      y: player.y,
      angle: player.angle,
      damage: player.damage,
      ghostActive: Date.now() < (player.ghostUntil || 0),
    })),
    police: room.police.map((car) => ({
      id: car.id,
      x: car.x,
      y: car.y,
      angle: car.angle,
    })),
    artifacts: room.artifacts.map((artifact) => ({
      type: artifact.type,
      x: artifact.x,
      y: artifact.y,
    })),
    banknoteEventId: room.banknoteEventId,
    banknoteReward: 0,
    score: room.score,
    total: room.total,
    damage: Math.max(...room.players.map((player) => player.damage)),
    statusText: room.started ? "Онлайн-игра" : "Ожидание второго игрока",
  };
}

function broadcast(room, message) {
  for (const client of room.clients) {
    send(client, message);
  }
}

function send(client, message) {
  if (!client.socket.writable) return;
  const payload = Buffer.from(JSON.stringify(message), "utf8");
  const header = [];
  header.push(0x81);
  if (payload.length <= 125) {
    header.push(payload.length);
  } else if (payload.length <= 65535) {
    header.push(126, (payload.length >> 8) & 255, payload.length & 255);
  } else {
    header.push(127, 0, 0, 0, 0, (payload.length >> 24) & 255, (payload.length >> 16) & 255, (payload.length >> 8) & 255, payload.length & 255);
  }
  client.socket.write(Buffer.concat([Buffer.from(header), payload]));
}
