-- 사용자 프로필 이미지 1장. 썸네일과 같이 목록 응답이 이미지를 읽지 않도록 users와 나누고,
-- users에는 갱신 시각만 둬서 화면이 있을 때만 · 바뀐 때만 받게 한다
CREATE TABLE user_avatars
(
    user_id    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    image      BYTEA       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMPTZ;
