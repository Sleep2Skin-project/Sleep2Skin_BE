-- action_master 시드 (24행)
-- DARK_CIRCLE(다크서클 회복) · COMPLEXION(혈색) · BARRIER(피부 장벽), 각 AVOID 4 + DO 4
-- reason은 "오늘은 피하세요" 카드 롱프레스 노출용. DO 항목엔 사용하지 않는다.

INSERT INTO action_master (category, title, reason, target_metric, threshold, impact_score, active, created_at, updated_at) VALUES
-- DARK_CIRCLE / AVOID
('AVOID', '눈 비비기·문지르기', '눈가 마찰은 자극을 주어 다크서클을 도드라지게 할 수 있어요.', 'DARK_CIRCLE', 80, 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '오후 카페인 과다 섭취', '늦은 카페인은 숙면을 방해해 눈가 회복에 영향을 줄 수 있어요', 'DARK_CIRCLE', 90, 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '짠 음식·야식', '과도한 나트륨은 눈가 붓기를 유발할 수 있어요', 'DARK_CIRCLE', 50, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '취침 전 스마트폰 장시간 사용', '취침 전 화면 사용은 숙면을 방해할 수 있어요', 'DARK_CIRCLE', 50, 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- DARK_CIRCLE / DO
('DO', '충분한 수면 시간 확보', '충분한 수면은 눈가의 피로하고 어두운 인상을 줄이는 데 도움을 줄 수 있어요', 'DARK_CIRCLE', 90, 9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '눈가 냉찜질 5분', '차가운 찜질은 눈가 붓기를 완화해 다크서클이 덜 도드라져 보이도록 도울 수 있어요', 'DARK_CIRCLE', 80, 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '눈가 보습 제품 사용', '눈가의 건조함을 줄여 피부가 칙칙해 보이지 않도록 관리해요', 'DARK_CIRCLE', 70, 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '베개 살짝 높여 자기', '머리를 약간 높여 자면 눈가에 체액이 고여 붓는 것을 줄이는 데 도움을 줄 수 있어요', 'DARK_CIRCLE', 50, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- COMPLEXION / AVOID
('AVOID', '과도한 음주', '과음은 수분 부족과 피로로 피부가 칙칙해 보일 수 있어요', 'COMPLEXION', 60, 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '끼니 자주 거르기', '불규칙한 식사는 피부가 생기 없어 보이는 데 영향을 줄 수 있어요', 'COMPLEXION', 80, 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '장시간 같은 자세로 있기', '오래 움직이지 않으면 얼굴이 생기 없어 보일 수 있어요', 'COMPLEXION', 90, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '과로·휴식 부족', '충분히 쉬지 못하면 피로가 쌓여 얼굴빛이 칙칙해 보일 수 있어요', 'COMPLEXION', 70, 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- COMPLEXION / DO
('DO', '충분한 수분 섭취', '충분한 수분을 섭취해 전반적인 피부 컨디션을 관리해 주세요', 'COMPLEXION', 80, 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '아침에 가볍게 스트레칭하기', '가벼운 스트레칭은 몸을 움직이고 활기 있는 하루를 시작하는 데 도움을 줘요', 'COMPLEXION', 90, 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '5분간 천천히 복식호흡하기', '복식호흡은 긴장 완화와 혈색 개선에 도움을 줄 수 있어요', 'COMPLEXION', 60, 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '아침에 가볍게 냉찜질하기', '가벼운 냉찜질은 붓기를 가라앉히고 혈색을 맑아 보이게 할 수 있어요', 'COMPLEXION', 50, 4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

-- BARRIER / AVOID
('AVOID', '강한 각질 제거', '과도한 각질 제거는 피부를 자극하고 피부 장벽을 약하게 만들 수 있어요', 'BARRIER', 50, 9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '뜨거운 물 세안', '뜨거운 물은 피부의 자연적인 유분을 제거해 건조하게 만들 수 있어요.', 'BARRIER', 80, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '새 제품 바로 얼굴 전체에 사용', '맞지 않는 제품은 피부 자극을 유발할 수 있어요', 'BARRIER', 50, 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('AVOID', '세안 후 피부 세게 문지르기', '강한 마찰은 피부 장벽에 자극을 줄 수 있어요', 'BARRIER', 90, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- BARRIER / DO
('DO', '수분 세럼 바르기', '수분 세럼을 사용해 피부에 수분을 공급해 주세요', 'BARRIER', 80, 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '보습 크림 바르기', '보습 크림은 피부의 수분 손실을 줄이고 촉촉함을 유지하는 데 도움을 줘요', 'BARRIER', 90, 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', 'SPF50 자외선 차단제 도포', '자외선 차단제를 사용해 자외선으로 인한 피부 손상을 예방해 주세요', 'BARRIER', 60, 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DO', '세라마이드 함유 크림 사용', '세라마이드가 함유된 보습제는 피부 장벽을 유지하고 회복하는 데 도움을 줄 수 있어요', 'BARRIER', 50, 4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
