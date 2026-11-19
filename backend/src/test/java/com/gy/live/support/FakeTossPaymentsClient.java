package com.gy.live.support;

import com.gy.live.donation.toss.TossPaymentsClient;
import java.util.concurrent.atomic.AtomicInteger;

/** 토스 승인 API 대신. 기본은 요청대로 승인하고, 거절 · 연결 실패 · 다른 금액 응답을 흉내 낼 수 있다. */
public class FakeTossPaymentsClient implements TossPaymentsClient {

	public enum Mode { APPROVE, REJECT, UNAVAILABLE, WRONG_AMOUNT }

	private volatile Mode mode = Mode.APPROVE;
	private final AtomicInteger calls = new AtomicInteger();

	public void reset() {
		mode = Mode.APPROVE;
		calls.set(0);
	}

	public void mode(Mode mode) {
		this.mode = mode;
	}

	public int calls() {
		return calls.get();
	}

	@Override
	public TossPayment confirm(String paymentKey, String orderId, long amount) {
		calls.incrementAndGet();
		return switch (mode) {
			case APPROVE -> new TossPayment(paymentKey, orderId, "DONE", amount);
			case WRONG_AMOUNT -> new TossPayment(paymentKey, orderId, "DONE", amount + 1);
			case REJECT -> throw new TossPaymentException("거절", true);
			case UNAVAILABLE -> throw new TossPaymentException("연결 실패", false);
		};
	}
}
