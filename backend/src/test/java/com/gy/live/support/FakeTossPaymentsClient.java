package com.gy.live.support;

import com.gy.live.donation.toss.TossPaymentsClient;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 토스 승인 · 조회 API 대신. 기본은 요청대로 승인하고, 거절 · 연결 실패 · 다른 금액 응답 ·
 * 승인은 됐는데 응답이 끊긴 경우(TIMEOUT_AFTER_APPROVE)를 흉내 낼 수 있다. 승인한 결제는 조회 API로 다시 볼 수 있다.
 */
public class FakeTossPaymentsClient implements TossPaymentsClient {

	public enum Mode { APPROVE, REJECT, UNAVAILABLE, WRONG_AMOUNT, TIMEOUT_AFTER_APPROVE }

	private volatile Mode mode = Mode.APPROVE;
	private volatile boolean lookupUnavailable;
	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicInteger lookups = new AtomicInteger();
	private final Map<String, TossPayment> payments = new ConcurrentHashMap<>();

	public void reset() {
		mode = Mode.APPROVE;
		lookupUnavailable = false;
		calls.set(0);
		lookups.set(0);
		payments.clear();
	}

	public void mode(Mode mode) {
		this.mode = mode;
	}

	/** 조회 API가 연결 실패하게 한다. */
	public void lookupUnavailable(boolean unavailable) {
		this.lookupUnavailable = unavailable;
	}

	/** 토스 쪽 결제 상태를 직접 정한다(예: 사용자가 취소해 ABORTED). */
	public void putPayment(TossPayment payment) {
		payments.put(payment.orderId(), payment);
	}

	public int calls() {
		return calls.get();
	}

	public int lookups() {
		return lookups.get();
	}

	@Override
	public TossPayment confirm(String paymentKey, String orderId, long amount) {
		calls.incrementAndGet();
		return switch (mode) {
			case APPROVE -> record(new TossPayment(paymentKey, orderId, "DONE", amount));
			case WRONG_AMOUNT -> new TossPayment(paymentKey, orderId, "DONE", amount + 1);
			case REJECT -> throw new TossPaymentException("거절", true);
			case UNAVAILABLE -> throw new TossPaymentException("연결 실패", false);
			case TIMEOUT_AFTER_APPROVE -> {
				record(new TossPayment(paymentKey, orderId, "DONE", amount));
				throw new TossPaymentException("읽기 시간 초과", false);
			}
		};
	}

	@Override
	public Optional<TossPayment> findByOrderId(String orderId) {
		lookups.incrementAndGet();
		if (lookupUnavailable) {
			throw new TossPaymentException("연결 실패", false);
		}
		return Optional.ofNullable(payments.get(orderId));
	}

	private TossPayment record(TossPayment payment) {
		payments.put(payment.orderId(), payment);
		return payment;
	}
}
