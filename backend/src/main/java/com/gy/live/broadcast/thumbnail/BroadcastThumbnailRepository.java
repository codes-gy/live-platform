package com.gy.live.broadcast.thumbnail;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BroadcastThumbnailRepository extends JpaRepository<BroadcastThumbnail, Long> {

	@Modifying
	@Query(value = """
			INSERT INTO broadcast_thumbnails (broadcast_id, image, updated_at) VALUES (:id, :image, :at)
			ON CONFLICT (broadcast_id) DO UPDATE SET image = excluded.image, updated_at = excluded.updated_at
			""", nativeQuery = true)
	void upsert(@Param("id") Long broadcastId, @Param("image") byte[] image, @Param("at") Instant at);

	/** broadcasts 행 전체를 다시 쓰지 않고 이 열만 바꿔, 동시에 진행 중인 종료 · 수정과 부딪치지 않게 한다 */
	@Modifying
	@Query(value = "UPDATE broadcasts SET thumbnail_updated_at = :at WHERE id = :id", nativeQuery = true)
	void touchBroadcast(@Param("id") Long broadcastId, @Param("at") Instant at);
}
