package com.gy.live.donation;

public enum DonationStatus {
	/** 주문만 만들었고 결제 승인 전 */
	PENDING,
	/** 토스 승인을 요청했고 결과를 아직 반영하지 못함. 응답이 끊기면 DonationReconcileScheduler가 토스에 조회해 마무리한다 */
	CONFIRMING,
	/** 토스 승인 완료 · 적립됨 */
	DONE
}
