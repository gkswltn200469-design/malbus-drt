PRAGMA foreign_keys = ON;

CREATE TABLE stop (
  id   TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  lat  REAL NOT NULL,
  lng  REAL NOT NULL
) STRICT;

CREATE TABLE stop_alias (
  id      INTEGER PRIMARY KEY,
  stop_id TEXT NOT NULL REFERENCES stop(id),
  alias   TEXT NOT NULL,
  UNIQUE (stop_id, alias)
) STRICT;

CREATE TABLE reservation (
  id           INTEGER PRIMARY KEY,
  channel      TEXT NOT NULL DEFAULT 'AGENT_CALL' CHECK (channel IN ('AGENT_CALL')),
  status       TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CONFIRMED')),
  mode         TEXT NOT NULL CHECK (mode IN ('LLM','RULE')),
  received_at  TEXT NOT NULL,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL,
  confirmed_at TEXT,
  confirmed_by TEXT,
  CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL))
) STRICT;

CREATE TABLE reservation_field (
  reservation_id   INTEGER NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
  name             TEXT NOT NULL CHECK (name IN ('purpose','from','to','date','time','people')),
  value            TEXT,
  stop_id          TEXT REFERENCES stop(id),
  said             TEXT,
  needs_check      INTEGER NOT NULL DEFAULT 1 CHECK (needs_check IN (0,1)),
  reason           TEXT CHECK (reason IN ('MISSING','AMBIGUOUS','INVALID','CORRECTED','NOT_IN_LIST')),
  ask_hint         TEXT,
  evidence_line_no INTEGER,
  evidence_text    TEXT,
  previous_value   TEXT,
  source           TEXT CHECK (source IN ('AI','RULE','AGENT')),
  decided_at       TEXT,
  note             TEXT,
  more_count       INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (reservation_id, name),
  CHECK (name IN ('from','to') OR stop_id IS NULL),   -- 정류장 FK는 출발·도착만
  CHECK (name NOT IN ('from','to') OR value IS NULL), -- 출발·도착 값은 stop_id에
  CHECK (needs_check = 1 OR reason IS NULL)           -- 확인 완료면 사유 없음
) STRICT;

CREATE TABLE field_candidate (
  reservation_id INTEGER NOT NULL,
  field_name     TEXT NOT NULL CHECK (field_name IN ('from','to')),
  rank           INTEGER NOT NULL CHECK (rank BETWEEN 1 AND 5),
  stop_id        TEXT NOT NULL REFERENCES stop(id),
  match_type     TEXT NOT NULL CHECK (match_type IN ('NAME','ALIAS','LEARNED','PARTIAL','FUZZY')),
  reason         TEXT NOT NULL,
  PRIMARY KEY (reservation_id, field_name, rank),
  UNIQUE (reservation_id, field_name, stop_id),
  FOREIGN KEY (reservation_id, field_name)
    REFERENCES reservation_field(reservation_id, name) ON DELETE CASCADE
) STRICT;

CREATE TABLE transcript_line (
  reservation_id INTEGER NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
  line_no        INTEGER NOT NULL CHECK (line_no >= 1),
  at             TEXT NOT NULL,
  speaker        TEXT NOT NULL CHECK (speaker IN ('AGENT','CUSTOMER','SYSTEM')),
  text           TEXT NOT NULL,
  PRIMARY KEY (reservation_id, line_no)
) STRICT;

CREATE TABLE learned_alias (
  id         INTEGER PRIMARY KEY,
  said       TEXT NOT NULL,
  stop_id    TEXT NOT NULL REFERENCES stop(id),
  count      INTEGER NOT NULL DEFAULT 1 CHECK (count >= 1),
  status     TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  first_seen TEXT NOT NULL,
  last_seen  TEXT NOT NULL,
  UNIQUE (said, stop_id)
) STRICT;

CREATE TABLE learned_alias_reservation (
  learned_alias_id INTEGER NOT NULL REFERENCES learned_alias(id) ON DELETE CASCADE,
  reservation_id   INTEGER NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
  PRIMARY KEY (learned_alias_id, reservation_id)
) STRICT;

CREATE INDEX idx_reservation_status ON reservation(status);
CREATE INDEX idx_learned_alias_said ON learned_alias(said);
CREATE INDEX idx_learned_alias_status ON learned_alias(status);
