export type Me = {
  id: number;
  email: string;
  nickname: string;
};

export type AuthStatus = "loading" | "authenticated" | "anonymous";
