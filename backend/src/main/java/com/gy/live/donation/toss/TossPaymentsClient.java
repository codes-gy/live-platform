package com.gy.live.donation.toss;

import java.util.Optional;

/** 토스페이먼츠 결제 승인 · 조회. 테스트에서는 가짜로 바꾼다. */
public interface TossPaymentsClient {

	/**
	 * 결제를 승인한다. 토스가 거절하면(4xx) {@link TossPaymentException} rejected=true,
	 * 연결 실패 · 시간 초과 · 토스 서버 오류면 rejected=false(토스가 승인했는지 알 수 없다).
	 */
	TossPayment confirm(String paymentKey, String orderId, long amount);

	/**
	 * 주문 번호로 결제를 조회한다(GET /v1/payments/orders/{orderId}). 토스에 그 주문의 결제가 없으면 빈 값.
	 * 연결 실패 · 토스 오류면 {@link TossPaymentException}.
	 */
	Optional<TossPayment> findByOrderId(String orderId);

	/** 토스 결제 응답(Payment 객체) 중 대조에 쓰는 값. */
	record TossPayment(String paymentKey, String orderId, String status, long totalAmount) {

		public boolean isDone() {
			return "DONE".equals(status);
		}

		/** 승인되지 않은 채 끝난 결제(취소 · 승인 실패 · 만료). 이 주문은 적립하지 않는다. */
		public boolean isClosedWithoutApproval() {
			return switch (status == null ? "" : status) {
				case "CANCELED", "PARTIAL_CANCELED", "ABORTED", "EXPIRED" -> true;
				default -> false;
			};
		}
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
