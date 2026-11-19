package com.gy.live.broadcast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

	/** 소유자 닉네임을 함께 읽어 목록에서 방송마다 사용자 조회가 나가지 않게 한다. */
	@EntityGraph(attributePaths = "owner")
	@Query(value = """
			select b from Broadcast b
			where (:status is null or b.status = :status)
			  and (:ownerId is null or b.owner.id = :ownerId)
			""",
			countQuery = """
					select count(b) from Broadcast b
					where (:status is null or b.status = :status)
					  and (:ownerId is null or b.owner.id = :ownerId)
					""")
	Page<Broadcast> search(@Param("status") BroadcastStatus status, @Param("ownerId") Long ownerId, Pageable pageable);

	@EntityGraph(attributePaths = "owner")
	@Query("select b from Broadcast b where b.id = :id")
	Optional<Broadcast> findWithOwnerById(@Param("id") Long id);

	/** 상태를 바꿀 때(시작 · 종료 · 웹훅) 행을 잠가, 동시에 온 요청이 같은 상태를 보고 둘 다 바꾸지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Broadcast b where b.id = :id")
	Optional<Broadcast> findForUpdateById(@Param("id") Long id);

	/**
	 * 댓글 작성용 공유 잠금(FOR SHARE). 댓글끼리는 막지 않고, 종료의 쓰기 잠금(FOR NO KEY UPDATE)과는 서로 기다린다.
	 * 그래서 종료가 커밋된 뒤에는 댓글이 생기지 않고, 실시간 구독자에게 ended가 마지막으로 간다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select b from Broadcast b where b.id = :id")
	Optional<Broadcast> findForShareById(@Param("id") Long id);

	/** 정리 대상: 시작한 지 유예 시간이 지난 LIVE 방송. 방송자 확인에 필요한 값만 읽는다. */
	@Query("select new com.gy.live.broadcast.LiveBroadcastRef(b.id, b.owner.id) from Broadcast b "
			+ "where b.status = com.gy.live.broadcast.BroadcastStatus.LIVE and b.startedAt < :startedBefore")
	List<LiveBroadcastRef> findLiveStartedBefore(@Param("startedBefore") Instant startedBefore);
}
