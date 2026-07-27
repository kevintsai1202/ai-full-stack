-- 文章媒體索引；檔案本體存 MinIO，資料庫只保存可稽核的中繼資料。
CREATE TABLE media_asset (
    id            BIGSERIAL PRIMARY KEY,
    object_key    TEXT        NOT NULL UNIQUE,
    sha256        VARCHAR(64) NOT NULL UNIQUE,
    kind          TEXT        NOT NULL,
    content_type  TEXT        NOT NULL,
    size_bytes    BIGINT      NOT NULL,
    original_name TEXT        NOT NULL,
    width         INTEGER,
    height        INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_media_asset_kind CHECK (kind IN ('IMAGE', 'FILE')),
    CONSTRAINT ck_media_asset_size CHECK (size_bytes > 0),
    CONSTRAINT ck_media_asset_dimensions CHECK (
        (kind = 'IMAGE' AND width > 0 AND height > 0)
        OR (kind = 'FILE' AND width IS NULL AND height IS NULL)
    )
);

-- 封面只保存媒體 ID；刪除媒體時回退 Emoji，不會連帶刪除文章。
ALTER TABLE campaign
    ADD COLUMN cover_media_id BIGINT REFERENCES media_asset(id) ON DELETE SET NULL;

CREATE INDEX idx_campaign_cover_media ON campaign (cover_media_id)
    WHERE cover_media_id IS NOT NULL;
