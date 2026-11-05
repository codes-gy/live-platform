package com.gy.live.livekit;

import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import org.springframework.stereotype.Component;

/**
 * LiveKit 웹훅은 Authorization 헤더에 본문 SHA-256을 담은 JWT(API 비밀키로 서명)를 보낸다.
 * 서명과 본문 해시가 모두 맞을 때만 이벤트로 받아들인다. 누구나 이 주소로 "방 종료"를 보내 방송을 끝내지 못하게 한다.
 */
@Component
public class LiveKitWebhookVerifier {

	private final AppProperties.Livekit livekit;

	public LiveKitWebhookVerifier(AppProperties properties) {
		this.livekit = properties.livekit();
	}

	public WebhookEvent verify(String body, String authorization) {
		if (!livekit.configured()) {
			throw new BusinessException(ErrorCode.LIVEKIT_NOT_CONFIGURED);
		}
		if (authorization == null || authorization.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_WEBHOOK);
		}
		try {
			return new WebhookReceiver(livekit.apiKey(), livekit.apiSecret()).receive(body, authorization);
		} catch (RuntimeException e) {
			throw new BusinessException(ErrorCode.INVALID_WEBHOOK);
		}
	}
}
