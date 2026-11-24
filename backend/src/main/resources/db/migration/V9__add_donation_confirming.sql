-- 토스 승인 호출을 트랜잭션(행 잠금) 밖으로 빼면서, 승인을 요청했지만 결과를 아직 반영하지 못한 상태(CONFIRMING)를 둔다.
-- 응답이 끊기거나 서버가 재시작돼도 이 표시가 남아, 정리 작업이 토스 결제 조회로 DONE 또는 PENDING으로 마무리한다
ALTER TABLE donations DROP CONSTRAINT donations_status_check;
ALTER TABLE donations
    ADD CONSTRAINT donations_status_check CHECK (status IN ('PENDING', 'CONFIRMING', 'DONE'));

ALTER TABLE donations ADD COLUMN confirm_requested_at TIMESTAMPTZ;

-- 정리 작업이 CONFIRMING 주문만 찾는다
CREATE INDEX idx_donations_confirming ON donations (confirm_requested_at) WHERE status = 'CONFIRMING';
