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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DonationService {

	private static final Logger log = LoggerFactory.getLogger(DonationService.class);

	static final int RECENT_LIMIT = 50;
	/**
	 * 승인 요청 뒤 이 시간 안에는 응답을 기다리는 중으로 보고, 같은 주문의 다른 요청이 토스를 다시 부르지 않는다.
	 * HttpTossPaymentsClient의 연결 5초 + 읽기 30초보다 길게 잡는다. 정리 작업도 이보다 오래된 주문만 본다.
	 */
	static final Duration CONFIRM_IN_FLIGHT = Duration.ofMinutes(1);
	private static final int RECONCILE_BATCH = 50;

	private final DonationRepository donationRepository;
	private final BroadcastRepository broadcastRepository;
	private final UserRepository userRepository;
	private final TossPaymentsClient toss;
	private final AppProperties.Toss tossProps;
	private final TransactionTemplate tx;

	public DonationService(DonationRepository donationRepository, BroadcastRepository broadcastRepository,
			UserRepository userRepository, TossPaymentsClient toss, AppProperties props, TransactionTemplate tx) {
		this.donationRepository = donationRepository;
		this.broadcastRepository = broadcastRepository;
		this.userRepository = userRepository;
		this.toss = toss;
		this.tossProps = props.toss();
		this.tx = tx;
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
	 *
	 * <p>토스 호출(최대 수십 초)이 DB 트랜잭션 · 행 잠금 · 커넥션을 붙잡지 않도록 세 단계로 나눈다.
	 * <ol>
	 *   <li>짧은 트랜잭션: 행을 잠그고 확인한 뒤 CONFIRMING으로 표시하고 커밋한다.</li>
	 *   <li>트랜잭션 밖에서 토스 승인을 부른다.</li>
	 *   <li>짧은 트랜잭션: 다시 잠그고 결과를 반영한다(DONE, 거절이면 PENDING으로 되돌린다).</li>
	 * </ol>
	 * 시간 초과 · 연결 끊김처럼 결과를 모르면 CONFIRMING으로 남기고 {@link #reconcileConfirming}이 토스에 조회해 마무리한다.
	 */
	public DonationResponse confirm(Long userId, ConfirmRequest request) {
		checkConfigured();
		// 1) 확인 · 표시
		ConfirmStart start = tx.execute(status -> beginConfirm(userId, request));
		if (start.alreadyDone() != null) {
			return start.alreadyDone();
		}

		// 2) 토스 승인(트랜잭션 밖). 같은 주문의 재요청은 Idempotency-Key로 토스도 한 번만 처리한다
		TossPayment paid;
		try {
			paid = toss.confirm(request.paymentKey(), request.orderId(), start.amount());
		} catch (TossPaymentException e) {
			if (e.rejected()) {
				tx.executeWithoutResult(status -> revertIfConfirming(request.orderId()));
				throw new BusinessException(ErrorCode.PAYMENT_REJECTED);
			}
			log.warn("토스 승인 결과를 모름. 정리 작업이 조회로 마무리한다 orderId={}: {}", request.orderId(), e.getMessage());
			throw new BusinessException(ErrorCode.PAYMENT_PENDING);
		} catch (RuntimeException e) {
			log.warn("토스 승인 중 예외. 정리 작업이 조회로 마무리한다 orderId={}", request.orderId(), e);
			throw new BusinessException(ErrorCode.PAYMENT_PENDING);
		}

		// 3) 결과 반영
		DonationResponse approved = tx.execute(status -> applyConfirmResult(request.orderId(), paid));
		if (approved == null) {
			throw new BusinessException(ErrorCode.PAYMENT_REJECTED);
		}
		return approved;
	}

	/** 1단계. 이미 같은 결제로 적립됐으면 그 결과를, 아니면 CONFIRMING으로 표시하고 주문 금액을 돌려준다. */
	private ConfirmStart beginConfirm(Long userId, ConfirmRequest request) {
		Donation order = donationRepository.findForUpdateByOrderId(request.orderId())
				.filter(d -> d.isOrderedBy(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.DONATION_NOT_FOUND));
		if (order.isDone()) {
			if (order.getPaymentKey().equals(request.paymentKey())) {
				return ConfirmStart.done(DonationResponse.from(order));
			}
			throw new BusinessException(ErrorCode.DONATION_ALREADY_DONE);
		}
		if (order.getAmount() != request.amount()) {
			throw new BusinessException(ErrorCode.DONATION_AMOUNT_MISMATCH);
		}
		Instant now = Instant.now();
		if (order.isConfirming()) {
			if (!order.getPaymentKey().equals(request.paymentKey())) {
				throw new BusinessException(ErrorCode.DONATION_IN_PROGRESS);
			}
			// 다른 요청이 지금 토스 응답을 기다리는 중이면 토스를 또 부르지 않는다
			if (order.isConfirmInFlight(now, CONFIRM_IN_FLIGHT)) {
				throw new BusinessException(ErrorCode.PAYMENT_PENDING);
			}
			// 그보다 오래됐으면 앞 요청은 결과를 모른 채 끝났다. 같은 결제로 다시 승인을 부른다(토스가 멱등 처리)
		}
		order.startConfirm(request.paymentKey(), now);
		return ConfirmStart.confirm(order.getAmount());
	}

	/** 3단계. 토스가 돌려준 결과도 주문과 같아야 적립한다. 다르면 승인 전으로 되돌리고 null. */
	private DonationResponse applyConfirmResult(String orderId, TossPayment paid) {
		Donation order = donationRepository.findForUpdateByOrderId(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.DONATION_NOT_FOUND));
		if (order.isDone()) {
			// 그 사이 정리 작업이 같은 결제를 반영했다
			return DonationResponse.from(order);
		}
		if (!matches(order, paid)) {
			log.warn("토스 승인 응답이 주문과 다름 orderId={} status={} amount={}", orderId,
					paid == null ? null : paid.status(), paid == null ? null : paid.totalAmount());
			order.revertToPending();
			return null;
		}
		order.approve(paid.paymentKey() != null ? paid.paymentKey() : order.getPaymentKey());
		donationRepository.flush();
		return DonationResponse.from(order);
	}

	private void revertIfConfirming(String orderId) {
		donationRepository.findForUpdateByOrderId(orderId)
				.filter(Donation::isConfirming)
				.ifPresent(Donation::revertToPending);
	}

	/**
	 * 승인을 요청한 지 olderThan이 지났는데 CONFIRMING으로 남은 주문을 토스 결제 조회로 마무리한다.
	 * 승인됨(DONE) → 적립, 결제 없음 · 취소 · 실패 · 만료 → 승인 전(PENDING)으로 되돌림, 그 밖(진행 중 · 조회 실패) → 다음 주기에 다시.
	 * 토스 조회는 트랜잭션 밖에서 하고, 반영은 행을 잠그고 한다. 마무리한 건수를 돌려준다.
	 */
	public int reconcileConfirming(Duration olderThan) {
		if (tossProps == null || !tossProps.configured()) {
			return 0;
		}
		List<String> orderIds = donationRepository.findConfirmingOrderIds(Instant.now().minus(olderThan),
				Limit.of(RECONCILE_BATCH));
		int resolved = 0;
		for (String orderId : orderIds) {
			Optional<TossPayment> payment;
			try {
				payment = toss.findByOrderId(orderId);
			} catch (RuntimeException e) {
				log.warn("토스 결제 조회 실패. 다음 주기에 다시 orderId={}: {}", orderId, e.getMessage());
				continue;
			}
			if (Boolean.TRUE.equals(tx.execute(status -> applyLookup(orderId, payment)))) {
				resolved++;
			}
		}
		return resolved;
	}

	/** 조회 결과를 반영했으면 true, 아직 결론을 못 내면 false. */
	private boolean applyLookup(String orderId, Optional<TossPayment> payment) {
		Donation order = donationRepository.findForUpdateByOrderId(orderId).orElse(null);
		if (order == null || !order.isConfirming()) {
			return false;
		}
		if (payment.isEmpty()) {
			log.info("토스에 결제가 없어 승인 전으로 되돌림 orderId={}", orderId);
			order.revertToPending();
			return true;
		}
		TossPayment paid = payment.get();
		if (paid.isDone()) {
			if (!matches(order, paid)) {
				log.error("토스 결제가 주문과 다름(적립하지 않음, 확인 필요) orderId={} amount={} paid={}", orderId,
						order.getAmount(), paid.totalAmount());
				order.revertToPending();
				return true;
			}
			order.approve(paid.paymentKey() != null ? paid.paymentKey() : order.getPaymentKey());
			log.info("결과를 모르던 후원을 토스 조회로 적립 orderId={}", orderId);
			return true;
		}
		if (paid.isClosedWithoutApproval()) {
			log.info("토스 결제가 승인되지 않고 끝나 승인 전으로 되돌림 orderId={} status={}", orderId, paid.status());
			order.revertToPending();
			return true;
		}
		// READY · IN_PROGRESS 등: 토스가 아직 결론을 내지 않았다(승인되지 않은 결제는 토스가 곧 만료시킨다)
		return false;
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

	private static boolean matches(Donation order, TossPayment paid) {
		return paid != null && paid.isDone() && paid.totalAmount() == order.getAmount()
				&& order.getOrderId().equals(paid.orderId());
	}

	private void checkConfigured() {
		if (tossProps == null || !tossProps.configured()) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_CONFIGURED);
		}
	}

	/** 1단계 결과: 이미 적립된 응답, 또는 토스에 승인을 요청할 주문 금액. */
	private record ConfirmStart(DonationResponse alreadyDone, int amount) {

		static ConfirmStart done(DonationResponse response) {
			return new ConfirmStart(response, 0);
		}

		static ConfirmStart confirm(int amount) {
			return new ConfirmStart(null, amount);
		}
	}
}
