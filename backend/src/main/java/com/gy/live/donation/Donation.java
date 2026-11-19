package com.gy.live.donation;

import com.gy.live.broadcast.Broadcast;
import com.gy.live.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 후원 주문 겸 결제 결과. 금액 · 대상은 주문을 만들 때 서버가 정하고, 승인 때 이 값과 대조한다. */
@Entity
@Table(name = "donations")
public class Donation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "broadcast_id", nullable = false, updatable = false)
	private Broadcast broadcast;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "donor_id", nullable = false, updatable = false)
	private User donor;

	@Column(name = "order_id", nullable = false, updatable = false, length = 64)
	private String orderId;

	@Column(nullable = false, updatable = false)
	private int amount;

	@Column(length = 100, updatable = false)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private DonationStatus status;

	@Column(name = "payment_key", length = 200)
	private String paymentKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "approved_at")
	private Instant approvedAt;

	protected Donation() {
	}

	public Donation(Broadcast broadcast, User donor, int amount, String message) {
		this.broadcast = Objects.requireNonNull(broadcast);
		this.donor = Objects.requireNonNull(donor);
		this.amount = amount;
		this.message = message;
		// 토스 규칙: 6~64자 영문 · 숫자 · -_
		this.orderId = "don-" + UUID.randomUUID();
		this.status = DonationStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public void approve(String paymentKey) {
		this.paymentKey = paymentKey;
		this.status = DonationStatus.DONE;
		this.approvedAt = Instant.now();
	}

	public boolean isDone() {
		return status == DonationStatus.DONE;
	}

	public boolean isOrderedBy(Long userId) {
		return donor.getId().equals(userId);
	}

	public Long getId() {
		return id;
	}

	public Broadcast getBroadcast() {
		return broadcast;
	}

	public User getDonor() {
		return donor;
	}

	public String getOrderId() {
		return orderId;
	}

	public int getAmount() {
		return amount;
	}

	public String getMessage() {
		return message;
	}

	public DonationStatus getStatus() {
		return status;
	}

	public String getPaymentKey() {
		return paymentKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}
}
