package com.gy.live.recording;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Recording r where r.egressId = :egressId")
	Optional<Recording> findForUpdateByEgressId(@Param("egressId") String egressId);

	/** 다시보기 목록: 재생할 수 있는 녹화만, 최신순. */
	@EntityGraph(attributePaths = {"broadcast", "broadcast.owner"})
	@Query(value = "select r from Recording r where r.status = com.gy.live.recording.RecordingStatus.READY",
			countQuery = "select count(r) from Recording r where r.status = com.gy.live.recording.RecordingStatus.READY")
	Page<Recording> findReady(Pageable pageable);

	@EntityGraph(attributePaths = {"broadcast", "broadcast.owner"})
	@Query("select r from Recording r where r.id = :id")
	Optional<Recording> findWithBroadcastById(@Param("id") Long id);

	@EntityGraph(attributePaths = {"broadcast", "broadcast.owner"})
	List<Recording> findByBroadcastIdOrderByIdDesc(Long broadcastId);
}
