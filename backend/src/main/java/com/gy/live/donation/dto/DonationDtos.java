package com.gy.live.donation.dto;

import com.gy.live.donation.Donation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 도네이션 API 요청 · 응답. */
public final class DonationDtos {

	private DonationDtos() {
	}

	public record OrderRequest(
			@Min(value = 1000, message = "후원 금액은 1,000원 이상입니다.")
			@Max(value = 1_000_000, message = "후원 금액은 1,000,000원 이하입니다.")
			int amount,
			@Size(max = 100, message = "메시지는 100자 이하입니다.")
			String message
	) {

		/** 빈 메시지는 null로 저장한다. */
		public String normalizedMessage() {
			return message == null || message.isBlank() ? null : message.strip();
		}
	}

	/** 결제창을 여는 데 필요한 값. 금액은 서버가 저장한 주문 금액이다. */
	public record OrderResponse(String orderId, int amount, String orderName, String clientKey,
			String customerKey) {
	}

	/** 토스 결제창이 successUrl에 붙여 준 값을 그대로 넘긴다. 서버는 이 값을 믿지 않고 주문과 대조한다. */
	public record ConfirmRequest(
			@NotBlank @Size(max = 200) String paymentKey,
			@NotBlank @Size(max = 64) String orderId,
			int amount
	) {
	}

	public record DonationResponse(Long id, Long broadcastId, Donor donor, int amount, String message,
			Instant approvedAt) {

		public record Donor(Long id, String nickname) {
		}

		public static DonationResponse from(Donation d) {
			return new DonationResponse(d.getId(), d.getBroadcast().getId(),
					new Donor(d.getDonor().getId(), d.getDonor().getNickname()),
					d.getAmount(), d.getMessage(), d.getApprovedAt());
		}
	}

	/** 방송별 후원 합계와 최근 내역. */
	public record SummaryResponse(long totalAmount, long count, List<DonationResponse> items) {
	}
}
