package com.gy.live.comment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	/** 최신순 커서 조회. before가 없으면 가장 최근부터, 있으면 그 id보다 오래된 것부터. 작성자를 함께 읽는다. */
	@EntityGraph(attributePaths = "author")
	@Query("""
			select c from Comment c
			where c.broadcast.id = :broadcastId
			  and (:before is null or c.id < :before)
			order by c.id desc
			""")
	List<Comment> findPage(@Param("broadcastId") Long broadcastId, @Param("before") Long before, Limit limit);

	@EntityGraph(attributePaths = {"author", "broadcast", "broadcast.owner"})
	@Query("select c from Comment c where c.id = :id and c.broadcast.id = :broadcastId")
	Optional<Comment> findInBroadcast(@Param("broadcastId") Long broadcastId, @Param("id") Long id);
}
