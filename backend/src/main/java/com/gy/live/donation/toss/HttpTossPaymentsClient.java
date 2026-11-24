package com.gy.live.donation.toss;

import com.gy.live.global.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 토스페이먼츠 결제 승인(POST /v1/payments/confirm) · 주문 번호로 조회(GET /v1/payments/orders/{orderId}). 시크릿 키 Basic 인증. */
@Component
public class HttpTossPaymentsClient implements TossPaymentsClient {

	private final RestClient client;

	public HttpTossPaymentsClient(AppProperties props) {
		String secret = props.toss() == null || props.toss().secretKey() == null ? "" : props.toss().secretKey();
		String basic = Base64.getEncoder().encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8));
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.client = RestClient.builder()
				.baseUrl("https://api.tosspayments.com")
				.requestFactory(factory)
				.defaultHeader("Authorization", "Basic " + basic)
				.build();
	}

	@Override
	public TossPayment confirm(String paymentKey, String orderId, long amount) {
		try {
			return client.post()
					.uri("/v1/payments/confirm")
					// 같은 주문의 재요청은 토스도 한 번만 처리한다
					.header("Idempotency-Key", orderId)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
					.retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
						throw new TossPaymentException("토스 결제 승인 거절: " + res.getStatusCode(), true);
					})
					.body(TossPayment.class);
		} catch (TossPaymentException e) {
			throw e;
		} catch (RestClientException e) {
			throw new TossPaymentException("토스 결제 승인 실패: " + e.getMessage(), false);
		}
	}

	@Override
	public Optional<TossPayment> findByOrderId(String orderId) {
		try {
			return Optional.ofNullable(client.get()
					.uri("/v1/payments/orders/{orderId}", orderId)
					.retrieve()
					.body(TossPayment.class));
		} catch (HttpClientErrorException.NotFound e) {
			// NOT_FOUND_PAYMENT: 승인 요청이 토스에 닿지 않았다
			return Optional.empty();
		} catch (RestClientException e) {
			throw new TossPaymentException("토스 결제 조회 실패: " + e.getMessage(), false);
		}
	}
}
