package com.gy.live.broadcast.thumbnail;

import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.security.LoginUser;
import com.gy.live.global.web.JpegImages;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
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

	static final int MAX_BYTES = 300 * 1024;

	private final BroadcastThumbnailService thumbnailService;

	public BroadcastThumbnailController(BroadcastThumbnailService thumbnailService) {
		this.thumbnailService = thumbnailService;
	}

	/** 소유자만, 방송 중에만. 본문은 JPEG 바이트(300KB 이하). */
	@PutMapping(consumes = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<Void> upload(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long broadcastId,
			HttpServletRequest request) throws IOException {
		thumbnailService.upload(loginUser.id(), broadcastId, JpegImages.read(request, MAX_BYTES, ErrorCode.INVALID_THUMBNAIL));
		return ResponseEntity.noContent().build();
	}

	/** 로그인 없이. 방송 응답의 thumbnailUpdatedAt을 주소 버전으로 붙여 부른다. */
	@GetMapping
	public ResponseEntity<byte[]> get(@PathVariable Long broadcastId) {
		BroadcastThumbnail thumbnail = thumbnailService.get(broadcastId);
		return JpegImages.ok(thumbnail.getImage(), thumbnail.getUpdatedAt());
	}
}
