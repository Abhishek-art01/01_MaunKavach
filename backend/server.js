/**
 * MaunKavach backend (production) — "dumb encrypted courier" only.
 *
 * - Real Postgres (Supabase) for message rows.
 * - Real Supabase Storage for encrypted file blobs.
 * - Custom auth (bcrypt + JWT sessions) — not Supabase Auth, per your choice.
 * - `ws` library for realtime delivery (client connects, authenticates, gets pushed
 *   encrypted messages addressed to their user id).
 *
 * This server still NEVER decrypts anything and holds no Vault Key material — that
 * guarantee doesn't change just because the storage backend did. Every column/field it
 * touches is ciphertext or a non-secret identifier (sender_id, receiver_id, timestamp).
 */
require("dotenv").config();
const http = require("http");
const { WebSocketServer } = require("ws");
const { URL } = require("url");

const { pool } = require("./db");
const auth = require("./auth");
const storage = require("./storage");

const PORT = process.env.PORT || 8080;
const MAX_PAYLOAD_BYTES = 50 * 1024 * 1024; // 50MB ceiling on a single JSON message body (spec section 21: reject oversized payloads)

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(body) });
  res.end(body);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => {
      try {
        resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString("utf8")) : {});
      } catch (e) {
        reject(auth.httpError(400, "invalid JSON body"));
      }
    });
    req.on("error", reject);
  });
}

function readRawBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

// userId -> Set of live sockets (a user might have multiple devices/tabs open)
const liveConnections = new Map();

function pushToUser(userId, payload) {
  const sockets = liveConnections.get(userId);
  if (!sockets) return;
  const msg = JSON.stringify(payload);
  for (const ws of sockets) {
    if (ws.readyState === ws.OPEN) ws.send(msg);
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  // Basic CORS for any future web/admin client; the Android app doesn't need this.
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  if (req.method === "OPTIONS") { res.writeHead(204); return res.end(); }

  try {
    // ---- Auth ----
    if (req.method === "POST" && url.pathname === "/auth/register") {
      const { username, password } = await readJsonBody(req);
      const user = await auth.register(username, password);
      return sendJson(res, 201, user);
    }

    if (req.method === "POST" && url.pathname === "/auth/login") {
      const { username, password } = await readJsonBody(req);
      const session = await auth.login(username, password);
      return sendJson(res, 200, session);
    }

    if (req.method === "POST" && url.pathname === "/auth/logout") {
      const header = req.headers["authorization"] || "";
      await auth.logout(header.replace("Bearer ", ""));
      return sendJson(res, 200, { ok: true });
    }

    // ---- Messages (all bodies are ciphertext/HMAC fields only — server cannot decrypt or verify) ----
    if (req.method === "POST" && url.pathname === "/messages") {
      const session = await auth.requireAuth(req);
      const body = await readJsonBody(req);
      const required = ["message_id", "receiver_uuid", "counter", "nonce_base64", "encrypted_payload", "hmac_base64"];
      for (const f of required) if (body[f] === undefined || body[f] === null) return sendJson(res, 400, { error: `missing ${f}` });

      // Oversized-payload rejection (spec section 21) — adjust limit to your real file/message ceiling.
      if (Buffer.byteLength(body.encrypted_payload, "utf8") > MAX_PAYLOAD_BYTES) {
        return sendJson(res, 413, { error: "payload too large" });
      }

      const expiryTime = body.expiry_seconds ? new Date(Date.now() + body.expiry_seconds * 1000) : null;

      let row;
      try {
        const result = await pool.query(
          `insert into messages
             (message_id, sender_uuid, receiver_uuid, counter, nonce_base64, encrypted_payload,
              encrypted_metadata, hmac_base64, key_version, delivery_status, expiry_time)
           values ($1,$2,$3,$4,$5,$6,$7,$8,$9,'SENT',$10)
           returning message_id, created_at, delivery_status`,
          [
            body.message_id, session.username, body.receiver_uuid, body.counter, body.nonce_base64,
            body.encrypted_payload, body.encrypted_metadata || null, body.hmac_base64,
            body.key_version || 1, expiryTime
          ]
        );
        row = result.rows[0];
      } catch (err) {
        // Unique violations on message_id (PK) or (sender,receiver,counter) index = replay attempt.
        if (err.code === "23505") return sendJson(res, 409, { error: "duplicate message_id or replayed counter rejected" });
        throw err;
      }

      // Realtime push to the receiver if connected; server forwards the opaque package as-is —
      // it never decrypts or re-signs anything.
      pushToUser(body.receiver_uuid, {
        type: "new_message",
        message_id: row.message_id,
        sender_uuid: session.username,
        counter: body.counter,
        nonce_base64: body.nonce_base64,
        encrypted_payload: body.encrypted_payload,
        encrypted_metadata: body.encrypted_metadata || null,
        hmac_base64: body.hmac_base64,
        key_version: body.key_version || 1,
        created_at: row.created_at
      });

      return sendJson(res, 201, row);
    }

    if (req.method === "GET" && url.pathname === "/messages") {
      const session = await auth.requireAuth(req);
      const otherUser = url.searchParams.get("with");
      // Never return rows past their expiry_time — self-destruct must hold server-side too.
      const params = otherUser ? [session.username, otherUser] : [session.username];
      const query = otherUser
        ? `select * from messages where ((sender_uuid=$1 and receiver_uuid=$2) or (sender_uuid=$2 and receiver_uuid=$1))
             and (expiry_time is null or expiry_time > now()) order by counter asc`
        : `select * from messages where (sender_uuid=$1 or receiver_uuid=$1)
             and (expiry_time is null or expiry_time > now()) order by created_at asc`;
      const result = await pool.query(query, params);
      return sendJson(res, 200, { rows: result.rows });
    }

    if (req.method === "DELETE" && url.pathname.startsWith("/messages/")) {
      const session = await auth.requireAuth(req);
      const id = url.pathname.split("/messages/")[1];
      await pool.query(
        "delete from messages where message_id=$1 and (sender_uuid=$2 or receiver_uuid=$2)",
        [id, session.username]
      );
      return sendJson(res, 200, { ok: true });
    }

    // ---- Encrypted file blobs (binary, opaque, stored in Supabase Storage; metadata row in `files`) ----
    if (req.method === "POST" && url.pathname === "/files") {
      const session = await auth.requireAuth(req);
      const fileId = req.headers["x-file-id"];
      const receiverUuid = req.headers["x-receiver-uuid"];
      const encryptedMetadata = req.headers["x-encrypted-metadata"];
      const paddedSizeBucket = parseInt(req.headers["x-padded-size-bucket"] || "0", 10);
      if (!fileId || !receiverUuid || !encryptedMetadata) {
        return sendJson(res, 400, { error: "missing x-file-id / x-receiver-uuid / x-encrypted-metadata headers" });
      }

      const buf = await readRawBody(req);
      if (buf.length > MAX_PAYLOAD_BYTES) return sendJson(res, 413, { error: "file too large" });

      const opaqueName = await storage.uploadEncryptedBlob(buf);
      try {
        await pool.query(
          `insert into files (file_id, sender_uuid, receiver_uuid, encrypted_blob_path, encrypted_metadata, padded_size_bucket, delivery_status)
           values ($1,$2,$3,$4,$5,$6,'SENT')`,
          [fileId, session.username, receiverUuid, opaqueName, encryptedMetadata, paddedSizeBucket]
        );
      } catch (err) {
        if (err.code === "23505") return sendJson(res, 409, { error: "duplicate file_id rejected" });
        throw err;
      }
      return sendJson(res, 201, { file_id: fileId, blob_url: opaqueName });
    }

    if (req.method === "GET" && url.pathname.startsWith("/files/")) {
      await auth.requireAuth(req);
      const opaqueName = url.pathname.split("/files/")[1];
      const buf = await storage.downloadEncryptedBlob(opaqueName);
      res.writeHead(200, { "Content-Type": "application/octet-stream" });
      return res.end(buf);
    }

    // ---- Health check (for Render) ----
    if (req.method === "GET" && url.pathname === "/health") {
      return sendJson(res, 200, { ok: true, time: Date.now() });
    }

    sendJson(res, 404, { error: "not found" });
  } catch (err) {
    const status = err.status || 500;
    if (status === 500) console.error(err);
    sendJson(res, status, { error: err.message || "internal error" });
  }
});

// ---- Realtime WebSocket layer ----
const wss = new WebSocketServer({ server, path: "/ws" });

wss.on("connection", async (ws, req) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const token = url.searchParams.get("token");
    if (!token) return ws.close(4401, "missing token");

    const session = await auth.verifyToken(token);
    const userId = session.username;

    if (!liveConnections.has(userId)) liveConnections.set(userId, new Set());
    liveConnections.get(userId).add(ws);

    ws.on("close", () => {
      liveConnections.get(userId)?.delete(ws);
    });

    ws.send(JSON.stringify({ type: "connected", userId }));
  } catch (err) {
    ws.close(4401, "auth failed");
  }
});

// ---- Expiry cleanup (spec section 6/9: delete expired rows + blobs, don't archive plaintext —
// there's no plaintext here to begin with, but the *rows* must still go away on schedule) ----
async function cleanupExpired() {
  try {
    const expiredFiles = await pool.query(
      "select file_id, encrypted_blob_path from files where expiry_time is not null and expiry_time <= now()"
    );
    for (const row of expiredFiles.rows) {
      try { await storage.deleteEncryptedBlob(row.encrypted_blob_path); } catch (e) { console.error("blob delete failed:", e.message); }
    }
    await pool.query("delete from files where expiry_time is not null and expiry_time <= now()");
    await pool.query("delete from messages where expiry_time is not null and expiry_time <= now()");
  } catch (err) {
    console.error("cleanupExpired error:", err);
  }
}
setInterval(cleanupExpired, 60 * 1000); // every minute

server.listen(PORT, () => {
  console.log(`MaunKavach backend listening on :${PORT}`);
  console.log(`WebSocket path: /ws?token=<jwt>`);
});
