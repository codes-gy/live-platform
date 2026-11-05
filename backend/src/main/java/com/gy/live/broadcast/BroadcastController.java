package com.gy.live.broadcast;

import com.gy.live.broadcast.dto.BroadcastInfoRequest;
import com.gy.live.broadcast.dto.BroadcastResponse;
import com.gy.live.global.security.LoginUser;
import com.gy.live.global.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broadcasts")
public class BroadcastController {

	private final BroadcastService broadcastService;

	public BroadcastController(BroadcastService broadcastService) {
		this.broadcastService = broadcastService;
	}

	/** 로그인한 사용자만. 등록한 사람이 방송 소유자가 된다. */
	@PostMapping
	public ResponseEntity<BroadcastResponse> create(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody BroadcastInfoRequest request) {
		BroadcastResponse created = broadcastService.create(loginUser.id(), request);
		return ResponseEntity.created(URI.create("/api/broadcasts/" + created.id())).body(created);
	}

	/** 로그인 없이 조회. status로 방송 중 · 종료 등을 거르고, ownerId로 특정 사용자의 방송만 본다. 최신순. */
	@GetMapping
	public PageResponse<BroadcastResponse> search(
			@RequestParam(required = false) BroadcastStatus status,
			@RequestParam(required = false) Long ownerId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return broadcastService.search(status, ownerId, page, size);
	}

	@GetMapping("/{broadcastId}")
	public BroadcastResponse get(@PathVariable Long broadcastId) {
		return broadcastService.get(broadcastId);
	}

	/** 소유자만. */
	@PatchMapping("/{broadcastId}")
	public BroadcastResponse update(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long broadcastId,
			@Valid @RequestBody BroadcastInfoRequest request) {
		return broadcastService.update(loginUser.id(), broadcastId, request);
	}

	/** 소유자만. 방송 중에는 먼저 종료해야 한다. */
	@DeleteMapping("/{broadcastId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long broadcastId) {
		broadcastService.delete(loginUser.id(), broadcastId);
		return ResponseEntity.noContent().build();
	}
}
