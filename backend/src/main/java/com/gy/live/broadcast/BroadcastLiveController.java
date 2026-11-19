package com.gy.live.broadcast;

import com.gy.live.broadcast.dto.BroadcastResponse;
import com.gy.live.broadcast.dto.LiveAccessResponse;
import com.gy.live.global.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broadcasts/{broadcastId}")
public class BroadcastLiveController {

	private final BroadcastLiveService liveService;

	public BroadcastLiveController(BroadcastLiveService liveService) {
		this.liveService = liveService;
	}

	/** 소유자만. 방송을 시작하고 송출 토큰을 받는다. */
	@PostMapping("/start")
	public LiveAccessResponse start(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId) {
		return liveService.start(user, broadcastId);
	}

	/** 소유자만, 방송 중. 재접속용 송출 토큰. */
	@PostMapping("/publisher-token")
	public LiveAccessResponse publisherToken(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId) {
		return liveService.publisherToken(user, broadcastId);
	}

	/** 소유자만. 방송을 끝내고 송출을 끊는다. */
	@PostMapping("/end")
	public BroadcastResponse end(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId) {
		return liveService.end(user, broadcastId);
	}

	/** 누구나(로그인 없이도), 방송 중. 시청 토큰. 로그인했으면 닉네임이 이름이 된다. */
	@PostMapping("/viewer-token")
	public LiveAccessResponse viewerToken(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId) {
		return liveService.viewerToken(user, broadcastId);
	}
}
