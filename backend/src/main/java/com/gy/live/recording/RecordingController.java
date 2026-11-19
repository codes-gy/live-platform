package com.gy.live.recording;

import com.gy.live.global.web.PageResponse;
import com.gy.live.recording.dto.RecordingResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 다시보기. 모두 공개(로그인 없이 시청할 수 있는 것과 같다). */
@RestController
public class RecordingController {

	private final RecordingService recordingService;

	public RecordingController(RecordingService recordingService) {
		this.recordingService = recordingService;
	}

	@GetMapping("/api/recordings")
	public PageResponse<RecordingResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "12") int size) {
		return recordingService.list(page, size);
	}

	@GetMapping("/api/recordings/{recordingId}")
	public RecordingResponse get(@PathVariable Long recordingId) {
		return recordingService.get(recordingId);
	}

	@GetMapping("/api/broadcasts/{broadcastId}/recordings")
	public List<RecordingResponse> ofBroadcast(@PathVariable Long broadcastId) {
		return recordingService.ofBroadcast(broadcastId);
	}
}
