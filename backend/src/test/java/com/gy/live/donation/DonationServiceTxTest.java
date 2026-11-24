package com.gy.live.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.donation.dto.DonationDtos.ConfirmRequest;
import com.gy.live.donation.toss.TossPaymentsClient;
import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 승인 흐름이 토스 호출을 트랜잭션(행 잠금) 밖에서 하는지 확인하는 단위 테스트(Docker 없이 돈다).
 * DB를 포함한 전체 흐름은 DonationControllerTest가 확인한다.
 */
class DonationServiceTxTest {

	private final CountingTxManager txManager = new CountingTxManager();
	private final DonationRepository repository = mock(DonationRepository.class);
	private Donation order;
	private DonationService service;
	private TossPaymentsClient toss;

	@BeforeEach
	void setUp() {
		Broadcast broadcast = mock(Broadcast.class);
		when(broadcast.getId()).thenReturn(10L);
		User donor = mock(User.class);
		when(donor.getId()).thenReturn(1L);
		when(donor.getNickname()).thenReturn("후원자");
		order = new Donation(broadcast, donor, 1000, null);
		when(repository.findForUpdateByOrderId(order.getOrderId())).thenReturn(Optional.of(order));
		when(repository.findConfirmingOrderIds(any(), any())).thenAnswer(inv -> List.of(order.getOrderId()));
	}

	private void service(TossPaymentsClient client) {
		this.toss = client;
		AppProperties props = new AppProperties(null, null, null, null,
				new AppProperties.Toss("test_ck", "test_sk"), null, null, null);
		service = new DonationService(repository, mock(BroadcastRepository.class), mock(UserRepository.class), toss,
				props, new TransactionTemplate(txManager));
	}

	private ConfirmRequest request() {
		return new ConfirmRequest("pk-1", order.getOrderId(), 1000);
	}

	@Test
	void 승인_성공_토스는_트랜잭션_밖에서_부르고_전후로_짧은_트랜잭션_두_번() {
		service(new AssertingToss(txManager, Mode.APPROVE));

		service.confirm(1L, request());

		assertThat(order.getStatus()).isEqualTo(DonationStatus.DONE);
		assertThat(order.getConfirmRequestedAt()).isNull();
		assertThat(txManager.commits.get()).isEqualTo(2);
	}

	@Test
	void 거절이면_PENDING으로_되돌린다() {
		service(new AssertingToss(txManager, Mode.REJECT));

		assertThatThrownBy(() -> service.confirm(1L, request()))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.PAYMENT_REJECTED);
		assertThat(order.getStatus()).isEqualTo(DonationStatus.PENDING);
		assertThat(order.getPaymentKey()).isNull();
	}

	@Test
	void 시간_초과면_CONFIRMING으로_남기고_정리_작업이_조회로_적립한다() {
		AssertingToss client = new AssertingToss(txManager, Mode.TIMEOUT);
		service(client);

		assertThatThrownBy(() -> service.confirm(1L, request()))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.PAYMENT_PENDING);
		assertThat(order.getStatus()).isEqualTo(DonationStatus.CONFIRMING);
		assertThat(order.getConfirmRequestedAt()).isNotNull();

		client.lookup = Optional.of(new TossPaymentsClient.TossPayment("pk-1", order.getOrderId(), "DONE", 1000));
		assertThat(service.reconcileConfirming(Duration.ZERO)).isEqualTo(1);

		assertThat(order.getStatus()).isEqualTo(DonationStatus.DONE);
		assertThat(order.getPaymentKey()).isEqualTo("pk-1");
		assertThat(client.lookups.get()).isEqualTo(1);
	}

	@Test
	void 정리_작업은_진행_중인_결제는_그대로_둔다() {
		AssertingToss client = new AssertingToss(txManager, Mode.TIMEOUT);
		service(client);
		assertThatThrownBy(() -> service.confirm(1L, request())).isInstanceOf(BusinessException.class);

		client.lookup = Optional.of(new TossPaymentsClient.TossPayment("pk-1", order.getOrderId(), "IN_PROGRESS", 1000));
		assertThat(service.reconcileConfirming(Duration.ZERO)).isZero();

		assertThat(order.getStatus()).isEqualTo(DonationStatus.CONFIRMING);
	}

	enum Mode { APPROVE, REJECT, TIMEOUT }

	/** 토스 호출이 트랜잭션 안에서 일어나면 실패시킨다. */
	static final class AssertingToss implements TossPaymentsClient {

		private final CountingTxManager txManager;
		private final Mode mode;
		volatile Optional<TossPayment> lookup = Optional.empty();
		final AtomicInteger lookups = new AtomicInteger();

		AssertingToss(CountingTxManager txManager, Mode mode) {
			this.txManager = txManager;
			this.mode = mode;
		}

		@Override
		public TossPayment confirm(String paymentKey, String orderId, long amount) {
			assertThat(txManager.active).as("토스 승인은 트랜잭션 밖에서 불러야 한다").isZero();
			return switch (mode) {
				case APPROVE -> new TossPayment(paymentKey, orderId, "DONE", amount);
				case REJECT -> throw new TossPaymentException("거절", true);
				case TIMEOUT -> throw new TossPaymentException("읽기 시간 초과", false);
			};
		}

		@Override
		public Optional<TossPayment> findByOrderId(String orderId) {
			assertThat(txManager.active).as("토스 조회는 트랜잭션 밖에서 불러야 한다").isZero();
			lookups.incrementAndGet();
			return lookup;
		}
	}

	/** 실제 DB 없이 트랜잭션 시작 · 커밋 횟수만 센다. */
	static final class CountingTxManager extends AbstractPlatformTransactionManager {

		volatile int active;
		final AtomicInteger commits = new AtomicInteger();

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			active++;
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
			active--;
			commits.incrementAndGet();
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			active--;
		}
	}
}
