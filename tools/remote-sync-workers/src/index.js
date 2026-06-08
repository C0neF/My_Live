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

export class SyncRoom {
  constructor(state, env) {
    this.state = state;
    this.clients = new Map();
    this.creatorWs = null;
    this.timer = null;
  }

  async fetch(request) {
    const url = new URL(request.url);

    if (url.pathname === "/status") {
      return json(200, {
        status: true,
        clients: this.clients.size,
        roomId: this.state.id?.name || "unknown",
      });
    }

    const upgradeHeader = request.headers.get("Upgrade");
    if (!upgradeHeader || upgradeHeader !== "websocket") {
      return json(426, { status: false, message: "websocket upgrade required" });
    }

    const [client, server] = Object.values(new WebSocketPair());
    this.handleConnection(server);

    return new Response(null, { status: 101, webSocket: client });
  }

  handleConnection(ws) {
    ws.accept();

    const clientData = {
      ws,
      connectionId: `c_${crypto.randomUUID().replace(/-/g, "").slice(0, 16)}`,
      isCreator: false,
      info: {},
    };
    this.clients.set(ws, clientData);

    ws.addEventListener("message", (event) => {
      try {
        const message = JSON.parse(event.data);
        this.handleMessage(clientData, message);
      } catch {
        this.sendError(clientData, "", "invalid_json", "Invalid JSON");
      }
    });

    ws.addEventListener("close", () => {
      this.removeClient(clientData);
    });

    ws.addEventListener("error", () => {
      this.removeClient(clientData);
    });

    this.resetTimer();
  }

  handleMessage(client, message) {
    const type = String(message.type || "");
    const requestId = String(message.requestId || "");

    switch (type) {
      case "createRoom":
        this.createRoom(client, requestId, message.payload);
        break;
      case "joinRoom":
        this.joinRoom(client, requestId, message.payload);
        break;
      case "sendFavorite":
      case "sendHistory":
      case "sendShieldWord":
      case "sendBiliAccount":
      case "sendDouyinAccount":
        this.forwardContent(client, requestId, type, message.payload);
        break;
      case "ping":
        this.send(client, { type: "pong", requestId });
        break;
      default:
        this.sendError(client, requestId, "unknown_type", "Unknown request type");
    }
  }

  createRoom(client, requestId, payload) {
    this.removeClient(client, false);

    client.isCreator = true;
    client.info = this.normalizeClientInfo(payload);
    this.creatorWs = client.ws;
    this.clients.set(client.ws, client);

    this.send(client, {
      type: "roomCreated",
      requestId,
      roomId: this.state.id?.name || "ROOM",
    });
    this.broadcastUsers();
  }

  joinRoom(client, requestId, payload) {
    if (this.clients.size >= 8) {
      this.sendError(client, requestId, "room_full", "房间人数已满");
      return;
    }

    this.removeClient(client, false);

    client.isCreator = false;
    client.info = this.normalizeClientInfo(payload);
    this.clients.set(client.ws, client);

    this.send(client, { type: "roomJoined", requestId });
    this.broadcastUsers();
  }

  forwardContent(client, requestId, type, payload) {
    const eventType = {
      sendFavorite: "favoriteReceived",
      sendHistory: "historyReceived",
      sendShieldWord: "shieldWordReceived",
      sendBiliAccount: "biliAccountReceived",
      sendDouyinAccount: "douyinAccountReceived",
    }[type];

    const msg = JSON.stringify({ type: eventType, payload: payload || {} });

    for (const [ws, target] of this.clients) {
      if (target !== client) {
        try {
          ws.send(msg);
        } catch {
          this.clients.delete(ws);
        }
      }
    }

    this.send(client, { type: "ack", requestId });
  }

  removeClient(client, notify = true) {
    const wasCreator = client.isCreator;
    this.clients.delete(client.ws);

    if (wasCreator) {
      this.creatorWs = null;
      for (const [ws] of this.clients) {
        try {
          ws.send(JSON.stringify({ type: "roomDestroyed", reason: "creator left" }));
        } catch {
          this.clients.delete(ws);
        }
      }
      this.clients.clear();
      return;
    }

    if (notify && this.clients.size > 0) {
      this.broadcastUsers();
    }

    if (this.clients.size === 0) {
      this.resetTimer();
    }
  }

  broadcastUsers() {
    const users = [];
    for (const [, client] of this.clients) {
      users.push({
        connectionId: client.connectionId,
        shortId: client.connectionId.slice(-4).toUpperCase(),
        platform: client.info.platform,
        version: client.info.version,
        app: client.info.app,
        isCreator: client.isCreator,
      });
    }

    for (const [ws, client] of this.clients) {
      try {
        ws.send(JSON.stringify({
          type: "userUpdated",
          users: users.map((u) => ({ ...u, isSelf: u.connectionId === client.connectionId })),
        }));
      } catch {
        this.clients.delete(ws);
      }
    }
  }

  resetTimer() {
    if (this.timer) clearTimeout(this.timer);
    if (this.clients.size === 0) {
      this.timer = setTimeout(() => {}, 60_000);
    } else {
      this.timer = setTimeout(() => {
        for (const [ws] of this.clients) {
          try { ws.close(1000, "room timeout"); } catch {}
        }
        this.clients.clear();
      }, 600_000);
    }
  }

  send(client, message) {
    try {
      client.ws.send(JSON.stringify(message));
    } catch {
      this.clients.delete(client.ws);
    }
  }

  sendError(client, requestId, code, message) {
    this.send(client, { type: "error", requestId, error: { code, message } });
  }

  normalizeClientInfo(payload) {
    const info = typeof payload === "object" && payload !== null ? payload : {};
    return {
      app: String(info.app || "My Live"),
      platform: String(info.platform || "unknown"),
      version: String(info.version || ""),
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
      const action = pathParts[1]; // "create" 或房间 ID

      let roomId;
      if (action === "create") {
        // 生成随机房间 ID
        roomId = crypto.randomUUID().replace(/-/g, "").slice(0, 6).toUpperCase();
      } else {
        // 使用 URL 中的房间 ID
        roomId = action.toUpperCase();
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
