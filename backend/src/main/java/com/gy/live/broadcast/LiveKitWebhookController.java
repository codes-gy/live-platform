package com.gy.live.broadcast;

import com.gy.live.livekit.LiveKitWebhookVerifier;
import livekit.LivekitWebhook.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** LiveKit Cloud → 서버. 서명을 확인한 이벤트만 처리한다. 같은 이벤트가 다시 와도 결과가 같다(LIVE일 때만 종료). */
@RestController
public class LiveKitWebhookController {

	private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

	private final LiveKitWebhookVerifier verifier;
	private final BroadcastLiveService liveService;

	public LiveKitWebhookController(LiveKitWebhookVerifier verifier, BroadcastLiveService liveService) {
		this.verifier = verifier;
		this.liveService = liveService;
	}

	@PostMapping(value = "/api/livekit/webhook", consumes = {"application/webhook+json", "application/json"})
	public ResponseEntity<Void> receive(@RequestBody String body,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		WebhookEvent event = verifier.verify(body, authorization);
		log.debug("LiveKit 웹훅: event={}, room={}", event.getEvent(), event.getRoom().getName());
		switch (event.getEvent()) {
			case "room_finished" -> liveService.onRoomFinished(event.getRoom().getName());
			case "participant_left" ->
					liveService.onParticipantLeft(event.getRoom().getName(), event.getParticipant().getIdentity());
			default -> {
			}
		}
		return ResponseEntity.ok().build();
	}
}
