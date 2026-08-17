-- action_master threshold 상향 (기존 값 +20, 90 초과 금지)
-- 재삽입(DELETE + INSERT) 금지 — daily_todo.action_master_id가 기존 id를 참조하고 있어 위험하다.
--
-- 실행 예시:
-- docker run -i --rm mysql:8 mysql \
--   --default-character-set=utf8mb4 \
--   -h "$RDS" -u sleep2skin -p sleep2skin < action_master_raise_threshold.sql

UPDATE action_master SET threshold = LEAST(threshold + 20, 90);
