package com.gy.live.recording;

import com.gy.live.global.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cloudflare R2(S3 호환)의 서명된 GET 주소를 AWS Signature V4 쿼리 서명으로 만든다.
 * 서명만 하면 되므로 AWS SDK 전체를 들이지 않고 표준 라이브러리로 계산한다.
 */
@Component
public class R2RecordingStorage implements RecordingStorage {

	private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String REGION = "auto";
	private static final String SERVICE = "s3";
	private static final long MAX_EXPIRES_SECONDS = 604_800;

	private final AppProperties.Recording recording;
	private final Clock clock;

	@Autowired
	public R2RecordingStorage(AppProperties properties) {
		this(properties.recording(), Clock.systemUTC());
	}

	R2RecordingStorage(AppProperties.Recording recording, Clock clock) {
		this.recording = recording;
		this.clock = clock;
	}

	@Override
	public String playbackUrl(String objectKey) {
		ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);
		String amzDate = AMZ_DATE.format(now);
		String date = DATE.format(now);
		String host = recording.r2AccountId() + ".r2.cloudflarestorage.com";
		String path = "/" + encode(recording.r2Bucket()) + "/" + encodePath(objectKey);
		String scope = date + "/" + REGION + "/" + SERVICE + "/aws4_request";
		long expires = Math.min(MAX_EXPIRES_SECONDS, recording.playbackTtl().toSeconds());

		Map<String, String> query = new TreeMap<>();
		query.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
		query.put("X-Amz-Credential", recording.r2AccessKeyId() + "/" + scope);
		query.put("X-Amz-Date", amzDate);
		query.put("X-Amz-Expires", Long.toString(expires));
		query.put("X-Amz-SignedHeaders", "host");
		String canonicalQuery = query.entrySet().stream()
				.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
				.collect(Collectors.joining("&"));

		String canonicalRequest = String.join("\n", "GET", path, canonicalQuery, "host:" + host, "", "host",
				"UNSIGNED-PAYLOAD");
		String stringToSign = String.join("\n", "AWS4-HMAC-SHA256", amzDate, scope, sha256Hex(canonicalRequest));

		byte[] key = hmac(("AWS4" + recording.r2SecretAccessKey()).getBytes(StandardCharsets.UTF_8), date);
		key = hmac(key, REGION);
		key = hmac(key, SERVICE);
		key = hmac(key, "aws4_request");
		String signature = HexFormat.of().formatHex(hmac(key, stringToSign));
		return "https://" + host + path + "?" + canonicalQuery + "&X-Amz-Signature=" + signature;
	}

	/** 경로는 '/'를 남기고 조각마다 인코딩한다. */
	static String encodePath(String path) {
		return java.util.Arrays.stream(path.split("/", -1)).map(R2RecordingStorage::encode)
				.collect(Collectors.joining("/"));
	}

	/** AWS SigV4 URI 인코딩: A-Z a-z 0-9 - _ . ~ 만 그대로, 나머지는 %XX(대문자). */
	static String encode(String value) {
		StringBuilder out = new StringBuilder();
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			char c = (char) (b & 0xff);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| c == '-' || c == '_' || c == '.' || c == '~') {
				out.append(c);
			} else {
				out.append('%').append(String.format("%02X", b & 0xff));
			}
		}
		return out.toString();
	}

	private static String sha256Hex(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] hmac(byte[] key, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
