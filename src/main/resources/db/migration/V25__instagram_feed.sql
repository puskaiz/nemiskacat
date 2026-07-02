-- Instagram feed cache (T-INSTAGRAM): DB-backed media cache and token store.
CREATE TABLE instagram_media (
    id          TEXT        NOT NULL PRIMARY KEY,   -- IG media id
    caption     TEXT,                               -- nullable
    media_type  TEXT        NOT NULL,
    display_url TEXT        NOT NULL,
    permalink   TEXT        NOT NULL,
    taken_at    TIMESTAMPTZ NOT NULL,
    position    INT         NOT NULL,
    fetched_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_instagram_media_position ON instagram_media (position ASC);

CREATE TABLE instagram_token (
    id           SMALLINT    NOT NULL PRIMARY KEY CHECK (id = 1),  -- singleton row
    access_token TEXT        NOT NULL,
    expires_at   TIMESTAMPTZ,                       -- null until first refresh
    updated_at   TIMESTAMPTZ NOT NULL
);
