-- BARRIER 지표 표기 통일: reason 문구의 "장벽" → "피부 장벽" (이슈 #114)
-- 재삽입(DELETE + INSERT) 금지 — daily_todo.action_master_id가 기존 id를 참조하고 있어 위험하다.
--
-- 대상은 id=17 한 행뿐이다. 나머지 BARRIER 행(id 20·24)의 reason은 이미 "피부 장벽"이다.
--
-- 실행 예시:
-- docker run -i --rm mysql:8 mysql \
--   --default-character-set=utf8mb4 \
--   -h "$RDS" -u sleep2skin -p sleep2skin < action_master_barrier_label.sql
--
-- ⚠️ --default-character-set=utf8mb4 를 빠뜨리면 한국어가 ???로 들어가고 UPDATE는 성공한다.

UPDATE action_master
SET reason = '과도한 각질 제거는 피부를 자극하고 피부 장벽을 약하게 만들 수 있어요'
WHERE id = 17;
