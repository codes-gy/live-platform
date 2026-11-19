-- 목록 응답에 썸네일 유무 · 갱신 시각을 실어, 카드가 없는 썸네일을 요청(404)하지 않고 바뀐 때만 새로 받게 한다
ALTER TABLE broadcasts ADD COLUMN thumbnail_updated_at TIMESTAMPTZ;
