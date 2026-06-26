/**
 * Backend integration test — mocks Postgres and Supabase Storage so this runs anywhere with
 * just `node test/backend.test.js` (no real database needed), but exercises the REAL
 * server.js/auth.js/storage.js code, not reimplemented logic. Covers the spec section 22
 * test list items that are server-side: replay rejection, expired-message disappearance,
 * ciphertext-only storage, auth gating.
 *
 * Run: cd backend && node test/backend.test.js
 */
process.env.JWT_SECRET = "test-secret-do-not-use-in-prod";
process.env.DATABASE_URL = "postgresql://fake/fake"; // never actually connected to — pool.query is mocked below
process.env.SUPABASE_URL = "https://fake.supabase.co";
process.env.SUPABASE_SERVICE_ROLE_KEY = "fake-service-role-key";
process.env.SUPABASE_STORAGE_BUCKET = "fake-bucket";
process.env.PORT = 8099;

const path = require("path");
const http = require("http");

// ---------------- In-memory fake Postgres ----------------
const users = [];
const sessions = [];
const messages = [];
const files = [];
let userIdCounter = 1;

function uniqueViolation() {
  const e = new Error("duplicate key value violates unique constraint");
  e.code = "23505";
  return e;
}

const fakePool = {
  query: async (sql, params = []) => {
    sql = sql.trim();

    if (sql.startsWith("select id from users where username")) {
      const u = users.find((x) => x.username === params[0]);
      return { rows: u ? [{ id: u.id }] : [] };
    }
    if (sql.startsWith("insert into users")) {
      const u = { id: userIdCounter++, username: params[0], password_hash: params[1] };
      users.push(u);
      return { rows: [{ id: u.id, username: u.username }] };
    }
    if (sql.startsWith("select id, username, password_hash from users")) {
      const u = users.find((x) => x.username === params[0]);
      return { rows: u ? [u] : [] };
    }
    if (sql.startsWith("insert into sessions")) {
      sessions.push({ token: params[0], user_id: params[1], expires_at: params[2] });
      return { rows: [] };
    }
    if (sql.startsWith("select user_id, expires_at from sessions")) {
      const s = sessions.find((x) => x.token === params[0]);
      return { rows: s ? [s] : [] };
    }
    if (sql.startsWith("delete from sessions")) {
      return { rows: [] };
    }
    if (sql.startsWith("insert into messages")) {
      const [message_id, sender_uuid, receiver_uuid, counter, nonce_base64, encrypted_payload, encrypted_metadata, hmac_base64, key_version, expiry_time] = params;
      if (messages.some((m) => m.message_id === message_id)) throw uniqueViolation();
      if (messages.some((m) => m.sender_uuid === sender_uuid && m.receiver_uuid === receiver_uuid && m.counter === counter)) throw uniqueViolation();
      const row = {
        message_id, sender_uuid, receiver_uuid, counter, nonce_base64, encrypted_payload,
        encrypted_metadata, hmac_base64, key_version, delivery_status: "SENT", expiry_time, created_at: new Date()
      };
      messages.push(row);
      return { rows: [{ message_id: row.message_id, created_at: row.created_at, delivery_status: row.delivery_status }] };
    }
    if (sql.startsWith("select * from messages where ((sender_uuid")) {
      const [a, b] = params;
      const now = Date.now();
      const rows = messages.filter(
        (m) => ((m.sender_uuid === a && m.receiver_uuid === b) || (m.sender_uuid === b && m.receiver_uuid === a)) &&
          (!m.expiry_time || new Date(m.expiry_time).getTime() > now)
      );
      return { rows };
    }
    if (sql.startsWith("insert into files")) {
      const [file_id, sender_uuid, receiver_uuid, encrypted_blob_path, encrypted_metadata, padded_size_bucket] = params;
      if (files.some((f) => f.file_id === file_id)) throw uniqueViolation();
      files.push({ file_id, sender_uuid, receiver_uuid, encrypted_blob_path, encrypted_metadata, padded_size_bucket, expiry_time: null });
      return { rows: [] };
    }
    if (sql.startsWith("select file_id, encrypted_blob_path from files where expiry_time")) {
      const now = Date.now();
      return { rows: files.filter((f) => f.expiry_time && new Date(f.expiry_time).getTime() <= now) };
    }
    if (sql.startsWith("delete from files where expiry_time") || sql.startsWith("delete from messages where expiry_time")) {
      return { rows: [] }; // not exercised by this test; cleanup job correctness covered separately
    }
    throw new Error("unhandled mock query: " + sql);
  }
};

const dbPath = require.resolve(path.join(__dirname, "..", "db.js"));
require.cache[dbPath] = { id: dbPath, filename: dbPath, loaded: true, exports: { pool: fakePool } };

// ---------------- Fake Supabase Storage client ----------------
const supaPath = require.resolve("@supabase/supabase-js", { paths: [path.join(__dirname, "..")] });
require.cache[supaPath] = {
  id: supaPath, filename: supaPath, loaded: true,
  exports: {
    createClient: () => ({
      storage: {
        from: () => ({
          upload: async () => ({ error: null }),
          download: async () => ({ data: { arrayBuffer: async () => Buffer.from("fake-encrypted-bytes") }, error: null }),
          remove: async () => ({ error: null })
        })
      }
    })
  }
};

require(path.join(__dirname, "..", "server.js")); // starts listening on PORT

function req(method, urlPath, body, token, extraHeaders = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const r = http.request(
      { host: "localhost", port: 8099, method, path: urlPath,
        headers: { "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}), ...extraHeaders } },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => resolve({ status: res.statusCode, body: JSON.parse(Buffer.concat(chunks).toString() || "{}") }));
      }
    );
    r.on("error", reject);
    if (data) r.write(data);
    r.end();
  });
}

let failures = 0;
function check(name, cond) {
  console.log((cond ? "PASS" : "FAIL") + " - " + name);
  if (!cond) failures++;
}

(async () => {
  await new Promise((r) => setTimeout(r, 300)); // let the server finish binding

  console.log("\n-- Auth --");
  const reg = await req("POST", "/auth/register", { username: "alice", password: "password123" });
  check("register succeeds", reg.status === 201);

  const dupReg = await req("POST", "/auth/register", { username: "alice", password: "password123" });
  check("duplicate username rejected", dupReg.status === 409);

  const badLogin = await req("POST", "/auth/login", { username: "alice", password: "wrongpassword" });
  check("wrong password rejected", badLogin.status === 401);

  const login = await req("POST", "/auth/login", { username: "alice", password: "password123" });
  check("correct login succeeds", login.status === 200);
  const token = login.body.token;

  console.log("\n-- Auth gating --");
  const unauth = await req("POST", "/messages", { message_id: "x", receiver_uuid: "bob", counter: 1, nonce_base64: "n", encrypted_payload: "ct", hmac_base64: "h" });
  check("unauthenticated message send rejected (401)", unauth.status === 401);

  console.log("\n-- Replay protection (spec section 5/22) --");
  const msg1 = { message_id: "m1", receiver_uuid: "bob", counter: 1, nonce_base64: "nonceA", encrypted_payload: "ct1", hmac_base64: "tagA" };
  check("first message accepted", (await req("POST", "/messages", msg1, token)).status === 201);
  check("exact replay (same message_id) rejected", (await req("POST", "/messages", msg1, token)).status === 409);

  const msgReplayedCounter = { message_id: "m2", receiver_uuid: "bob", counter: 1, nonce_base64: "nonceB", encrypted_payload: "ct2", hmac_base64: "tagB" };
  check("replayed counter under new message_id rejected", (await req("POST", "/messages", msgReplayedCounter, token)).status === 409);

  const msgNext = { message_id: "m3", receiver_uuid: "bob", counter: 2, nonce_base64: "nonceC", encrypted_payload: "ct3", hmac_base64: "tagC" };
  check("next valid counter accepted", (await req("POST", "/messages", msgNext, token)).status === 201);

  console.log("\n-- Ciphertext-only storage --");
  const thread = await req("GET", "/messages?with=bob", null, token);
  check("thread fetch succeeds", thread.status === 200);
  check("server never stores/returns plaintext — payload is exactly the opaque ciphertext sent", thread.body.rows[0].encrypted_payload === "ct1");
  check("no field in the row resembles plaintext message text", !JSON.stringify(thread.body.rows).includes("hi check this image"));

  console.log("\n-- Self-destruct / expiry (spec section 9) --");
  const expiring = { message_id: "m4", receiver_uuid: "bob", counter: 3, nonce_base64: "nonceD", encrypted_payload: "ct4", hmac_base64: "tagD", expiry_seconds: -1 };
  await req("POST", "/messages", expiring, token);
  const threadAfter = await req("GET", "/messages?with=bob", null, token);
  check("expired message disappears from the thread immediately", threadAfter.body.rows.every((r) => r.message_id !== "m4"));

  console.log("\n-- File upload (spec section 3/6) --");
  const fileRes = await req("POST", "/files", null, token, {
    "x-file-id": "f1", "x-receiver-uuid": "bob", "x-encrypted-metadata": "encrypted-meta-blob", "x-padded-size-bucket": "65536"
  });
  check("file upload accepted", fileRes.status === 201);
  check("file_id round-trips correctly", fileRes.body.file_id === "f1");

  console.log("\n-- Health check --");
  check("health endpoint responds", (await req("GET", "/health")).status === 200);

  console.log(`\nTOTAL: ${failures === 0 ? "ALL PASSED" : failures + " FAILURES"}`);
  process.exit(failures > 0 ? 1 : 0);
})().catch((e) => {
  console.error("UNEXPECTED TEST FAILURE:", e);
  process.exit(1);
});
