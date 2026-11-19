package com.gy.live.support;

import com.gy.live.recording.RecordingStorage;

/** 서명 대신 알아보기 쉬운 주소를 돌려준다. */
public class FakeRecordingStorage implements RecordingStorage {

	@Override
	public String playbackUrl(String objectKey) {
		return "https://r2.test/" + objectKey + "?signed";
	}
}
