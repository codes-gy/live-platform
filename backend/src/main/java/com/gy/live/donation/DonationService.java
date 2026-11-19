package com.gy.live.donation;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.donation.dto.DonationDtos.ConfirmRequest;
import com.gy.live.donation.dto.DonationDtos.DonationResponse;
import com.gy.live.donation.dto.DonationDtos.OrderRequest;
import com.gy.live.donation.dto.DonationDtos.OrderResponse;
import com.gy.live.donation.dto.DonationDtos.SummaryResponse;
import com.gy.live.donation.toss.TossPaymentsClient;
import com.gy.live.donation.toss.TossPaymentsClient.TossPayment;
import com.gy.live.donation.toss.TossPaymentsClient.TossPaymentException;
import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonationService {

	static final int RECENT_LIMIT = 50;

	private final DonationRepository donationRepository;
	private final BroadcastRepository broadcastRepository;
	private final UserRepository userRepository;
	private final TossPaymentsClient toss;
	private final AppProperties.Toss tossProps;

	public DonationService(DonationRepository donationRepository, BroadcastRepository broadcastRepository,
			UserRepository userRepository, TossPaymentsClient toss, AppProperties props) {
		this.donationRepository = donationRepository;
		this.broadcastRepository = broadcastRepository;
		this.userRepository = userRepository;
		this.toss = toss;
		this.tossProps = props.toss();
	}

	/** 로그인 사용자, 방송 중인 남의 방송에만. 금액 · 대상은 여기서 저장한 값만 믿는다. */
	@Transactional
	public OrderResponse order(Long userId, Long broadcastId, OrderRequest request) {
		checkConfigured();
		Broadcast broadcast = broadcastRepository.findWithOwnerById(broadcastId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
		broadcast.checkLive();
		if (broadcast.isOwnedBy(userId)) {
			throw new BusinessException(ErrorCode.CANNOT_DONATE_SELF);
		}
		User donor = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		Donation order = donationRepository.save(
				new Donation(broadcast, donor, request.amount(), request.normalizedMessage()));
		String orderName = broadcast.getOwner().getNickname() + "님께 후원";
		return new OrderResponse(order.getOrderId(), order.getAmount(), orderName,
				tossProps.clientKey(), "user-" + userId);
	}

	/**
	 * 주문한 본인만. 주문과 금액을 대조한 뒤 토스 승인 API를 부르고 적립한다.
	 * 이미 같은 결제로 적립된 주문이면 토스를 다시 부르지 않고 그 결과를 돌려준다(중복 통지에도 1회 적립).
	 */
	@Transactional
	public DonationResponse confirm(Long userId, ConfirmRequest request) {
		checkConfigured();
		Donation order = donationRepository.findForUpdateByOrderId(request.orderId())
				.filter(d -> d.isOrderedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.DONATION_NOT_FOUND));
		if (order.isDone()) {
			if (order.getPaymentKey().equals(request.paymentKey())) {
				return DonationResponse.from(order);
			}
			throw new BusinessException(ErrorCode.DONATION_ALREADY_DONE);
		}
		if (order.getAmount() != request.amount()) {
			throw new BusinessException(ErrorCode.DONATION_AMOUNT_MISMATCH);
		}
		TossPayment paid;
		try {
			paid = toss.confirm(request.paymentKey(), order.getOrderId(), order.getAmount());
		} catch (TossPaymentException e) {
			throw new BusinessException(e.rejected() ? ErrorCode.PAYMENT_REJECTED : ErrorCode.PAYMENT_UNAVAILABLE);
		}
		// 토스가 돌려준 결과도 주문과 같아야 적립한다
		if (paid == null || !"DONE".equals(paid.status()) || paid.totalAmount() != order.getAmount()
				|| !order.getOrderId().equals(paid.orderId())) {
			throw new BusinessException(ErrorCode.PAYMENT_REJECTED);
		}
		order.approve(request.paymentKey());
		donationRepository.flush();
		return DonationResponse.from(order);
	}

	/** 누구나. 적립된 후원의 합계와 최근 내역. */
	@Transactional(readOnly = true)
	public SummaryResponse summary(Long broadcastId) {
		if (!broadcastRepository.existsById(broadcastId)) {
			throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
		}
		List<DonationResponse> items = donationRepository.findDone(broadcastId, Limit.of(RECENT_LIMIT)).stream()
				.map(DonationResponse::from).toList();
		return new SummaryResponse(donationRepository.sumDone(broadcastId), donationRepository.countDone(broadcastId),
				items);
	}

	private void checkConfigured() {
		if (tossProps == null || !tossProps.configured()) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED);
		}
	}
}
