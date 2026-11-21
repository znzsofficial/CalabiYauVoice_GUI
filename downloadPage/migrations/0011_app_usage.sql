-- Beijing civil days as Unix day numbers. One cumulative snapshot per participating installation/day.
CREATE TABLE app_usage_daily (
  day INTEGER NOT NULL,
  install_key TEXT NOT NULL,
  cohort INTEGER NOT NULL,
  version TEXT NOT NULL,
  seq INTEGER NOT NULL,
  active INTEGER NOT NULL,
  starts INTEGER NOT NULL,
  counts TEXT NOT NULL,
  PRIMARY KEY (day, install_key)
) WITHOUT ROWID;
