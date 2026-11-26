package com.company.common.aianalysis.service;

import java.util.List;

/** 명세 「질문 종류(조회 함수) 목록」 그대로. F-08(지급 현황)은 삭제되어 없다. 함수를 늘리면 명세 표와 여기에 한 줄씩 더한다. */
public final class AiAnalysisCatalog {
    private AiAnalysisCatalog() {}
    public record Entry(String id, String name, List<String> inputs, String sourceScreenId) {}
    public static final List<Entry> FUNCTIONS = List.of(
        new Entry("F-01", "회사 손익", List.of("항목", "기간", "전년 비교 여부"), "UI-08"),
        new Entry("F-02", "파트 이익금", List.of("영업 파트", "기간"), "UI-19"),
        new Entry("F-03", "프로젝트 손익", List.of("프로젝트", "기간"), "UI-04"),
        new Entry("F-04", "개인 손익·적자 인력", List.of("연월", "조건", "직무"), "UI-03"),
        new Entry("F-05", "사업계획 달성률", List.of("기간", "항목"), "UI-18"),
        new Entry("F-06", "청구·미수", List.of("거래처·프로젝트", "기준일"), "UI-13"),
        new Entry("F-07", "자금 현황", List.of("기간"), "UI-21"),
        new Entry("F-09", "인력 현황", List.of("연월", "직무·등급"), "UI-28"),
        new Entry("F-10", "철수 예정", List.of("기간", "다음 배정 없음만 여부"), "UI-05"),
        new Entry("F-11", "프로젝트 상태", List.of("신호등", "프로젝트"), "UI-31"),
        new Entry("F-12", "영업 기회", List.of("단계", "영업 파트"), "UI-29"),
        new Entry("P-01", "매출·영업이익 예측", List.of("기간"), "UI-29"),
        new Entry("P-02", "턴키 최종 마진율", List.of("프로젝트"), "UI-04"),
        new Entry("P-03", "자금 잔액 예측", List.of("기간"), "UI-21"),
        new Entry("P-04", "인력 과부족", List.of("기간", "직무"), "UI-17"),
        new Entry("P-05", "미수 연체 위험", List.of("기준일"), "UI-13"));
}
