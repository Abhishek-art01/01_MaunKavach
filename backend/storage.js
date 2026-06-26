const { createClient } = require("@supabase/supabase-js");
const crypto = require("crypto");

const SUPABASE_URL = process.env.SUPABASE_URL;
const SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const BUCKET = process.env.SUPABASE_STORAGE_BUCKET;

if (!SUPABASE_URL || !SERVICE_ROLE_KEY || !BUCKET) {
  throw new Error("SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, and SUPABASE_STORAGE_BUCKET must all be set.");
}

// Service-role key — full bucket access. This lives ONLY on the Render server's env vars.
// It must never be embedded in or shipped with the Android app.
const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

/**
 * Stores an already-encrypted blob (raw AES-GCM ciphertext bytes from the Android app) under
 * a random opaque object name. The original filename never reaches this function — the app
 * encrypts the filename separately as `encrypted_metadata` on the message row.
 */
async function uploadEncryptedBlob(buffer) {
  const opaqueName = `${crypto.randomUUID()}.bin`;
  const { error } = await supabase.storage.from(BUCKET).upload(opaqueName, buffer, {
    contentType: "application/octet-stream", // deliberately generic — true mime is encrypted client-side
    upsert: false
  });
  if (error) throw error;
  return opaqueName;
}

async function downloadEncryptedBlob(opaqueName) {
  const { data, error } = await supabase.storage.from(BUCKET).download(opaqueName);
  if (error) throw error;
  return Buffer.from(await data.arrayBuffer());
}

/** Short-lived signed URL, in case the Android app wants to stream large blobs directly instead of proxying through Render. */
async function createSignedDownloadUrl(opaqueName, expiresInSeconds = 300) {
  const { data, error } = await supabase.storage.from(BUCKET).createSignedUrl(opaqueName, expiresInSeconds);
  if (error) throw error;
  return data.signedUrl;
}

async function deleteEncryptedBlob(opaqueName) {
  const { error } = await supabase.storage.from(BUCKET).remove([opaqueName]);
  if (error) throw error;
}

module.exports = { uploadEncryptedBlob, downloadEncryptedBlob, createSignedDownloadUrl, deleteEncryptedBlob, BUCKET };
