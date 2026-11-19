package com.gy.live.donation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DonationRepository extends JpaRepository<Donation, Long> {

	/** 승인은 주문 행을 잠그고 한다. 같은 승인 요청이 동시에 와도 하나만 토스를 부르고 나머지는 결과를 본다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Donation d where d.orderId = :orderId")
	Optional<Donation> findForUpdateByOrderId(@Param("orderId") String orderId);

	@EntityGraph(attributePaths = "donor")
	@Query("select d from Donation d where d.broadcast.id = :broadcastId "
			+ "and d.status = com.gy.live.donation.DonationStatus.DONE order by d.id desc")
	List<Donation> findDone(@Param("broadcastId") Long broadcastId, Limit limit);

	@Query("select count(d) from Donation d where d.broadcast.id = :broadcastId "
			+ "and d.status = com.gy.live.donation.DonationStatus.DONE")
	long countDone(@Param("broadcastId") Long broadcastId);

	@Query("select coalesce(sum(d.amount), 0) from Donation d where d.broadcast.id = :broadcastId "
			+ "and d.status = com.gy.live.donation.DonationStatus.DONE")
	long sumDone(@Param("broadcastId") Long broadcastId);
}
