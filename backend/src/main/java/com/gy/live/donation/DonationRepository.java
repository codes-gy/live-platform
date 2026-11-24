package com.gy.live.donation;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DonationRepository extends JpaRepository<Donation, Long> {

	/**
	 * 승인 전 확인 · 결과 반영은 주문 행을 잠그고 짧게 한다. 토스 호출(네트워크)은 잠금을 풀고 한다.
	 * 승인 중(CONFIRMING) 표시가 있으면 다른 요청은 토스를 다시 부르지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Donation d where d.orderId = :orderId")
	Optional<Donation> findForUpdateByOrderId(@Param("orderId") String orderId);

	/** 승인을 요청한 지 오래됐는데 결과를 반영하지 못한 주문. 오래된 것부터. */
	@Query("select d.orderId from Donation d where d.status = com.gy.live.donation.DonationStatus.CONFIRMING "
			+ "and d.confirmRequestedAt < :before order by d.confirmRequestedAt")
	List<String> findConfirmingOrderIds(@Param("before") Instant before, Limit limit);

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
