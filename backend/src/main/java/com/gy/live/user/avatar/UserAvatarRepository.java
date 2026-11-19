package com.gy.live.user.avatar;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {

	@Modifying
	@Query(value = """
			INSERT INTO user_avatars (user_id, image, updated_at) VALUES (:id, :image, :at)
			ON CONFLICT (user_id) DO UPDATE SET image = excluded.image, updated_at = excluded.updated_at
			""", nativeQuery = true)
	void upsert(@Param("id") Long userId, @Param("image") byte[] image, @Param("at") Instant at);

	/** at이 null이면 이미지가 없다는 뜻 */
	@Modifying
	@Query(value = "UPDATE users SET avatar_updated_at = :at WHERE id = :id", nativeQuery = true)
	void touchUser(@Param("id") Long userId, @Param("at") Instant at);
}
