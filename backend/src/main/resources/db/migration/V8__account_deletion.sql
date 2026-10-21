-- Account deletion anonymises the user row instead of removing it: expenses,
-- shares and settlements keep pointing at the same id, so every group's history
-- and balances stay exactly as they were. deleted_at marks the row as a tombstone.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;
