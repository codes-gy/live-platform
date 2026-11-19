package com.gy.live.broadcast.thumbnail;

import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
			HttpServletRequest request) throws IOException {
		// 본문을 다 읽기 전에 크기부터 본다. 길이를 밝히지 않은 본문도 한도 + 1바이트까지만 읽는다
		long declared = request.getContentLengthLong();
		if (declared > BroadcastThumbnailService.MAX_BYTES) {
			throw new BusinessException(ErrorCode.INVALID_THUMBNAIL);
		}
		byte[] image = request.getInputStream().readNBytes(BroadcastThumbnailService.MAX_BYTES + 1);
		thumbnailService.upload(loginUser.id(), broadcastId, image);
		return ResponseEntity.noContent().build();
	}

	/** 로그인 없이. 목록 카드가 주소에 갱신 시각(thumbnailUpdatedAt)을 붙여 부르므로 주소가 같으면 내용도 같다. */
	@GetMapping
	public ResponseEntity<byte[]> get(@PathVariable Long broadcastId) {
		BroadcastThumbnail thumbnail = thumbnailService.get(broadcastId);
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
				.lastModified(thumbnail.getUpdatedAt())
				.body(thumbnail.getImage());
	}
}
