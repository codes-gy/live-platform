package com.gy.live.livekit;

public class LiveKitException extends RuntimeException {

	public LiveKitException(String message) {
		super(message);
	}

	public LiveKitException(String message, Throwable cause) {
		super(message, cause);
	}
}
