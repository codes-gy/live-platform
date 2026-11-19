-- 라이브 방송 녹화(LiveKit Egress → R2). 방송이 시작될 때 RECORDING으로 만들고, egress_ended 웹훅으로 READY · FAILED가 된다
CREATE TABLE recordings
(
    id            BIGSERIAL PRIMARY KEY,
    broadcast_id  BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    -- LiveKit이 준 녹화 작업 ID. 녹화를 시작하지 못했으면 null
    egress_id     VARCHAR(100) UNIQUE,
    status        VARCHAR(16)  NOT NULL CHECK (status IN ('RECORDING', 'READY', 'FAILED')),
    -- R2 버킷 안의 파일 위치(버킷은 비공개, 재생할 때 서명된 주소를 만든다)
    object_key    VARCHAR(300) NOT NULL,
    duration_ms   BIGINT,
    size_bytes    BIGINT,
    error_message VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at      TIMESTAMPTZ
);

CREATE INDEX idx_recordings_ready ON recordings (id DESC) WHERE status = 'READY';
CREATE INDEX idx_recordings_broadcast ON recordings (broadcast_id, id DESC);
