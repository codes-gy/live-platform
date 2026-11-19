-- 방송별 마지막 썸네일 1장. 목록 조회가 이미지까지 읽지 않도록 broadcasts와 나눈다
CREATE TABLE broadcast_thumbnails
(
    broadcast_id BIGINT PRIMARY KEY REFERENCES broadcasts (id) ON DELETE CASCADE,
    image        BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);
