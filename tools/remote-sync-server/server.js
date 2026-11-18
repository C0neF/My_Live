import http from "node:http";
import { randomBytes } from "node:crypto";
import { WebSocket, WebSocketServer } from "ws";

const PORT = Number(process.env.PORT || 51999);
const ROOM_TTL_MS = 600_000;
const MAX_ROOM_CONNECTIONS = 8;
const MAX_ACTIVE_ROOMS = 4096;
const MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
const MAX_CLIENT_INFO_LENGTH = 64;
const RATE_LIMIT_WINDOW_MS = 10_000;
const RATE_LIMIT_MAX_MESSAGES = 30;
const ROOM_ID_PATTERN = /^[A-Z0-9]{6}$/;
const BASE32_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const rooms = new Map();
const clients = new Map();

function parseSyncRoute(rawUrl) {
  let pathname = "/";
  try {
    pathname = new URL(rawUrl || "/", "http://127.0.0.1").pathname;
  } catch {
    return null;
  }
  if (pathname === "/sync" || pathname === "/sync/create") {
    return { mode: "create_or_legacy", urlRoomId: "" };
  }
  const parts = pathname.split("/").filter(Boolean);
  if (parts.length === 2 && parts[0] === "sync") {
    const candidate = parts[1].toUpperCase();
    if (ROOM_ID_PATTERN.test(candidate)) {
      return { mode: "join", urlRoomId: candidate };
    }
  }
  return null;
}

const server = http.createServer((req, res) => {
  if (req.url === "/" || req.url === "/status") {
    writeJson(res, 200, {
      status: true,
      app: "My Live Remote Sync",
    });
    return;
  }

  if (parseSyncRoute(req.url)) {
    writeJson(res, 426, {
      status: false,
      message: "websocket upgrade required",
    });
    return;
  }

  writeJson(res, 404, {
    status: false,
    message: "not found",
  });
});

const wss = new WebSocketServer({
  noServer: true,
  maxPayload: MAX_PAYLOAD_BYTES,
});

server.on("upgrade", (req, socket, head) => {
  const route = parseSyncRoute(req.url);
  if (!route) {
    socket.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit("connection", ws, route);
  });
});

wss.on("connection", (ws, route = { urlRoomId: "" }) => {
  const client = {
    ws,
    connectionId: `c_${randomBytes(8).toString("hex")}`,
    roomId: "",
    urlRoomId: route.urlRoomId || "",
    isCreator: false,
    info: {},
    rateWindow: [],
  };
  clients.set(ws, client);

  ws.on("message", (raw) => {
    handleMessage(client, raw.toString());
  });

  ws.on("close", () => {
    removeClient(client, "creator disconnected");
  });

  ws.on("error", () => {
    removeClient(client, "connection error");
  });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Remote sync server listening on ws://127.0.0.1:${PORT}/sync`);
});

function allowMessage(client) {
  const now = Date.now();
  client.rateWindow = client.rateWindow.filter((ts) => now - ts < RATE_LIMIT_WINDOW_MS);
  if (client.rateWindow.length >= RATE_LIMIT_MAX_MESSAGES) {
    return false;
  }
  client.rateWindow.push(now);
  return true;
}

function handleMessage(client, raw) {
  if (!allowMessage(client)) {
    sendError(client, "", "rate_limited", "请求过于频繁，请稍后再试");
    return;
  }

  let message;
  try {
    message = JSON.parse(raw);
  } catch {
    sendError(client, "", "invalid_json", "Invalid JSON");
    return;
  }

  const type = String(message.type || "");
  const requestId = String(message.requestId || "");

  switch (type) {
    case "createRoom":
      createRoom(client, requestId, message.payload);
      break;
    case "joinRoom":
      joinRoom(client, requestId, message.roomId, message.payload);
      break;
    case "sendFavorite":
    case "sendHistory":
    case "sendShieldWord":
    case "sendBiliAccount":
    case "sendDouyinAccount":
      forwardContent(client, requestId, type, message.roomId, message.payload);
      break;
    case "ping":
      send(client, {
        type: "pong",
        requestId,
      });
      break;
    default:
      sendError(client, requestId, "unknown_type", "Unknown request type");
      break;
  }
}

function createRoom(client, requestId, payload) {
  removeClient(client, "switch room", false, false);

  if (rooms.size >= MAX_ACTIVE_ROOMS) {
    sendError(client, requestId, "server_busy", "服务器繁忙，请稍后重试");
    return;
  }

  const roomId = generateRoomId();
  const timer = setTimeout(() => {
    destroyRoom(roomId, "timeout");
  }, ROOM_TTL_MS);
  const room = {
    id: roomId,
    creator: client,
    clients: new Set([client]),
    timer,
  };
  rooms.set(roomId, room);

  client.roomId = roomId;
  client.isCreator = true;
  client.info = normalizeClientInfo(payload);

  send(client, {
    type: "roomCreated",
    requestId,
    roomId,
  });
  broadcastUsers(room);
}

function joinRoom(client, requestId, rawRoomId, payload) {
  const roomId = String(rawRoomId || client.urlRoomId || "").trim().toUpperCase();
  const room = rooms.get(roomId);
  if (!room || !room.creator || !room.clients.has(room.creator)) {
    sendError(client, requestId, "room_not_found", "未找到同步房间");
    return;
  }
  if (room.clients.size >= MAX_ROOM_CONNECTIONS) {
    sendError(client, requestId, "room_full", "房间人数已满");
    return;
  }

  removeClient(client, "switch room", false, false);
  client.roomId = roomId;
  client.isCreator = false;
  client.info = normalizeClientInfo(payload);
  room.clients.add(client);

  send(client, {
    type: "roomJoined",
    requestId,
  });
  broadcastUsers(room);
}

function forwardContent(client, requestId, type, rawRoomId, payload) {
  const roomId = String(rawRoomId || client.roomId || "").trim().toUpperCase();
  const room = rooms.get(roomId);
  if (!room || !room.clients.has(client)) {
    sendError(client, requestId, "room_not_found", "未找到直播间");
    return;
  }

  const eventType = {
    sendFavorite: "favoriteReceived",
    sendHistory: "historyReceived",
    sendShieldWord: "shieldWordReceived",
    sendBiliAccount: "biliAccountReceived",
    sendDouyinAccount: "douyinAccountReceived",
  }[type];

  for (const target of room.clients) {
    if (target !== client) {
      send(target, {
        type: eventType,
        payload: payload || {},
      });
    }
  }

  send(client, {
    type: "ack",
    requestId,
  });
}

function removeClient(client, reason, notify = true, forgetClient = true) {
  if (!clients.has(client.ws) && !client.roomId) {
    return;
  }

  if (forgetClient) {
    clients.delete(client.ws);
  }
  const roomId = client.roomId;
  const wasCreator = client.isCreator;
  client.roomId = "";
  client.isCreator = false;

  if (!roomId) {
    return;
  }

  const room = rooms.get(roomId);
  if (!room) {
    return;
  }

  if (wasCreator) {
    destroyRoom(roomId, reason);
    return;
  }

  room.clients.delete(client);
  if (room.clients.size === 0) {
    clearTimeout(room.timer);
    rooms.delete(roomId);
  } else if (notify) {
    broadcastUsers(room);
  }
}

function destroyRoom(roomId, reason) {
  const room = rooms.get(roomId);
  if (!room) {
    return;
  }

  clearTimeout(room.timer);
  rooms.delete(roomId);

  for (const client of room.clients) {
    if (client.roomId === roomId) {
      client.roomId = "";
      client.isCreator = false;
    }
    if (client !== room.creator) {
      send(client, {
        type: "roomDestroyed",
        reason,
      });
    }
  }
}

function broadcastUsers(room) {
  const clientsInRoom = [...room.clients];
  for (const client of clientsInRoom) {
    send(client, {
      type: "userUpdated",
      users: clientsInRoom.map((user) => ({
        connectionId: user.connectionId,
        shortId: user.connectionId.slice(-4).toUpperCase(),
        platform: user.info.platform,
        version: user.info.version,
        app: user.info.app,
        isCreator: user.isCreator,
        isSelf: user === client,
      })),
    });
  }
}

function normalizeClientInfo(payload) {
  const info = typeof payload === "object" && payload !== null ? payload : {};
  return {
    app: String(info.app || "My Live").slice(0, MAX_CLIENT_INFO_LENGTH),
    platform: String(info.platform || "unknown").slice(0, MAX_CLIENT_INFO_LENGTH),
    version: String(info.version || "").slice(0, MAX_CLIENT_INFO_LENGTH),
  };
}

function generateRoomId() {
  let roomId = "";
  do {
    const bytes = randomBytes(6);
    roomId = "";
    for (let i = 0; i < bytes.length; i++) {
      roomId += BASE32_ALPHABET[bytes[i] & 31];
    }
  } while (rooms.has(roomId));
  return roomId;
}

function sendError(client, requestId, code, message) {
  send(client, {
    type: "error",
    requestId,
    error: {
      code,
      message,
    },
  });
}

function send(client, message) {
  if (client.ws.readyState === WebSocket.OPEN) {
    client.ws.send(JSON.stringify(message));
  }
}

function writeJson(res, status, body) {
  const content = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(content),
  });
  res.end(content);
}
