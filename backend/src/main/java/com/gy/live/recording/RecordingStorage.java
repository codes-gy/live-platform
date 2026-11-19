package com.gy.live.recording;

/** 녹화 파일 저장소. 버킷은 비공개라 재생할 때마다 잠깐 유효한 주소를 만든다. 테스트에서는 가짜로 바꾼다. */
public interface RecordingStorage {

	/** 브라우저가 바로 재생할 수 있는 서명된 GET 주소. */
	String playbackUrl(String objectKey);
}
