import { api, json } from "./api";

export type DonationOrder = {
  orderId: string;
  amount: number;
  orderName: string;
  clientKey: string;
  customerKey: string;
};

export type Donation = {
  id: number;
  broadcastId: number;
  donor: { id: number; nickname: string };
  amount: number;
  message: string | null;
  approvedAt: string;
};

export type DonationSummary = { totalAmount: number; count: number; items: Donation[] };

export const DONATION_MIN = 1000;
export const DONATION_MAX = 1_000_000;
export const DONATION_PRESETS = [1000, 5000, 10000, 50000];
export const DONATION_MESSAGE_MAX = 100;

export const formatWon = (n: number) => `${n.toLocaleString("ko-KR")}원`;

export const fetchDonations = (broadcastId: number) =>
  api<DonationSummary>(`/api/broadcasts/${broadcastId}/donations`);

export const orderDonation = (broadcastId: number, amount: number, message: string) =>
  api<DonationOrder>(`/api/broadcasts/${broadcastId}/donations/orders`, {
    method: "POST",
    body: json({ amount, message }),
  });

export const confirmDonation = (paymentKey: string, orderId: string, amount: number) =>
  api<Donation>("/api/donations/confirm", {
    method: "POST",
    body: json({ paymentKey, orderId, amount }),
  });
