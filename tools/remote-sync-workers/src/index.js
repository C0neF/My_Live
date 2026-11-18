/**
 * My Live Remote Sync - Cloudflare Worker + Durable Objects
 *
 * WebSocket 同步服务，用于多设备之间同步收藏、历史、弹幕屏蔽词、账号等数据。
 *
 * 部署: npm run deploy (或 npx wrangler deploy)
 * 本地调试: npm run dev (或 npx wrangler dev)
 *
 * 协议:
 *   - 创建房间: WS /sync/create
 *   - 加入房间: WS /sync/{roomId}
 *   - 健康检查: GET /status
 */

const MAX_MESSAGE_BYTES = 8 * 1024 * 1024;
const ROOM_TTL_MS = 600_000;
const MAX_ROOM_CONNECTIONS = 8;
const MAX_CLIENT_INFO_LENGTH = 64;
const RATE_LIMIT_WINDOW_MS = 10_000;
const RATE_LIMIT_MAX_MESSAGES = 30;
const ROOM_ID_PATTERN = /^[A-Z0-9]{6}$/;
const BASE32_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function generateRoomId() {
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  let roomId = "";
  for (let i = 0; i < bytes.length; i++) {
    roomId += BASE32_ALPHABET[bytes[i] & 31];
  }
  return roomId;
}

export class SyncRoom {
  constructor(state, env) {
    this.state = state;
    this.clients = new Map();
    this.rateWindows = new WeakMap();
    this.timer = null;
    this.roomTimeoutAt = 0;
  }

  useHibernation() {
    return (
      typeof this.state?.acceptWebSocket === "function" &&
      typeof this.state?.getWebSockets === "function"
    );
  }

  getClientRecords() {
    if (this.useHibernation()) {
      const records = [];
      for (const ws of this.state.getWebSockets()) {
        const data = typeof ws.deserializeAttachment === "function"
          ? ws.deserializeAttachment()
          : this.clients.get(ws);
        if (data) {
          records.push([ws, data]);
        }
      }
      return records;
    }
    return [...this.clients.entries()];
  }

  saveClientRecord(ws, clientData) {
    if (this.useHibernation() && typeof ws.serializeAttachment === "function") {
      ws.serializeAttachment(clientData);
    } else {
      this.clients.set(ws, clientData);
    }
  }

  deleteClientRecord(ws) {
    this.clients.delete(ws);
  }

  findCreatorRecord() {
    for (const [ws, client] of this.getClientRecords()) {
      if (client.isCreator) {
        return [ws, client];
      }
    }
    return null;
  }

  async fetch(request) {
    const upgradeHeader = request.headers.get("Upgrade");
    if (!upgradeHeader || upgradeHeader.toLowerCase() !== "websocket") {
      return json(426, { status: false, message: "websocket upgrade required" });
    }

    const [client, server] = Object.values(new WebSocketPair());
    this.handleConnection(server);

    return new Response(null, { status: 101, webSocket: client });
  }

  handleConnection(ws) {
    const clientData = {
      connectionId: `c_${crypto.randomUUID().replace(/-/g, "").slice(0, 16)}`,
      isCreator: false,
      info: {},
    };

    if (this.useHibernation()) {
      this.state.acceptWebSocket(ws);
      this.saveClientRecord(ws, clientData);
      return;
    }

    ws.accept();
    const record = { ws, ...clientData };
    this.saveClientRecord(ws, record);

    ws.addEventListener("message", (event) => {
      this.onIncomingData(ws, record, event.data);
    });

    ws.addEventListener("close", () => {
      this.removeClient(ws, record);
    });

    ws.addEventListener("error", () => {
      this.removeClient(ws, record);
    });
  }

  webSocketMessage(ws, data) {
    const client = typeof ws.deserializeAttachment === "function"
      ? ws.deserializeAttachment()
      : this.clients.get(ws);
    if (!client) return;
    this.onIncomingData(ws, client, data);
  }

  webSocketClose(ws) {
    const client = typeof ws.deserializeAttachment === "function"
      ? ws.deserializeAttachment()
      : this.clients.get(ws);
    if (client) {
      this.removeClient(ws, client);
    }
  }

  webSocketError(ws) {
    this.webSocketClose(ws);
  }

  onIncomingData(ws, clientData, data) {
    try {
      const byteLength = typeof data === "string"
        ? new TextEncoder().encode(data).byteLength
        : data.byteLength;
      if (byteLength > MAX_MESSAGE_BYTES) {
        this.sendError(ws, "", "payload_too_large", "同步内容超过大小限制");
        return;
      }
      if (!this.allowMessage(ws)) {
        this.sendError(ws, "", "rate_limited", "请求过于频繁，请稍后再试");
        return;
      }
      const raw = typeof data === "string"
        ? data
        : new TextDecoder().decode(data);
      const message = JSON.parse(raw);
      this.handleMessage(ws, clientData, message);
    } catch {
      this.sendError(ws, "", "invalid_json", "Invalid JSON");
    }
  }

  allowMessage(ws) {
    const now = Date.now();
    const history = (this.rateWindows.get(ws) || []).filter(
      (ts) => now - ts < RATE_LIMIT_WINDOW_MS
    );
    if (history.length >= RATE_LIMIT_MAX_MESSAGES) {
      this.rateWindows.set(ws, history);
      return false;
    }
    history.push(now);
    this.rateWindows.set(ws, history);
    return true;
  }

  handleMessage(ws, client, message) {
    const type = String(message.type || "");
    const requestId = String(message.requestId || "");

    switch (type) {
      case "createRoom":
        this.createRoom(ws, client, requestId, message.payload);
        break;
      case "joinRoom":
        this.joinRoom(ws, client, requestId, message.payload);
        break;
      case "sendFavorite":
      case "sendHistory":
      case "sendShieldWord":
      case "sendBiliAccount":
      case "sendDouyinAccount":
        this.forwardContent(ws, client, requestId, type, message.payload);
        break;
      case "ping":
        this.send(ws, { type: "pong", requestId });
        break;
      default:
        this.sendError(ws, requestId, "unknown_type", "Unknown request type");
    }
  }

  createRoom(ws, client, requestId, payload) {
    const existingCreator = this.findCreatorRecord();
    if (existingCreator && existingCreator[0] !== ws) {
      this.sendError(ws, requestId, "room_exists", "同步房间已存在，请重试");
      return;
    }

    const updated = {
      ...client,
      isCreator: true,
      info: this.normalizeClientInfo(payload),
    };
    if (!this.useHibernation()) {
      updated.ws = ws;
    }
    this.saveClientRecord(ws, updated);
    this.scheduleRoomTimeout();

    this.send(ws, {
      type: "roomCreated",
      requestId,
      roomId: this.state.id?.name || "ROOM",
    });
    this.broadcastUsers();
  }

  joinRoom(ws, client, requestId, payload) {
    const creator = this.findCreatorRecord();
    if (!creator) {
      this.sendError(ws, requestId, "room_not_found", "未找到同步房间");
      return;
    }

    const records = this.getClientRecords();
    const alreadyInRoom = records.some(([existingWs]) => existingWs === ws);
    if (records.length >= MAX_ROOM_CONNECTIONS && !alreadyInRoom) {
      this.sendError(ws, requestId, "room_full", "房间人数已满");
      return;
    }

    const updated = {
      ...client,
      isCreator: false,
      info: this.normalizeClientInfo(payload),
    };
    if (!this.useHibernation()) {
      updated.ws = ws;
    }
    this.saveClientRecord(ws, updated);

    this.send(ws, { type: "roomJoined", requestId });
    this.broadcastUsers();
  }

  forwardContent(ws, client, requestId, type, payload) {
    const creator = this.findCreatorRecord();
    const records = this.getClientRecords();
    const senderPresent = records.some(([existingWs]) => existingWs === ws);
    if (!creator || !senderPresent) {
      this.sendError(ws, requestId, "room_not_found", "未找到同步房间");
      return;
    }

    const eventType = {
      sendFavorite: "favoriteReceived",
      sendHistory: "historyReceived",
      sendShieldWord: "shieldWordReceived",
      sendBiliAccount: "biliAccountReceived",
      sendDouyinAccount: "douyinAccountReceived",
    }[type];

    const msg = JSON.stringify({ type: eventType, payload: payload || {} });

    for (const [targetWs] of records) {
      if (targetWs !== ws) {
        try {
          targetWs.send(msg);
        } catch {
          this.deleteClientRecord(targetWs);
        }
      }
    }

    this.send(ws, { type: "ack", requestId });
  }

  removeClient(ws, client, notify = true) {
    const currentRecord = this.getClientRecords().find(([existingWs]) => existingWs === ws)?.[1] || client;
    const wasCreator = Boolean(currentRecord?.isCreator);
    this.deleteClientRecord(ws);

    if (wasCreator) {
      for (const [peerWs] of this.getClientRecords()) {
        if (peerWs === ws) continue;
        try {
          peerWs.send(JSON.stringify({ type: "roomDestroyed", reason: "creator left" }));
        } catch {}
        try {
          peerWs.close(1000, "creator left");
        } catch {}
        this.deleteClientRecord(peerWs);
      }
      this.clearRoomTimeout();
      return;
    }

    const remaining = this.getClientRecords();
    if (notify && remaining.length > 0) {
      this.broadcastUsers();
    }

    if (remaining.length === 0) {
      this.clearRoomTimeout();
    }
  }

  broadcastUsers() {
    const records = this.getClientRecords();
    const users = records.map(([, client]) => ({
      connectionId: client.connectionId,
      shortId: client.connectionId.slice(-4).toUpperCase(),
      platform: client.info.platform,
      version: client.info.version,
      app: client.info.app,
      isCreator: Boolean(client.isCreator),
    }));

    for (const [ws, client] of records) {
      try {
        ws.send(JSON.stringify({
          type: "userUpdated",
          users: users.map((u) => ({ ...u, isSelf: u.connectionId === client.connectionId })),
        }));
      } catch {
        this.deleteClientRecord(ws);
      }
    }
  }

  scheduleRoomTimeout() {
    this.clearRoomTimeout();
    this.roomTimeoutAt = Date.now() + ROOM_TTL_MS;
    if (typeof this.state?.storage?.setAlarm === "function") {
      void this.state.storage.setAlarm(this.roomTimeoutAt);
      return;
    }
    this.timer = setTimeout(() => {
      this.destroyRoomOnTimeout();
    }, ROOM_TTL_MS);
  }

  clearRoomTimeout() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (typeof this.state?.storage?.deleteAlarm === "function") {
      void this.state.storage.deleteAlarm();
    }
    this.roomTimeoutAt = 0;
  }

  async alarm() {
    this.destroyRoomOnTimeout();
  }

  destroyRoomOnTimeout() {
    this.timer = null;
    this.roomTimeoutAt = 0;
    const records = this.getClientRecords();
    this.clients.clear();
    for (const [ws, record] of records) {
      if (this.useHibernation() && typeof ws.serializeAttachment === "function") {
        ws.serializeAttachment({ ...record, isCreator: false });
      }
      try {
        ws.send(JSON.stringify({ type: "roomDestroyed", reason: "timeout" }));
      } catch {}
    }
    for (const [ws] of records) {
      try {
        ws.close(1000, "room timeout");
      } catch {}
    }
  }

  send(ws, message) {
    try {
      ws.send(JSON.stringify(message));
    } catch {
      this.deleteClientRecord(ws);
    }
  }

  sendError(ws, requestId, code, message) {
    this.send(ws, { type: "error", requestId, error: { code, message } });
  }

  normalizeClientInfo(payload) {
    const info = typeof payload === "object" && payload !== null ? payload : {};
    return {
      app: String(info.app || "My Live").slice(0, MAX_CLIENT_INFO_LENGTH),
      platform: String(info.platform || "unknown").slice(0, MAX_CLIENT_INFO_LENGTH),
      version: String(info.version || "").slice(0, MAX_CLIENT_INFO_LENGTH),
    };
  }
}

// Worker 入口：URL 路由到不同 Durable Object
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 健康检查
    if (url.pathname === "/" || url.pathname === "/status") {
      return json(200, { status: true, app: "My Live Remote Sync" });
    }

    // /sync/create → 创建房间（DO 名称随机生成）
    // /sync/{roomId} → 加入房间（DO 名称 = roomId）
    if (url.pathname.startsWith("/sync/")) {
      const pathParts = url.pathname.split("/").filter(Boolean);
      if (pathParts.length !== 2) {
        return json(404, { status: false, message: "not found" });
      }
      const action = pathParts[1]; // "create" 或房间 ID

      let roomId;
      if (action === "create") {
        roomId = generateRoomId();
      } else {
        roomId = action.toUpperCase();
        if (!ROOM_ID_PATTERN.test(roomId)) {
          return json(400, { status: false, message: "invalid room id" });
        }
      }

      const doId = env.SYNC_ROOM.idFromName(roomId);
      const stub = env.SYNC_ROOM.get(doId);
      return stub.fetch(request);
    }

    return json(404, { status: false, message: "not found" });
  },
};

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
