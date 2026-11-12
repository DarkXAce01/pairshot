-- Accounts are businesses. One per Google account; in dev mode, one per device.
CREATE TABLE accounts (
  id TEXT PRIMARY KEY,
  google_sub TEXT UNIQUE,
  device_id TEXT,
  email TEXT,
  plan TEXT NOT NULL DEFAULT 'free',          -- 'free' | 'pro'
  inbound_token TEXT NOT NULL UNIQUE,         -- secret in the CRM webhook URL
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_accounts_device ON accounts(device_id);

CREATE TABLE sessions (
  token_hash TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL
);

-- Stored photos, named by the SHA-256 of their bytes. R2 key: p/{account_id}/{hash}
CREATE TABLE photos (
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  hash TEXT NOT NULL,
  bytes INTEGER NOT NULL,
  content_type TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (account_id, hash)
);

-- One per job. The id is made on the phone.
CREATE TABLE galleries (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  job_id TEXT NOT NULL,
  external_id TEXT,                            -- the CRM's job id, if the job came from the CRM
  slug TEXT NOT NULL UNIQUE,
  manifest TEXT,                               -- JSON, image src = photo hash; null until first publish
  job_json TEXT,                               -- client details for webhooks (never shown on the page)
  lock_key TEXT NOT NULL,                      -- job title + place at first send; free plan can't change it
  created_at INTEGER NOT NULL,
  published_at INTEGER,
  updated_at INTEGER,
  expires_at INTEGER,
  revoked_at INTEGER,
  first_viewed_at INTEGER,
  view_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_galleries_account ON galleries(account_id);
CREATE INDEX idx_galleries_external ON galleries(account_id, external_id);

-- Photos the gallery shows right now.
CREATE TABLE gallery_photos (
  gallery_id TEXT NOT NULL REFERENCES galleries(id) ON DELETE CASCADE,
  hash TEXT NOT NULL,
  PRIMARY KEY (gallery_id, hash)
);
CREATE INDEX idx_gallery_photos_hash ON gallery_photos(hash);

-- Every photo ever put in a gallery (stops one link being reused for job after job).
CREATE TABLE gallery_history (
  gallery_id TEXT NOT NULL REFERENCES galleries(id) ON DELETE CASCADE,
  hash TEXT NOT NULL,
  PRIMARY KEY (gallery_id, hash)
);

-- Counters for limits: (account, what, period) -> count.
CREATE TABLE counters (
  account_id TEXT NOT NULL,
  name TEXT NOT NULL,
  period TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, name, period)
);

-- Jobs sent in by the CRM, waiting for the phone to pick them up.
CREATE TABLE crm_jobs (
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  external_id TEXT NOT NULL,
  data TEXT NOT NULL,                          -- normalised JSON
  cancelled INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (account_id, external_id)
);
CREATE INDEX idx_crm_jobs_updated ON crm_jobs(account_id, updated_at);

CREATE TABLE webhooks (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  url TEXT NOT NULL,
  secret TEXT NOT NULL,
  events TEXT NOT NULL,                        -- comma separated
  created_at INTEGER NOT NULL
);

CREATE TABLE webhook_deliveries (
  id TEXT PRIMARY KEY,
  webhook_id TEXT NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
  account_id TEXT NOT NULL,
  event TEXT NOT NULL,
  payload TEXT NOT NULL,
  status TEXT NOT NULL,                        -- 'pending' | 'ok' | 'failed'
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at INTEGER,
  last_status INTEGER,
  last_error TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_deliveries_due ON webhook_deliveries(status, next_attempt_at);
CREATE INDEX idx_deliveries_account ON webhook_deliveries(account_id, created_at);

CREATE TABLE api_keys (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  key_hash TEXT NOT NULL UNIQUE,
  prefix TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_used_at INTEGER
);
