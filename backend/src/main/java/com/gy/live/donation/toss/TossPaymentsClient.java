package com.gy.live.donation.toss;

/** 토스페이먼츠 결제 승인. 테스트에서는 가짜로 바꾼다. */
public interface TossPaymentsClient {

	/**
	 * 결제를 승인한다. 토스가 거절하면(4xx) {@link TossPaymentException} rejected=true,
	 * 연결 실패 · 토스 서버 오류면 rejected=false.
	 */
	TossPayment confirm(String paymentKey, String orderId, long amount);

	/** 토스 결제 승인 응답 중 대조에 쓰는 값. */
	record TossPayment(String paymentKey, String orderId, String status, long totalAmount) {
	}

	class TossPaymentException extends RuntimeException {

		private final boolean rejected;

		public TossPaymentException(String message, boolean rejected) {
			super(message);
			this.rejected = rejected;
		}

		public boolean rejected() {
			return rejected;
		}
	}
}
