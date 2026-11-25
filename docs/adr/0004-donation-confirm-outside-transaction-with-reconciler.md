# 후원 승인은 세 단계로 나누고, 결과를 모르면 주기 조회로 마무리한다

처음에는 주문 행을 `FOR UPDATE`로 잠근 트랜잭션 안에서 토스 승인 API(최대 35초)를 불렀다. 토스가 느리면 DB 커넥션과 잠금을 오래 붙잡고, 응답이 끊기면 토스는 승인했는데 우리는 롤백해 적립이 빠질 수 있었다. 그래서 ① 잠그고 확인한 뒤 `CONFIRMING`으로 표시해 커밋 ② 트랜잭션 밖에서 토스 호출(`Idempotency-Key: orderId`) ③ 다시 잠그고 결과 반영으로 나눴다. 결과를 모르면 `CONFIRMING`으로 남기고 504 `PAYMENT_PENDING`을 돌려주며, `DonationReconcileScheduler`가 토스 주문 조회로 적립하거나 `PENDING`으로 되돌린다.

근거: 커밋 d8e726a, Flyway `V9__add_donation_confirming.sql`, PR #72, [docs/DESIGN.md](../DESIGN.md) 7.1절.

## Consequences

- 후원 주문은 `PENDING` · `CONFIRMING` · `DONE` 세 상태를 가진다. 504는 실패가 아니라 "확인 중"이고, 화면도 그렇게 보여 준다.
- 토스 호출을 다시 트랜잭션 안으로 옮기는 "단순화"는 이 문제를 되살린다.
