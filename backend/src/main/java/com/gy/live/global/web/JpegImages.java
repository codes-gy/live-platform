package com.gy.live.global.web;

import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 썸네일 · 프로필 이미지처럼 브라우저가 만든 JPEG를 받아 DB에 두고 그대로 내보내는 경로의 공통 처리. */
public final class JpegImages {

	private JpegImages() {
	}

	/**
	 * 본문을 다 읽기 전에 선언된 크기부터 보고, 길이를 밝히지 않은 본문도 한도 + 1바이트까지만 읽는다.
	 * 공개 GET으로 그대로 내보내므로 JPEG 시작 표식(FF D8 FF)이 아닌 것은 받지 않는다.
	 */
	public static byte[] read(HttpServletRequest request, int maxBytes, ErrorCode invalid) throws IOException {
		if (request.getContentLengthLong() > maxBytes) {
			throw new BusinessException(invalid);
		}
		byte[] image = request.getInputStream().readNBytes(maxBytes + 1);
		boolean jpeg = image.length > 3 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8 && (image[2] & 0xFF) == 0xFF;
		if (image.length > maxBytes || !jpeg) {
			throw new BusinessException(invalid);
		}
		return image;
	}

	/** 화면은 주소에 갱신 시각을 붙여 부르므로 주소가 같으면 내용도 같다. */
	public static ResponseEntity<byte[]> ok(byte[] image, Instant updatedAt) {
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
				.lastModified(updatedAt)
				.body(image);
	}
}
