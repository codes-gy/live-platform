package com.gy.live.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.broadcast.BroadcastStatus;
import com.gy.live.support.AuthSupport;
import com.gy.live.support.FakeLiveKitRecorder;
import com.gy.live.support.FakeLiveKitRoomClient;
import com.gy.live.support.IntegrationTest;
import io.livekit.server.AccessToken;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RecordingControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	RecordingRepository recordingRepository;

	@Autowired
	BroadcastRepository broadcastRepository;

	@Autowired
	FakeLiveKitRecorder recorder;

	@Autowired
	FakeLiveKitRoomClient liveKit;

	Cookie owner;

	@BeforeEach
	void setUp() throws Exception {
		recorder.reset();
		liveKit.reset();
		owner = AuthSupport.signupNewUser(mockMvc, "방송주인");
	}

	private long createAndStart() throws Exception {
		String location = mockMvc.perform(post("/api/broadcasts").cookie(owner)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"title":"녹화 시험"}
								"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
		long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
		mockMvc.perform(post("/api/broadcasts/{id}/start", id).cookie(owner)).andExpect(status().isOk());
		return id;
	}

	/** 녹화는 시작 응답 뒤 따로 걸리므로 행이 생길 때까지 잠깐 기다린다. */
	private Recording awaitRecording(long broadcastId) throws InterruptedException {
		for (int i = 0; i < 50; i++) {
			List<Recording> found = recordingRepository.findByBroadcastIdOrderByIdDesc(broadcastId);
			if (!found.isEmpty()) {
				return found.getFirst();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("녹화가 만들어지지 않았다");
	}

	private static String sign(String body) throws Exception {
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
		AccessToken token = new AccessToken(IntegrationTest.LIVEKIT_KEY, IntegrationTest.LIVEKIT_SECRET);
		token.setSha256(Base64.getEncoder().encodeToString(hash));
		return token.toJwt();
	}

	private void egressEnded(String egressId, String status, String objectKey, String error) throws Exception {
		String body = """
				{"event":"egress_ended","egressInfo":{"egressId":"%s","status":"%s","error":"%s",
				"fileResults":[%s]}}
				""".formatted(egressId, status, error,
				objectKey == null ? "" : """
						{"filename":"%s","duration":"65000000000","size":"1048576"}""".formatted(objectKey));
		mockMvc.perform(post("/api/livekit/webhook").contentType("application/webhook+json").content(body)
						.header(HttpHeaders.AUTHORIZATION, sign(body)))
				.andExpect(status().isOk());
	}

	@Test
	void 방송을_시작하면_방_녹화를_건다() throws Exception {
		long id = createAndStart();
		Recording recording = awaitRecording(id);

		assertThat(recording.getStatus()).isEqualTo(RecordingStatus.RECORDING);
		assertThat(recorder.started).hasSize(1);
		assertThat(recorder.started.getFirst().roomName()).isEqualTo("broadcast-" + id);
		assertThat(recording.getObjectKey()).startsWith("recordings/broadcast-" + id + "/").endsWith(".mp4");
		assertThat(recording.getEgressId()).isEqualTo(recorder.started.getFirst().egressId());
	}

	@Test
	void 녹화가_끝나면_다시보기_목록과_상세에서_재생할_수_있다() throws Exception {
		long id = createAndStart();
		Recording recording = awaitRecording(id);

		egressEnded(recording.getEgressId(), "EGRESS_COMPLETE", recording.getObjectKey(), "");

		mockMvc.perform(get("/api/recordings/{rid}", recording.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.durationMs").value(65000))
				.andExpect(jsonPath("$.sizeBytes").value(1048576))
				.andExpect(jsonPath("$.broadcast.id").value(id))
				.andExpect(jsonPath("$.playbackUrl").value("https://r2.test/" + recording.getObjectKey() + "?signed"));
		mockMvc.perform(get("/api/recordings").param("size", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == %d)].status".formatted(recording.getId())).value("READY"))
				.andExpect(jsonPath("$.items[?(@.id == %d)].playbackUrl".formatted(recording.getId())).isEmpty());
		mockMvc.perform(get("/api/broadcasts/{id}/recordings", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(recording.getId()));
	}

	@Test
	void 같은_종료_알림이_다시_와도_결과가_같다() throws Exception {
		long id = createAndStart();
		Recording recording = awaitRecording(id);

		egressEnded(recording.getEgressId(), "EGRESS_COMPLETE", recording.getObjectKey(), "");
		egressEnded(recording.getEgressId(), "EGRESS_COMPLETE", recording.getObjectKey(), "");

		assertThat(recordingRepository.findByBroadcastIdOrderByIdDesc(id)).hasSize(1)
				.first().extracting(Recording::getStatus).isEqualTo(RecordingStatus.READY);
	}

	@Test
	void 녹화가_실패하면_목록에_없고_재생_주소도_없다() throws Exception {
		long id = createAndStart();
		Recording recording = awaitRecording(id);

		egressEnded(recording.getEgressId(), "EGRESS_FAILED", null, "upload failed");

		mockMvc.perform(get("/api/recordings/{rid}", recording.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.playbackUrl").isEmpty());
		mockMvc.perform(get("/api/recordings").param("size", "50"))
				.andExpect(jsonPath("$.items[?(@.id == %d)]".formatted(recording.getId())).isEmpty());
	}

	@Test
	void 녹화를_시작하지_못해도_방송은_계속된다() throws Exception {
		recorder.failStart = true;
		long id = createAndStart();
		Recording recording = awaitRecording(id);

		assertThat(recording.getStatus()).isEqualTo(RecordingStatus.FAILED);
		assertThat(recording.getEgressId()).isNull();
		assertThat(broadcastRepository.findById(id).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.LIVE);
	}

	@Test
	void 녹화_설정이_없으면_녹화하지_않는다() throws Exception {
		recorder.enabled = false;
		long id = createAndStart();
		Thread.sleep(500);

		assertThat(recordingRepository.findByBroadcastIdOrderByIdDesc(id)).isEmpty();
		assertThat(recorder.started).isEmpty();
	}

	@Test
	void 모르는_녹화의_종료_알림은_무시한다() throws Exception {
		egressEnded("EG_unknown", "EGRESS_COMPLETE", "recordings/x.mp4", "");
	}

	@Test
	void 없는_녹화는_404() throws Exception {
		mockMvc.perform(get("/api/recordings/{rid}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECORDING_NOT_FOUND"));
	}
}
