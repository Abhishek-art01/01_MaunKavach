-- MaunKavach schema — run this in the Supabase SQL editor (or via `psql` against your
-- Supabase connection string) before first deploy.
--
-- Reminder of the contract (spec section 6): this database NEVER stores plaintext messages,
-- plaintext files, plaintext filenames, MIME types, thumbnails, Vault keys, AES keys, HMAC
-- keys, substitution tables, or rotation rules. Every "encrypted_*"/ciphertext/hmac column
-- holds only opaque base64 strings produced on-device by the Android app; this server cannot
-- decrypt or verify any of it (it doesn't hold the contact keys).

create extension if not exists "pgcrypto";

create table if not exists users (
    id uuid primary key default gen_random_uuid(),
    username text unique not null,
    password_hash text not null,        -- bcrypt hash, never plaintext password
    created_at timestamptz not null default now()
);

create table if not exists sessions (
    token text primary key,
    user_id uuid not null references users(id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

-- Spec section 6 "Messages table" — exact column set, plus replay-protection columns
-- (counter, nonce) the server enforces as defense-in-depth alongside the client's own check.
create table if not exists messages (
    message_id uuid primary key,             -- client-generated; PRIMARY KEY enforces no duplicate message_id server-side
    sender_uuid text not null,
    receiver_uuid text not null,
    counter bigint not null,                  -- per-(sender,receiver) ratchet counter, used to reject stale/replayed values
    nonce_base64 text not null,
    encrypted_payload text not null,          -- AES-256-GCM ciphertext, base64
    encrypted_metadata text,                  -- self-destruct policy etc, itself encrypted
    hmac_base64 text not null,                -- HMAC-SHA256 tag; server stores but cannot verify (no HMAC key)
    key_version int not null default 1,
    created_at timestamptz not null default now(),
    delivery_status text not null default 'SENT',
    expiry_time timestamptz                   -- self-destruct: server purges the row after this time
);

create unique index if not exists idx_messages_replay_guard on messages (sender_uuid, receiver_uuid, counter);
create index if not exists idx_messages_thread on messages (sender_uuid, receiver_uuid, created_at);
create index if not exists idx_messages_receiver on messages (receiver_uuid, created_at);
create index if not exists idx_messages_expiry on messages (expiry_time) where expiry_time is not null;

-- Spec section 6 "Files table" — exact column set.
create table if not exists files (
    file_id uuid primary key,
    sender_uuid text not null,
    receiver_uuid text not null,
    encrypted_blob_path text not null,        -- Supabase Storage object path (random UUID name, never original filename)
    encrypted_metadata text not null,         -- encrypted filename + MIME + size-category, all together
    padded_size_bucket bigint not null,       -- rounded-up size bucket only, never the exact byte count
    created_at timestamptz not null default now(),
    delivery_status text not null default 'SENT',
    expiry_time timestamptz
);

create index if not exists idx_files_receiver on files (receiver_uuid, created_at);
create index if not exists idx_files_expiry on files (expiry_time) where expiry_time is not null;

-- Row Level Security: kept as a backstop since only the server-side service-role key talks to
-- Postgres (the Android app never gets the service-role key) — tighten further if you later
-- let any client hit Supabase directly.
alter table messages enable row level security;
alter table files enable row level security;
alter table users enable row level security;
alter table sessions enable row level security;

create policy "service role full access messages" on messages using (true) with check (true);
create policy "service role full access files" on files using (true) with check (true);
create policy "service role full access users" on users using (true) with check (true);
create policy "service role full access sessions" on sessions using (true) with check (true);
