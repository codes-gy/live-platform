package com.gy.live.livekit;

import com.gy.live.global.config.AppProperties;
import io.livekit.server.AudioMixing;
import io.livekit.server.EgressServiceClient;
import java.io.IOException;
import livekit.LivekitEgress.EgressInfo;
import livekit.LivekitEgress.EncodedFileOutput;
import livekit.LivekitEgress.EncodedFileType;
import livekit.LivekitEgress.EncodingOptionsPreset;
import livekit.LivekitEgress.S3Upload;
import org.springframework.stereotype.Component;
import retrofit2.Response;

@Component
public class SdkLiveKitRecorder implements LiveKitRecorder {

	/** 화면 공유 · 카메라를 크게 보여 주는 LiveKit 기본 레이아웃 */
	private static final String LAYOUT = "speaker";

	private final AppProperties.Livekit livekit;
	private final AppProperties.Recording recording;
	private volatile EgressServiceClient client;

	public SdkLiveKitRecorder(AppProperties properties) {
		this.livekit = properties.livekit();
		this.recording = properties.recording();
	}

	@Override
	public boolean enabled() {
		return livekit.configured() && recording != null && recording.configured();
	}

	@Override
	public String startRoomRecording(String roomName, String objectKey) {
		S3Upload r2 = S3Upload.newBuilder()
				.setAccessKey(recording.r2AccessKeyId())
				.setSecret(recording.r2SecretAccessKey())
				.setBucket(recording.r2Bucket())
				.setEndpoint(recording.endpoint())
				.setRegion("auto")
				.setForcePathStyle(true)
				.build();
		EncodedFileOutput output = EncodedFileOutput.newBuilder()
				.setFileType(EncodedFileType.MP4)
				.setFilepath(objectKey)
				.setS3(r2)
				.build();
		Response<EgressInfo> response;
		try {
			response = egress().startRoomCompositeEgress(roomName, output, LAYOUT,
					EncodingOptionsPreset.H264_720P_30, null, false, false, "", AudioMixing.DEFAULT_MIXING).execute();
		} catch (IOException e) {
			throw new LiveKitException("녹화 시작 요청 실패", e);
		}
		if (!response.isSuccessful() || response.body() == null) {
			throw new LiveKitException("녹화 시작 실패: HTTP " + response.code());
		}
		return response.body().getEgressId();
	}

	private EgressServiceClient egress() {
		EgressServiceClient current = client;
		if (current == null) {
			synchronized (this) {
				if (client == null) {
					client = EgressServiceClient.Companion.createClient(
							SdkLiveKitRoomClient.httpUrl(livekit.url()), livekit.apiKey(), livekit.apiSecret());
				}
				current = client;
			}
		}
		return current;
	}
}
