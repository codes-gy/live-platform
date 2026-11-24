package com.gy.live.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.donation.toss.TossPaymentsClient.TossPayment;
import com.gy.live.support.AuthSupport;
import com.gy.live.support.FakeLiveKitRoomClient;
import com.gy.live.support.FakeTossPaymentsClient;
import com.gy.live.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class DonationControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeLiveKitRoomClient liveKit;

	@Autowired
	FakeTossPaymentsClient toss;

	@Autowired
	DonationService donationService;

	@Autowired
	JdbcTemplate jdbc;

	Cookie owner;
	Cookie donor;
	Cookie other;

	@BeforeEach
	void setUp() throws Exception {
		liveKit.reset();
		toss.reset();
		owner = AuthSupport.signupNewUser(mockMvc, "방송주인");
		donor = AuthSupport.signupNewUser(mockMvc, "후원자");
		other = AuthSupport.signupNewUser(mockMvc, "다른사람");
	}

	private long broadcast(boolean live) throws Exception {
		String body = mockMvc.perform(post("/api/broadcasts").cookie(owner)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"후원 방송"}
								"""))
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		if (live) {
			mockMvc.perform(post("/api/broadcasts/{id}/start", id).cookie(owner)).andExpect(status().isOk());
		}
		return id;
	}

	private ResultActions order(Cookie who, long broadcastId, int amount) throws Exception {
		var req = post("/api/broadcasts/{id}/donations/orders", broadcastId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":%d,"message":"응원해요"}
						""".formatted(amount));
		return mockMvc.perform(who == null ? req : req.cookie(who));
	}

	private String orderId(long broadcastId, int amount) throws Exception {
		String body = order(donor, broadcastId, amount).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.orderId");
	}

	private ResultActions confirm(Cookie who, String paymentKey, String orderId, int amount) throws Exception {
		return mockMvc.perform(post("/api/donations/confirm").cookie(who)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"paymentKey":"%s","orderId":"%s","amount":%d}
						""".formatted(paymentKey, orderId, amount)));
	}

	@Test
	@DisplayName("주문 → 승인 → 방송별 합계 · 내역")
	void orderConfirmSummary() throws Exception {
		long id = broadcast(true);
		order(donor, id, 5000).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(5000))
				.andExpect(jsonPath("$.clientKey").value("test_ck_fake"))
				.andExpect(jsonPath("$.orderName").value("방송주인님께 후원"));
		String orderId = orderId(id, 3000);

		confirm(donor, "pk-" + UUID.randomUUID(), orderId, 3000).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(3000))
				.andExpect(jsonPath("$.donor.nickname").value("후원자"))
				.andExpect(jsonPath("$.message").value("응원해요"));

		// 승인 전 주문(5000)은 합계에 들어가지 않는다. 비로그인도 본다
		mockMvc.perform(get("/api/broadcasts/{id}/donations", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAmount").value(3000))
				.andExpect(jsonPath("$.count").value(1))
				.andExpect(jsonPath("$.items[0].amount").value(3000));
	}

	@Test
	@DisplayName("같은 승인 통지가 반복돼도 한 번만 적립하고 토스는 한 번만 부른다")
	void duplicateConfirmIsIdempotent() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 2000);
		String paymentKey = "pk-" + UUID.randomUUID();

		confirm(donor, paymentKey, orderId, 2000).andExpect(status().isOk());
		confirm(donor, paymentKey, orderId, 2000).andExpect(status().isOk());

		assertThat(toss.calls()).isEqualTo(1);
		mockMvc.perform(get("/api/broadcasts/{id}/donations", id))
				.andExpect(jsonPath("$.totalAmount").value(2000))
				.andExpect(jsonPath("$.count").value(1));

		// 같은 주문을 다른 결제로 다시 승인하려 하면 거절
		confirm(donor, "pk-other", orderId, 2000).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DONATION_ALREADY_DONE"));
	}

	@Test
	@DisplayName("금액이 주문과 다르면 토스를 부르지 않고 거절")
	void amountMismatch() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 5000);

		confirm(donor, "pk-1", orderId, 100).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DONATION_AMOUNT_MISMATCH"));
		assertThat(toss.calls()).isZero();
	}

	@Test
	@DisplayName("토스 응답 금액이 주문과 다르거나 거절 · 연결 실패면 적립하지 않는다")
	void tossFailures() throws Exception {
		long id = broadcast(true);

		toss.mode(FakeTossPaymentsClient.Mode.WRONG_AMOUNT);
		confirm(donor, "pk-a", orderId(id, 1000), 1000).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));
		toss.mode(FakeTossPaymentsClient.Mode.REJECT);
		confirm(donor, "pk-b", orderId(id, 1000), 1000).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));
		// 연결 실패는 토스가 승인했는지 알 수 없다: 적립하지 않고 결과 확인 대기(504)로 남긴다
		toss.mode(FakeTossPaymentsClient.Mode.UNAVAILABLE);
		confirm(donor, "pk-c", orderId(id, 1000), 1000).andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.code").value("PAYMENT_PENDING"));

		mockMvc.perform(get("/api/broadcasts/{id}/donations", id))
				.andExpect(jsonPath("$.totalAmount").value(0))
				.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	@DisplayName("승인 성공: DONE으로 적립하고 승인 중 표시를 지운다")
	void confirmSuccessClearsConfirming() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 1000);
		String paymentKey = "pk-" + UUID.randomUUID();

		confirm(donor, paymentKey, orderId, 1000).andExpect(status().isOk());

		assertThat(row(orderId)).containsEntry("status", "DONE").containsEntry("payment_key", paymentKey)
				.containsEntry("confirm_requested_at", null);
	}

	@Test
	@DisplayName("토스가 거절하면 승인 전(PENDING)으로 되돌린다")
	void rejectedRevertsToPending() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 1000);

		toss.mode(FakeTossPaymentsClient.Mode.REJECT);
		confirm(donor, "pk-r", orderId, 1000).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));

		assertThat(row(orderId)).containsEntry("status", "PENDING").containsEntry("payment_key", null);
	}

	@Test
	@DisplayName("승인은 됐는데 응답이 끊기면 CONFIRMING으로 남고, 정리 작업이 토스 조회로 적립한다")
	void timeoutThenReconcileApproves() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 4000);
		String paymentKey = "pk-" + UUID.randomUUID();

		toss.mode(FakeTossPaymentsClient.Mode.TIMEOUT_AFTER_APPROVE);
		confirm(donor, paymentKey, orderId, 4000).andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.code").value("PAYMENT_PENDING"));
		assertThat(row(orderId)).containsEntry("status", "CONFIRMING");
		mockMvc.perform(get("/api/broadcasts/{id}/donations", id)).andExpect(jsonPath("$.count").value(0));

		// 앞 요청의 응답을 기다리는 시간 안에 다시 오면 토스를 또 부르지 않는다
		toss.mode(FakeTossPaymentsClient.Mode.APPROVE);
		confirm(donor, paymentKey, orderId, 4000).andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.code").value("PAYMENT_PENDING"));
		// 다른 결제로 같은 주문을 승인하려 하면 거절
		confirm(donor, "pk-other", orderId, 4000).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DONATION_IN_PROGRESS"));
		assertThat(toss.calls()).isEqualTo(1);

		donationService.reconcileConfirming(Duration.ZERO);

		assertThat(row(orderId)).containsEntry("status", "DONE").containsEntry("payment_key", paymentKey);
		assertThat(toss.lookups()).isGreaterThanOrEqualTo(1);
		mockMvc.perform(get("/api/broadcasts/{id}/donations", id))
				.andExpect(jsonPath("$.totalAmount").value(4000))
				.andExpect(jsonPath("$.count").value(1));
		// 결제창 복귀 화면을 새로고침해도 토스를 다시 부르지 않고 적립 결과를 돌려준다
		confirm(donor, paymentKey, orderId, 4000).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(4000));
		assertThat(toss.calls()).isEqualTo(1);
	}

	@Test
	@DisplayName("정리 작업: 토스에 결제가 없거나 승인되지 않고 끝났으면 PENDING으로 되돌리고, 조회 실패면 그대로 둔다")
	void reconcileRevertsOrKeeps() throws Exception {
		long id = broadcast(true);
		toss.mode(FakeTossPaymentsClient.Mode.UNAVAILABLE);
		String notFound = orderId(id, 1000);
		String aborted = orderId(id, 1000);
		confirm(donor, "pk-nf", notFound, 1000).andExpect(status().isGatewayTimeout());
		confirm(donor, "pk-ab", aborted, 1000).andExpect(status().isGatewayTimeout());
		toss.putPayment(new TossPayment("pk-ab", aborted, "ABORTED", 1000));

		// 조회가 실패하면 결론을 내지 않는다
		toss.lookupUnavailable(true);
		donationService.reconcileConfirming(Duration.ZERO);
		assertThat(row(notFound)).containsEntry("status", "CONFIRMING");
		assertThat(row(aborted)).containsEntry("status", "CONFIRMING");

		toss.lookupUnavailable(false);
		donationService.reconcileConfirming(Duration.ZERO);
		assertThat(row(notFound)).containsEntry("status", "PENDING").containsEntry("payment_key", null);
		assertThat(row(aborted)).containsEntry("status", "PENDING").containsEntry("payment_key", null);
		mockMvc.perform(get("/api/broadcasts/{id}/donations", id)).andExpect(jsonPath("$.count").value(0));
	}

	@Test
	@DisplayName("정리 작업은 방금 승인을 요청한(응답 대기 중일 수 있는) 주문은 건드리지 않는다")
	void reconcileSkipsInFlight() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 1000);
		toss.mode(FakeTossPaymentsClient.Mode.TIMEOUT_AFTER_APPROVE);
		confirm(donor, "pk-if", orderId, 1000).andExpect(status().isGatewayTimeout());

		donationService.reconcileConfirming(DonationService.CONFIRM_IN_FLIGHT);

		assertThat(row(orderId)).containsEntry("status", "CONFIRMING");
	}

	private Map<String, Object> row(String orderId) {
		return jdbc.queryForMap("select status, payment_key, confirm_requested_at from donations where order_id = ?",
				orderId);
	}

	@Test
	@DisplayName("남의 주문은 승인할 수 없다")
	void otherUsersOrder() throws Exception {
		long id = broadcast(true);
		String orderId = orderId(id, 1000);

		confirm(other, "pk-x", orderId, 1000).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DONATION_NOT_FOUND"));
		assertThat(toss.calls()).isZero();
	}

	@Test
	@DisplayName("주문: 비로그인 401 · 자기 방송 400 · 방송 전 409 · 금액 범위 400 · 없는 방송 404")
	void orderRules() throws Exception {
		long live = broadcast(true);
		long ready = broadcast(false);

		order(null, live, 1000).andExpect(status().isUnauthorized());
		order(owner, live, 1000).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_DONATE_SELF"));
		order(donor, ready, 1000).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("BROADCAST_NOT_LIVE"));
		order(donor, live, 999).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		order(donor, live, 1_000_001).andExpect(status().isBadRequest());
		order(donor, 999_999L, 1000).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/donations/confirm").contentType(MediaType.APPLICATION_JSON)
						.content("{\"paymentKey\":\"a\",\"orderId\":\"b\",\"amount\":1000}"))
				.andExpect(status().isUnauthorized());
	}
}
