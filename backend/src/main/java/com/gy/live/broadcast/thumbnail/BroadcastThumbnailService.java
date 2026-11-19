package com.gy.live.broadcast.thumbnail;

import com.gy.live.broadcast.BroadcastService;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 방송자 브라우저가 방송 중 주기적으로 찍어 올리는 송출 화면. 방송마다 마지막 1장만 둔다. */
@Service
public class BroadcastThumbnailService {

	private final BroadcastService broadcastService;
	private final BroadcastThumbnailRepository thumbnailRepository;

	public BroadcastThumbnailService(BroadcastService broadcastService, BroadcastThumbnailRepository thumbnailRepository) {
		this.broadcastService = broadcastService;
		this.thumbnailRepository = thumbnailRepository;
	}

	@Transactional
	public void upload(Long userId, Long broadcastId, byte[] image) {
		broadcastService.requireLiveOwned(userId, broadcastId);
		Instant now = Instant.now();
		thumbnailRepository.upsert(broadcastId, image, now);
		thumbnailRepository.touchBroadcast(broadcastId, now);
	}

	@Transactional(readOnly = true)
	public BroadcastThumbnail get(Long broadcastId) {
		return thumbnailRepository.findById(broadcastId)
				.orElseThrow(() -> new BusinessException(ErrorCode.THUMBNAIL_NOT_FOUND));
	}
}
