package com.gy.live.comment.dto;

import java.util.List;

/**
 * 최신순 커서 페이지.
 *
 * @param items      최신 댓글이 앞에 온다
 * @param nextBefore 더 오래된 댓글을 받을 때 {@code before}로 넘길 값. 더 없으면 null
 */
public record CommentPageResponse(List<CommentResponse> items, boolean hasNext, Long nextBefore) {
}
