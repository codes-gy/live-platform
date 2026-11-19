package com.gy.live.donation;

import com.gy.live.donation.dto.DonationDtos.ConfirmRequest;
import com.gy.live.donation.dto.DonationDtos.DonationResponse;
import com.gy.live.donation.dto.DonationDtos.OrderRequest;
import com.gy.live.donation.dto.DonationDtos.OrderResponse;
import com.gy.live.donation.dto.DonationDtos.SummaryResponse;
import com.gy.live.global.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DonationController {

	private final DonationService donationService;

	public DonationController(DonationService donationService) {
		this.donationService = donationService;
	}

	/** 로그인 사용자. 방송 중인 남의 방송에 후원 주문을 만든다. */
	@PostMapping("/api/broadcasts/{broadcastId}/donations/orders")
	public OrderResponse order(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId,
			@Valid @RequestBody OrderRequest request) {
		return donationService.order(user.id(), broadcastId, request);
	}

	/** 주문한 본인. 결제창 성공 뒤 서버에서 승인 · 적립한다. 같은 요청이 반복돼도 한 번만 적립된다. */
	@PostMapping("/api/donations/confirm")
	public DonationResponse confirm(@AuthenticationPrincipal LoginUser user,
			@Valid @RequestBody ConfirmRequest request) {
		return donationService.confirm(user.id(), request);
	}

	/** 누구나. 방송별 후원 합계 · 최근 내역. */
	@GetMapping("/api/broadcasts/{broadcastId}/donations")
	public SummaryResponse summary(@PathVariable Long broadcastId) {
		return donationService.summary(broadcastId);
	}
}
