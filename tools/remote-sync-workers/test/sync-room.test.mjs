import test from "node:test";
import assert from "node:assert/strict";
import worker, { SyncRoom, generateRoomId } from "../src/index.js";

function createFakeSocket() {
  const listeners = new Map();
  const sent = [];
  let closed = false;
  return {
    sent,
    get closed() {
      return closed;
    },
    accept() {},
    addEventListener(type, fn) {
      const list = listeners.get(type) || [];
      list.push(fn);
      listeners.set(type, list);
    },
    send(data) {
      if (closed) throw new Error("closed");
      sent.push(JSON.parse(data));
    },
    close() {
      closed = true;
      for (const fn of listeners.get("close") || []) fn();
    },
    emitMessage(obj) {
      const data = typeof obj === "string" ? obj : JSON.stringify(obj);
      for (const fn of listeners.get("message") || []) fn({ data });
    },
  };
}

test("generateRoomId emits 6 unambiguous Base32 characters", () => {
  for (let i = 0; i < 50; i++) {
    const id = generateRoomId();
    assert.match(id, /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$/);
  }
});

test("SyncRoom rejects joinRoom when no creator exists (phantom room guard)", () => {
  const room = new SyncRoom({ id: { name: "GHOST1" } }, {});
  const joiner = createFakeSocket();
  try {
    room.handleConnection(joiner);
    joiner.emitMessage({ type: "joinRoom", requestId: "r1", payload: { platform: "android" } });

    assert.equal(joiner.sent.length, 1);
    assert.equal(joiner.sent[0].type, "error");
    assert.equal(joiner.sent[0].error.code, "room_not_found");
  } finally {
    joiner.close();
  }
});

test("SyncRoom creates room, clamps clientInfo, prevents overwrite, allows join and forwards E2EE payload", () => {
  const room = new SyncRoom({ id: { name: "ABC123" } }, {});
  const creator = createFakeSocket();
  const joiner = createFakeSocket();

  try {
    room.handleConnection(creator);
    creator.emitMessage({
      type: "createRoom",
      requestId: "c1",
      payload: { platform: "a".repeat(128), version: "v".repeat(90), app: "m".repeat(80) },
    });
    assert.equal(creator.sent[0].type, "roomCreated");
    assert.equal(creator.sent[0].roomId, "ABC123");
    assert.equal(creator.sent[1].users[0].platform.length, 64);
    assert.equal(creator.sent[1].users[0].version.length, 64);
    assert.equal(creator.sent[1].users[0].app.length, 64);

    // Second client attempting createRoom on an occupied room is rejected.
    room.handleConnection(joiner);
    joiner.emitMessage({ type: "createRoom", requestId: "c2", payload: { platform: "windows" } });
    assert.equal(joiner.sent[0].type, "error");
    assert.equal(joiner.sent[0].error.code, "room_exists");

    // Joining succeeds and forwards encrypted envelope opaquely.
    joiner.emitMessage({ type: "joinRoom", requestId: "j1", payload: { platform: "windows" } });
    assert.ok(joiner.sent.some((m) => m.type === "roomJoined" && m.requestId === "j1"));

    creator.emitMessage({
      type: "sendFavorite",
      requestId: "s1",
      payload: { envelope: "RMSYNC1.iv.cipher" },
    });
    const received = joiner.sent.find((m) => m.type === "favoriteReceived");
    assert.deepEqual(received?.payload, { envelope: "RMSYNC1.iv.cipher" });
  } finally {
    creator.close();
    joiner.close();
  }
});

test("SyncRoom enforces per-client rate limiting", () => {
  const room = new SyncRoom({ id: { name: "RATE01" } }, {});
  const creator = createFakeSocket();
  try {
    room.handleConnection(creator);
    for (let i = 0; i < 31; i++) {
      creator.emitMessage({ type: "ping", requestId: `p${i}` });
    }
    const last = creator.sent.at(-1);
    assert.equal(last.type, "error");
    assert.equal(last.error.code, "rate_limited");
  } finally {
    creator.close();
  }
});

test("SyncRoom notifies connected peers with roomDestroyed on timeout", async () => {
  let scheduledAlarm = 0;
  const state = {
    id: { name: "TIME01" },
    storage: {
      async setAlarm(ts) {
        scheduledAlarm = ts;
      },
      async deleteAlarm() {
        scheduledAlarm = 0;
      },
    },
  };
  const room = new SyncRoom(state, {});
  const creator = createFakeSocket();
  const joiner = createFakeSocket();

  room.handleConnection(creator);
  creator.emitMessage({ type: "createRoom", requestId: "c1", payload: { platform: "android" } });
  room.handleConnection(joiner);
  joiner.emitMessage({ type: "joinRoom", requestId: "j1", payload: { platform: "windows" } });

  assert.ok(scheduledAlarm > Date.now());
  await room.alarm();

  assert.ok(creator.sent.some((m) => m.type === "roomDestroyed" && m.reason === "timeout"));
  assert.ok(joiner.sent.some((m) => m.type === "roomDestroyed" && m.reason === "timeout"));
  assert.equal(creator.closed, true);
  assert.equal(joiner.closed, true);
});

test("Worker entrypoint rejects malformed room IDs before allocating Durable Objects", async () => {
  let doLookups = 0;
  const env = {
    SYNC_ROOM: {
      idFromName() {
        doLookups++;
        return {};
      },
      get() {
        return { fetch: async () => new Response("ok") };
      },
    },
  };
  const badRes = await worker.fetch(new Request("https://sync.example.com/sync/not-valid-room-id"), env);
  assert.equal(badRes.status, 400);
  assert.equal(doLookups, 0);
});
