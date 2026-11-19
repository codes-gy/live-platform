package com.gy.live.broadcast;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 주기적으로 LIVE로 남은 방송을 정리한다. 로직은 {@link BroadcastLiveService#reconcileLive()}. */
@Component
class LiveReconcileScheduler {

	private final BroadcastLiveService liveService;

	LiveReconcileScheduler(BroadcastLiveService liveService) {
		this.liveService = liveService;
	}

	// 첫 실행도 주기만큼 기다린다: 기동 직후 방송자가 재접속하기 전에 확인하지 않게
	@Scheduled(fixedDelayString = "${app.livekit.reconcile-interval:1m}",
			initialDelayString = "${app.livekit.reconcile-interval:1m}")
	void run() {
		liveService.reconcileLive();
	}
}
