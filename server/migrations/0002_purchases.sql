-- Google Play purchases of Pro. A purchase token unlocks one account; deleting the account frees it.
CREATE TABLE purchases (
  token TEXT PRIMARY KEY,
  account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL,
  order_id TEXT,
  state TEXT NOT NULL DEFAULT 'active',      -- 'active' | 'voided' (refunded or charged back)
  purchased_at INTEGER,
  created_at INTEGER NOT NULL
);
CREATE INDEX idx_purchases_account ON purchases(account_id);
