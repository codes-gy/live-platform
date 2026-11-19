package com.gy.live.user.avatar;

import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.security.LoginUser;
import com.gy.live.global.web.JpegImages;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAvatarController {

	static final int MAX_BYTES = 200 * 1024;

	private final UserAvatarService avatarService;

	public UserAvatarController(UserAvatarService avatarService) {
		this.avatarService = avatarService;
	}

	/** 로그인한 본인만. 본문은 JPEG 바이트(200KB 이하). 화면이 정사각형으로 줄여 보낸다. */
	@PutMapping(path = "/me/avatar", consumes = MediaType.IMAGE_JPEG_VALUE)
	public ResponseEntity<Void> replace(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request)
			throws IOException {
		avatarService.replace(loginUser.id(), JpegImages.read(request, MAX_BYTES, ErrorCode.INVALID_AVATAR));
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/me/avatar")
	public ResponseEntity<Void> remove(@AuthenticationPrincipal LoginUser loginUser) {
		avatarService.remove(loginUser.id());
		return ResponseEntity.noContent().build();
	}

	/** 로그인 없이. 사용자 응답의 avatarUpdatedAt을 주소 버전으로 붙여 부른다. */
	@GetMapping("/{userId}/avatar")
	public ResponseEntity<byte[]> get(@PathVariable Long userId) {
		UserAvatar avatar = avatarService.get(userId);
		return JpegImages.ok(avatar.getImage(), avatar.getUpdatedAt());
	}
}
