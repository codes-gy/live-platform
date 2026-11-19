package com.gy.live.broadcast.thumbnail;

import com.gy.live.global.security.LoginUser;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/broadcasts/{broadcastId}/thumbnail")
public class BroadcastThumbnailController {

	private final BroadcastThumbnailService thumbnailService;

	public BroadcastThumbnailController(BroadcastThumbnailService thumbnailService) {
		this.thumbnailService = thumbnailService;
	}

	/** 소유자만, 방송 중에만. 본문은 JPEG 바이트. */
	@PutMapping(consumes = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<Void> upload(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long broadcastId,
			@RequestBody byte[] image) {
		thumbnailService.upload(loginUser.id(), broadcastId, image);
		return ResponseEntity.noContent().build();
	}

	/** 로그인 없이. 목록 카드가 주소에 갱신 시각을 붙여 부르므로 짧게 캐시해도 새 화면이 보인다. */
	@GetMapping
	public ResponseEntity<byte[]> get(@PathVariable Long broadcastId) {
		BroadcastThumbnail thumbnail = thumbnailService.get(broadcastId);
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
				.lastModified(thumbnail.getUpdatedAt())
				.body(thumbnail.getImage());
	}
}
