package com.gy.live.donation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 승인 결과를 모른 채 CONFIRMING으로 남은 후원을 주기적으로 토스 결제 조회로 마무리한다.
 * 로직은 {@link DonationService#reconcileConfirming}. 반영은 행 잠금 안에서 하므로 서버가 여러 대여도 한 번만 적립된다.
 */
@Component
class DonationReconcileScheduler {

	private final DonationService donationService;

	DonationReconcileScheduler(DonationService donationService) {
		this.donationService = donationService;
	}

	@Scheduled(fixedDelayString = "${app.toss.reconcile-interval:1m}",
			initialDelayString = "${app.toss.reconcile-interval:1m}")
	void run() {
		donationService.reconcileConfirming(DonationService.CONFIRM_IN_FLIGHT);
	}
}
