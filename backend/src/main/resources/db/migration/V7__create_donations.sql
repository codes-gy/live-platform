-- 후원 주문과 결제 결과를 한 행에 둔다. 주문을 먼저 PENDING으로 만들고, 토스 승인 뒤 DONE으로 바꾼다
CREATE TABLE donations
(
    id           BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    donor_id     BIGINT       NOT NULL REFERENCES users (id),
    order_id     VARCHAR(64)  NOT NULL UNIQUE,
    amount       INTEGER      NOT NULL CHECK (amount > 0),
    message      VARCHAR(100),
    status       VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'DONE')),
    -- 같은 결제가 두 번 적립되지 않게 한다(중복 승인 통지)
    payment_key  VARCHAR(200) UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at  TIMESTAMPTZ
);

-- 방송별 후원 내역(최신순) · 합계
CREATE INDEX idx_donations_broadcast_done ON donations (broadcast_id, id DESC) WHERE status = 'DONE';
