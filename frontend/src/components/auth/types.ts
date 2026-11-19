export type Me = {
  id: number;
  email: string;
  nickname: string;
  /** 프로필 이미지가 없으면 null */
  avatarUpdatedAt: string | null;
};

export type AuthStatus = "loading" | "authenticated" | "anonymous";
