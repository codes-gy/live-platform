export type Me = {
  id: number;
  email: string;
  nickname: string;
};

export type TestAccount = "STREAMER" | "VIEWER";

export type AuthStatus = "loading" | "authenticated" | "anonymous";
