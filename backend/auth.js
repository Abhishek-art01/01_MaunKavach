const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");
const { pool } = require("./db");

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  throw new Error("JWT_SECRET is not set — generate one with `openssl rand -hex 32`.");
}

const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

/**
 * Custom auth, entirely server-side. Important boundary: this has nothing to do with the
 * Vault Key. The account password proves "this is the same person who registered this
 * username" — it never derives, wraps, or gates any encryption key. Vault Key material is
 * generated and stored only on-device (Android Keystore + EncryptedSharedPreferences) and
 * this server never sees it, regardless of login state.
 */

async function register(username, password) {
  if (!username || username.length < 3) throw httpError(400, "username must be at least 3 characters");
  if (!password || password.length < 8) throw httpError(400, "password must be at least 8 characters");

  const existing = await pool.query("select id from users where username = $1", [username]);
  if (existing.rows.length > 0) throw httpError(409, "username already taken");

  const passwordHash = await bcrypt.hash(password, 12);
  const result = await pool.query(
    "insert into users (username, password_hash) values ($1, $2) returning id, username",
    [username, passwordHash]
  );
  return result.rows[0];
}

async function login(username, password) {
  const result = await pool.query("select id, username, password_hash from users where username = $1", [username]);
  const user = result.rows[0];
  if (!user) throw httpError(401, "invalid username or password");

  const valid = await bcrypt.compare(password, user.password_hash);
  if (!valid) throw httpError(401, "invalid username or password");

  const token = crypto.randomBytes(32).toString("hex");
  const expiresAt = new Date(Date.now() + SESSION_TTL_MS);
  await pool.query(
    "insert into sessions (token, user_id, expires_at) values ($1, $2, $3)",
    [token, user.id, expiresAt]
  );

  const jwtToken = jwt.sign({ sub: user.id, username: user.username, sessionToken: token }, JWT_SECRET, {
    expiresIn: "30d"
  });

  return { token: jwtToken, userId: user.id, username: user.username };
}

async function verifyToken(jwtToken) {
  let payload;
  try {
    payload = jwt.verify(jwtToken, JWT_SECRET);
  } catch {
    throw httpError(401, "invalid or expired token");
  }

  const result = await pool.query(
    "select user_id, expires_at from sessions where token = $1",
    [payload.sessionToken]
  );
  const session = result.rows[0];
  if (!session || new Date(session.expires_at) < new Date()) {
    throw httpError(401, "session expired — please log in again");
  }
  return { userId: payload.sub, username: payload.username };
}

async function logout(jwtToken) {
  try {
    const payload = jwt.verify(jwtToken, JWT_SECRET);
    await pool.query("delete from sessions where token = $1", [payload.sessionToken]);
  } catch {
    // already invalid/expired — nothing to do
  }
}

function httpError(status, message) {
  const err = new Error(message);
  err.status = status;
  return err;
}

/** Express-less auth middleware helper for the plain http server in server.js. */
async function requireAuth(req) {
  const header = req.headers["authorization"];
  if (!header || !header.startsWith("Bearer ")) throw httpError(401, "missing Authorization header");
  return verifyToken(header.slice("Bearer ".length));
}

module.exports = { register, login, verifyToken, logout, requireAuth, httpError };
