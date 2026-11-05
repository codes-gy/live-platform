package com.gy.live.livekit;

import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.Hidden;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * LiveKit 방 접속 토큰을 서버 비밀키로 서명해 발급한다. 무엇을 할 수 있는지는 토큰의 권한(grant)이 정하고,
 * LiveKit 서버가 그대로 강제한다. 그래서 송출 권한은 서버가 소유자를 확인한 뒤에만 넣는다.
 */
@Component
public class LiveKitTokenIssuer {

	/** 방송자가 올릴 수 있는 트랙: PC(화면 + 화면 소리 + 마이크), 모바일(카메라 + 마이크). */
	static final List<String> PUBLISH_SOURCES = List.of("camera", "microphone", "screen_share", "screen_share_audio");

	private final AppProperties.Livekit livekit;

	public LiveKitTokenIssuer(AppProperties properties) {
		this.livekit = properties.livekit();
	}

	public String publisherToken(String roomName, String identity, String name) {
		AccessToken token = newToken(identity, name);
		token.addGrants(new RoomJoin(true), new RoomName(roomName),
				new CanPublish(true), new CanPublishSources(PUBLISH_SOURCES),
				new CanSubscribe(true), new CanPublishData(false));
		return token.toJwt();
	}

	/** 시청자는 받기만 한다. 다른 시청자에게 보이지 않게 숨긴다. */
	public String viewerToken(String roomName, String identity, String name) {
		AccessToken token = newToken(identity, name);
		token.addGrants(new RoomJoin(true), new RoomName(roomName),
				new CanPublish(false), new CanSubscribe(true), new CanPublishData(false), new Hidden(true));
		return token.toJwt();
	}

	public String serverUrl() {
		requireConfigured();
		return livekit.url();
	}

	private AccessToken newToken(String identity, String name) {
		requireConfigured();
		AccessToken token = new AccessToken(livekit.apiKey(), livekit.apiSecret());
		token.setIdentity(identity);
		token.setName(name);
		token.setTtl(livekit.tokenTtl().toMillis());
		return token;
	}

	private void requireConfigured() {
		if (!livekit.configured()) {
			throw new BusinessException(ErrorCode.LIVEKIT_NOT_CONFIGURED);
		}
	}
}
