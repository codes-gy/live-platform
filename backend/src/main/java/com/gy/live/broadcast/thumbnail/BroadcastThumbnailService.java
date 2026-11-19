package com.gy.live.broadcast.thumbnail;

import com.gy.live.broadcast.BroadcastService;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 방송자 브라우저가 방송 중 주기적으로 찍어 올리는 송출 화면. 방송마다 마지막 1장만 둔다. */
@Service
public class BroadcastThumbnailService {

	static final int MAX_BYTES = 300 * 1024;

	private final BroadcastService broadcastService;
	private final BroadcastThumbnailRepository thumbnailRepository;

	public BroadcastThumbnailService(BroadcastService broadcastService, BroadcastThumbnailRepository thumbnailRepository) {
		this.broadcastService = broadcastService;
		this.thumbnailRepository = thumbnailRepository;
	}

	@Transactional
	public void upload(Long userId, Long broadcastId, byte[] image) {
		broadcastService.requireLiveOwned(userId, broadcastId);
		if (image.length > MAX_BYTES || !isJpeg(image)) {
			throw new BusinessException(ErrorCode.INVALID_THUMBNAIL);
		}
		thumbnailRepository.findById(broadcastId).ifPresentOrElse(
				thumbnail -> thumbnail.replace(image),
				() -> thumbnailRepository.save(new BroadcastThumbnail(broadcastId, image)));
	}

	@Transactional(readOnly = true)
	public BroadcastThumbnail get(Long broadcastId) {
		return thumbnailRepository.findById(broadcastId)
				.orElseThrow(() -> new BusinessException(ErrorCode.THUMBNAIL_NOT_FOUND));
	}

	/** 공개 GET으로 그대로 내보내므로 JPEG 시작 표식(FF D8 FF)이 아닌 것은 받지 않는다 */
	private static boolean isJpeg(byte[] image) {
		return image.length > 3 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8 && (image[2] & 0xFF) == 0xFF;
	}
}
