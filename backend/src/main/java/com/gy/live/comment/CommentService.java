package com.gy.live.comment;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.broadcast.BroadcastRepository;
import com.gy.live.comment.dto.CommentPageResponse;
import com.gy.live.comment.dto.CommentRequest;
import com.gy.live.comment.dto.CommentResponse;
import com.gy.live.global.error.BusinessException;
import com.gy.live.global.error.ErrorCode;
import com.gy.live.user.User;
import com.gy.live.user.UserRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

	static final int MAX_PAGE_SIZE = 100;

	private final CommentRepository commentRepository;
	private final BroadcastRepository broadcastRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher events;

	public CommentService(CommentRepository commentRepository, BroadcastRepository broadcastRepository,
			UserRepository userRepository, ApplicationEventPublisher events) {
		this.commentRepository = commentRepository;
		this.broadcastRepository = broadcastRepository;
		this.userRepository = userRepository;
		this.events = events;
	}

	/** 로그인 사용자, 방송 중일 때만. */
	@Transactional
	public CommentResponse create(Long userId, Long broadcastId, CommentRequest request) {
		Broadcast broadcast = broadcastRepository.findForShareById(broadcastId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BROADCAST_NOT_FOUND));
		broadcast.checkLive();
		User author = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		CommentResponse created = CommentResponse.from(
				commentRepository.save(new Comment(broadcast, author, request.normalizedContent())));
		events.publishEvent(new CommentEvent.Created(created));
		return created;
	}

	/** 누구나. 종료된 방송도 볼 수 있다(다시보기). */
	@Transactional(readOnly = true)
	public CommentPageResponse list(Long broadcastId, Long before, int size) {
		if (!broadcastRepository.existsById(broadcastId)) {
			throw new BusinessException(ErrorCode.BROADCAST_NOT_FOUND);
		}
		int limit = Math.clamp(size, 1, MAX_PAGE_SIZE);
		// 하나 더 읽어서 다음 페이지가 있는지 안다
		List<Comment> rows = commentRepository.findPage(broadcastId, before, Limit.of(limit + 1));
		boolean hasNext = rows.size() > limit;
		List<CommentResponse> items = rows.stream().limit(limit).map(CommentResponse::from).toList();
		return new CommentPageResponse(items, hasNext, hasNext ? items.getLast().id() : null);
	}

	/** 작성자만. */
	@Transactional
	public CommentResponse edit(Long userId, Long broadcastId, Long commentId, CommentRequest request) {
		Comment comment = find(broadcastId, commentId);
		if (!comment.isWrittenBy(userId)) {
			throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
		}
		comment.edit(request.normalizedContent());
		CommentResponse edited = CommentResponse.from(comment);
		events.publishEvent(new CommentEvent.Updated(edited));
		return edited;
	}

	/** 작성자, 또는 자기 방송의 댓글을 정리하는 방송 소유자. */
	@Transactional
	public void delete(Long userId, Long broadcastId, Long commentId) {
		Comment comment = find(broadcastId, commentId);
		if (!comment.isWrittenBy(userId) && !comment.getBroadcast().isOwnedBy(userId)) {
			throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
		}
		commentRepository.delete(comment);
		events.publishEvent(new CommentEvent.Deleted(broadcastId, commentId));
	}

	private Comment find(Long broadcastId, Long commentId) {
		return commentRepository.findInBroadcast(broadcastId, commentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
	}
}
