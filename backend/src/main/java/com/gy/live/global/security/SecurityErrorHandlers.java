package com.gy.live.global.security;

import com.gy.live.global.error.ErrorCode;
import com.gy.live.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 인증 실패(401) · 인가 실패(403)도 컨트롤러 오류와 같은 {code, message} 형식으로 응답한다. */
@Component
public class SecurityErrorHandlers {

	private final ObjectMapper objectMapper;

	public SecurityErrorHandlers(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public AuthenticationEntryPoint entryPoint() {
		return (request, response, e) -> write(response, ErrorCode.UNAUTHORIZED);
	}

	public AccessDeniedHandler accessDeniedHandler() {
		return (request, response, e) -> write(response, ErrorCode.FORBIDDEN);
	}

	private void write(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code));
	}
}
