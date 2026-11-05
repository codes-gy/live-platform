CREATE TABLE broadcasts
(
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT        NOT NULL REFERENCES users (id),
    title       VARCHAR(100)  NOT NULL,
    description VARCHAR(1000),
    status      VARCHAR(10)   NOT NULL,
    started_at  TIMESTAMPTZ,
    ended_at    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_broadcasts_status CHECK (status IN ('READY', 'LIVE', 'ENDED'))
);

-- 목록: 상태 필터 + 최신순
CREATE INDEX idx_broadcasts_status_created ON broadcasts (status, created_at DESC);
-- 내 방송 목록
CREATE INDEX idx_broadcasts_owner_created ON broadcasts (owner_id, created_at DESC);
