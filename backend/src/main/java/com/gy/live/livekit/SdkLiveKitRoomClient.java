package com.gy.live.livekit;

import com.gy.live.global.config.AppProperties;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import livekit.LivekitModels.ParticipantInfo;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

@Component
public class SdkLiveKitRoomClient implements LiveKitRoomClient {

	private static final int NOT_FOUND = 404;

	private final AppProperties.Livekit livekit;
	private volatile RoomServiceClient client;

	public SdkLiveKitRoomClient(AppProperties properties) {
		this.livekit = properties.livekit();
	}

	@Override
	public void createRoom(String roomName) {
		execute(roomService().createRoom(roomName,
				(int) livekit.emptyTimeout().toSeconds(), null, null, null, null, null, null,
				(int) livekit.departureTimeout().toSeconds()), "방 만들기");
	}

	@Override
	public void deleteRoom(String roomName) {
		Response<Void> response = call(roomService().deleteRoom(roomName), "방 닫기");
		if (!response.isSuccessful() && response.code() != NOT_FOUND) {
			throw new LiveKitException("방 닫기 실패: HTTP " + response.code());
		}
	}

	@Override
	public boolean hasParticipant(String roomName, String identity) {
		Response<java.util.List<ParticipantInfo>> response = call(roomService().listParticipants(roomName), "참가자 조회");
		if (response.code() == NOT_FOUND) {
			return false;
		}
		if (!response.isSuccessful() || response.body() == null) {
			throw new LiveKitException("참가자 조회 실패: HTTP " + response.code());
		}
		return response.body().stream().anyMatch(p -> p.getIdentity().equals(identity));
	}

	private <T> void execute(Call<T> call, String action) {
		Response<T> response = call(call, action);
		if (!response.isSuccessful()) {
			throw new LiveKitException(action + " 실패: HTTP " + response.code());
		}
	}

	private <T> Response<T> call(Call<T> call, String action) {
		try {
			return call.execute();
		} catch (IOException e) {
			throw new LiveKitException(action + " 요청 실패", e);
		}
	}

	/** 키가 없을 때도 서버는 떠야 하므로(목록 · 상세는 동작) 처음 쓸 때 만든다. */
	private RoomServiceClient roomService() {
		if (!livekit.configured()) {
			throw new BusinessException(ErrorCode.LIVEKIT_NOT_CONFIGURED);
		}
		RoomServiceClient current = client;
		if (current == null) {
			synchronized (this) {
				if (client == null) {
					client = RoomServiceClient.createClient(httpUrl(livekit.url()), livekit.apiKey(), livekit.apiSecret());
				}
				current = client;
			}
		}
		return current;
	}

	/** 클라이언트용 wss:// 주소를 서버 API용 https:// 주소로 바꾼다. */
	static String httpUrl(String url) {
		if (url.startsWith("wss://")) {
			return "https://" + url.substring("wss://".length());
		}
		if (url.startsWith("ws://")) {
			return "http://" + url.substring("ws://".length());
		}
		return url;
	}
}
