package com.gy.live.user.avatar;

import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAvatarService {

	private final UserAvatarRepository avatarRepository;

	public UserAvatarService(UserAvatarRepository avatarRepository) {
		this.avatarRepository = avatarRepository;
	}

	@Transactional
	public void replace(Long userId, byte[] image) {
		Instant now = Instant.now();
		avatarRepository.upsert(userId, image, now);
		avatarRepository.touchUser(userId, now);
	}

	@Transactional
	public void remove(Long userId) {
		avatarRepository.deleteById(userId);
		avatarRepository.touchUser(userId, null);
	}

	@Transactional(readOnly = true)
	public UserAvatar get(Long userId) {
		return avatarRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.AVATAR_NOT_FOUND));
	}
}
