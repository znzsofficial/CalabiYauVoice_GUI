-- Compacted Beijing-day totals. No per-install keys, IPs, versions or individual events.
CREATE TABLE app_usage_history (
  day INTEGER PRIMARY KEY,
  starts INTEGER NOT NULL,
  new_installs INTEGER NOT NULL
);
