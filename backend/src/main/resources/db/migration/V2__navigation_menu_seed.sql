DO $navigation$
BEGIN
 PERFORM pg_advisory_xact_lock(734901, 1);
 LOCK TABLE TB_SY_MENU IN EXCLUSIVE MODE;
 IF NOT EXISTS (SELECT 1 FROM TB_SY_MENU) THEN
  INSERT INTO TB_SY_MENU (MENU_ID, UPPER_MENU_ID, MENU_NM, SCRN_ID, MENU_LVL, SORT_ORD, USE_YN, PC_ONLY_YN, ICON_NM, LOCK_YN, REG_USER_ID) VALUES
  ('G001', NULL, '대시보드', NULL, 1, 1, 'Y', 'N', 'grid', 'N', 'SYSTEM'),
  ('M001', 'G001', '대시보드', 'UI-16', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G002', NULL, '기준정보', NULL, 1, 2, 'Y', 'N', 'db', 'N', 'SYSTEM'),
  ('M002', 'G002', '거래처·영업 파트', 'UI-15', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M003', 'G002', '개발자 풀', 'UI-02', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G003', NULL, '프로젝트·투입', NULL, 1, 3, 'Y', 'N', 'folder', 'N', 'SYSTEM'),
  ('M004', 'G003', '프로젝트', 'UI-01', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M005', 'G003', '프로젝트 현황판', 'UI-31', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M006', 'G003', '투입 배정', 'UI-12', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M007', 'G003', '철수 리스트', 'UI-05', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M008', 'G003', '인원 계획 대비 투입', 'UI-11', 2, 5, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G004', NULL, '인력 계획', NULL, 1, 4, 'Y', 'N', 'calendar', 'N', 'SYSTEM'),
  ('M009', 'G004', '인력 운영 보드', 'UI-28', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M010', 'G004', '인력 수요 예측', 'UI-17', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M011', 'G004', '영업 파이프라인', 'UI-29', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M012', 'G004', '외부 인력 후보', 'UI-33', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G005', NULL, '매출', NULL, 1, 5, 'Y', 'N', 'receipt', 'N', 'SYSTEM'),
  ('M013', 'G005', '청구·입금', 'UI-13', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G006', NULL, '원가·수익', NULL, 1, 6, 'Y', 'N', 'chart', 'N', 'SYSTEM'),
  ('M014', 'G006', '개인 원가·수익표', 'UI-03', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M015', 'G006', '프로젝트 원가 분석', 'UI-04', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M016', 'G006', '영업 파트 이익금', 'UI-19', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M017', 'G006', '단가 재협상 대상', 'UI-32', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G007', NULL, '재무·자금', NULL, 1, 7, 'Y', 'N', 'wallet', 'N', 'SYSTEM'),
  ('M018', 'G007', '재무 항목', 'UI-10', 2, 1, 'Y', 'Y', NULL, 'N', 'SYSTEM'),
  ('M019', 'G007', '전체 수익 구조', 'UI-07', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M020', 'G007', '영업이익표', 'UI-08', 2, 3, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M021', 'G007', '순이익표', 'UI-09', 2, 4, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M022', 'G007', '사업계획 대비 실적', 'UI-18', 2, 5, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M023', 'G007', '자금수지표', 'UI-21', 2, 6, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G008', NULL, '마감', NULL, 1, 8, 'Y', 'N', 'lock', 'N', 'SYSTEM'),
  ('M024', 'G008', '월 마감', 'UI-14', 2, 1, 'Y', 'Y', NULL, 'N', 'SYSTEM'),
  ('G009', NULL, 'AI', NULL, 1, 9, 'Y', 'N', 'sparkle', 'N', 'SYSTEM'),
  ('M025', 'G009', 'AI 분석 도우미', 'UI-26', 2, 1, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('M026', 'G009', 'AI 인력 추천', 'UI-30', 2, 2, 'Y', 'N', NULL, 'N', 'SYSTEM'),
  ('G010', NULL, '시스템', NULL, 1, 10, 'Y', 'N', 'gear', 'Y', 'SYSTEM'),
  ('M027', 'G010', '사용자', 'UI-23', 2, 1, 'Y', 'Y', NULL, 'Y', 'SYSTEM'),
  ('M028', 'G010', '권한', 'UI-24', 2, 2, 'Y', 'Y', NULL, 'Y', 'SYSTEM'),
  ('M029', 'G010', '메뉴', 'UI-25', 2, 3, 'Y', 'Y', NULL, 'Y', 'SYSTEM'),
  ('M030', 'G010', '공통코드', 'UI-27', 2, 4, 'Y', 'Y', NULL, 'Y', 'SYSTEM');
 END IF;
END;
$navigation$;
