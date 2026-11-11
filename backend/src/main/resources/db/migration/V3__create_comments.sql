CREATE TABLE comments
(
    id           BIGSERIAL PRIMARY KEY,
    -- 방송을 지우면(종료 · 준비 상태만 가능) 댓글도 함께 지운다
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    author_id    BIGINT       NOT NULL REFERENCES users (id),
    content      VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ
);

-- 방송별 최신순 커서 조회(id < before)
CREATE INDEX idx_comments_broadcast_id ON comments (broadcast_id, id DESC);
