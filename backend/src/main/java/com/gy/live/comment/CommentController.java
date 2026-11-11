package com.gy.live.comment;

import com.gy.live.comment.dto.CommentPageResponse;
import com.gy.live.comment.dto.CommentRequest;
import com.gy.live.comment.dto.CommentResponse;
import com.gy.live.global.security.LoginUser;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/broadcasts/{broadcastId}/comments")
public class CommentController {

	private final CommentService commentService;
	private final CommentStreams commentStreams;

	public CommentController(CommentService commentService, CommentStreams commentStreams) {
		this.commentService = commentService;
		this.commentStreams = commentStreams;
	}

	/** 로그인 사용자, 방송 중일 때만. */
	@PostMapping
	public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal LoginUser user,
			@PathVariable Long broadcastId, @Valid @RequestBody CommentRequest request) {
		CommentResponse created = commentService.create(user.id(), broadcastId, request);
		return ResponseEntity.created(URI.create("/api/broadcasts/" + broadcastId + "/comments/" + created.id()))
				.body(created);
	}

	/** 누구나. 최신순. 처음엔 before 없이, 위로 스크롤하면 응답의 nextBefore를 넘긴다. */
	@GetMapping
	public CommentPageResponse list(@PathVariable Long broadcastId,
			@RequestParam(required = false) Long before,
			@RequestParam(defaultValue = "50") int size) {
		return commentService.list(broadcastId, before, size);
	}

	/**
	 * 누구나. 새 댓글 · 수정 · 삭제 · 방송 종료를 SSE로 받는다.
	 * X-Accel-Buffering: Nginx가 응답을 모아 두지 않고 바로 흘려보내게 한다.
	 */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> stream(@PathVariable Long broadcastId) {
		return ResponseEntity.ok()
				.header("X-Accel-Buffering", "no")
				.body(commentStreams.subscribe(broadcastId));
	}

	/** 작성자만. */
	@PatchMapping("/{commentId}")
	public CommentResponse edit(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId,
			@PathVariable Long commentId, @Valid @RequestBody CommentRequest request) {
		return commentService.edit(user.id(), broadcastId, commentId, request);
	}

	/** 작성자 또는 방송 소유자. */
	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal LoginUser user, @PathVariable Long broadcastId,
			@PathVariable Long commentId) {
		commentService.delete(user.id(), broadcastId, commentId);
		return ResponseEntity.noContent().build();
	}
}
