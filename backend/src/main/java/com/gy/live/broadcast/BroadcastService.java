package com.gy.live.broadcast;

import com.gy.live.broadcast.dto.BroadcastInfoRequest;
import com.gy.live.broadcast.dto.BroadcastResponse;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.web.PageResponse;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BroadcastService {

	static final int MAX_PAGE_SIZE = 50;

	private final BroadcastRepository broadcastRepository;
	private final UserRepository userRepository;

	public BroadcastService(BroadcastRepository broadcastRepository, UserRepository userRepository) {
		this.broadcastRepository = broadcastRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public BroadcastResponse create(Long userId, BroadcastInfoRequest request) {
		User owner = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		Broadcast broadcast = broadcastRepository.save(
				new Broadcast(owner, request.normalizedTitle(), request.normalizedDescription()));
		return BroadcastResponse.from(broadcast);
	}

	@Transactional(readOnly = true)
	public PageResponse<BroadcastResponse> search(BroadcastStatus status, Long ownerId, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
		return PageResponse.of(broadcastRepository.search(status, ownerId, pageable), BroadcastResponse::from);
	}

	@Transactional(readOnly = true)
	public BroadcastResponse get(Long broadcastId) {
		return BroadcastResponse.from(find(broadcastId));
	}

	@Transactional
	public BroadcastResponse update(Long userId, Long broadcastId, BroadcastInfoRequest request) {
		Broadcast broadcast = findOwned(userId, broadcastId);
		broadcast.updateInfo(request.normalizedTitle(), request.normalizedDescription());
		return BroadcastResponse.from(broadcast);
	}

	@Transactional
	public void delete(Long userId, Long broadcastId) {
		Broadcast broadcast = findOwned(userId, broadcastId);
		broadcast.checkDeletable();
		broadcastRepository.delete(broadcast);
	}

	/** 수정 · 삭제 · 송출 제어의 공통 관문: 방송이 있고, 요청한 사람이 소유자여야 한다. */
	Broadcast findOwned(Long userId, Long broadcastId) {
		Broadcast broadcast = find(broadcastId);
		if (!broadcast.isOwnedBy(userId)) {
			throw new BusinessException(ErrorCode.NOT_BROADCAST_OWNER);
		}
		return broadcast;
	}

	private Broadcast find(Long broadcastId) {
		return broadcastRepository.findWithOwnerById(broadcastId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
	}
}
