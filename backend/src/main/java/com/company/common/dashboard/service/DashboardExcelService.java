package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.dashboard.mapper.DashboardMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardExcelService {
    private final CommonDashboardService dashboard;
    private final DashboardMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public DashboardExcelService(CommonDashboardService dashboard, DashboardMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.dashboard = dashboard; this.mapper = mapper; this.excel = excel; this.actor = actor; this.calendar = calendar;
    }
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonDashboardExcel(String yearMonth) {
        var data = dashboard.getCommonDashboard(yearMonth).data();
        var rows = rows(data);
        var now = calendar.now();
        byte[] bytes = excel.write("대시보드", List.of("영역", "항목", "기간", "지표", "값"), rows, actor.userId(), now);
        var month = CommonDashboardService.month(yearMonth);
        var name = excel.fileName("대시보드", month.getYear() + "년 " + month.getMonthValue() + "월", now);
        mapper.insertAccess(actor.userId(), "대시보드 " + yearMonth + " " + rows.size() + "건");
        return new ExcelFile(bytes, name, rows.size());
    }
    static List<List<String>> rows(Result data) {
        var rows = new ArrayList<List<String>>();
        var s = data.summary();
        String month = data.selectedMonth(), today = data.today().toString();
        // 파트장·PM 모양에서 숨긴 칸(null)은 엑셀에도 넣지 않는다(명세 ⑧-4·⑨-2).
        if (s != null) {
        add(rows, "요약", "매출", month, "금액", s.revenue());
        add(rows, "요약", "매출", month, "전월 대비(%)", s.revenuePreviousComparison());
        add(rows, "요약", "매출", month, "목표 달성률(%)", s.revenueTargetRate());
        add(rows, "요약", "영업이익", month, "금액", s.operatingProfit());
        add(rows, "요약", "영업이익", month, "이익률(%)", s.operatingProfitRate());
        add(rows, "요약", "영업이익", month, "전월 대비(%)", s.operatingProfitPreviousComparison());
        add(rows, "요약", "누계 영업이익", month, "금액", s.cumulativeOperatingProfit());
        add(rows, "요약", "누계 영업이익", month, "달성률(%)", s.cumulativeAchievementRate());
        add(rows, "요약", "가동률", month, "비율(%)", s.utilizationRate());
        add(rows, "요약", "가동률", month, "목표(%)", s.utilizationTargetRate());
        add(rows, "요약", "가동률", month, "대기 인원", s.idlePeople());
        add(rows, "요약", "미수금", today, "금액", s.unpaidAmount());
        add(rows, "요약", "미수금", today, "미수 건수", s.unpaidCount());
        add(rows, "요약", "미수금", today, "연체 건수", s.overdueCount());
        add(rows, "요약", "미수금", today, "연체 금액", s.overdueAmount());
        }
        // 화면에 보이는 줄만(0건 줄은 시안처럼 뺀다).
        for (var t : data.todos()) if (t.visible()) add(rows, "확인할 일", t.title(),
            "billingMissing".equals(t.key()) ? month : today, t.description(), t.count());
        if (data.closing() != null) {
            add(rows, "마감", "점검", month, "완료 수", data.closing().completedCount());
            add(rows, "마감", "점검", month, "전체 수", data.closing().totalCount());
            for (var item : data.closing().remainingItems()) add(rows, "마감", item.name(), month, "남은 항목", item.description());
        }
        for (var a : data.projectAlerts()) {
            add(rows, "프로젝트 경보", a.projectName(), today, a.alertName(), a.mainCause());
            add(rows, "프로젝트 경보", a.projectName(), today, a.marginLabel() + "(%)", a.marginRate());
            add(rows, "프로젝트 경보", a.projectName(), today, "예산 소진(%)", a.budgetBurnRate());
            if (a.mainCauseAmount() != null) add(rows, "프로젝트 경보", a.projectName(), today, "주원인 금액", a.mainCauseAmount());
        }
        // 숫자 표와 같게 매출·원가는 고른 달까지, 목표 매출도 그 달까지만 적는다(그래프 점선은 화면에서만 12달).
        for (var t : data.trend()) {
            if (t.revenue() == null && t.cost() == null && t.month().compareTo(month) > 0) continue;
            String scope = "part".equals(data.dashMode()) ? "자기 파트" : "전체";
            add(rows, "추이", scope, t.month(), "매출", t.revenue());
            add(rows, "추이", scope, t.month(), "원가", t.cost());
            add(rows, "추이", scope, t.month(), "목표 매출", t.targetRevenue());
        }
        for (var m : data.manpower()) {
            add(rows, "인력 수급", "회사 전체", m.month(), "가용", m.supply());
            add(rows, "인력 수급", "회사 전체", m.month(), "필요", m.demand());
            add(rows, "인력 수급", "회사 전체", m.month(), "차이", m.difference());
        }
        for (var p : data.salesParts()) {
            add(rows, "영업 파트", p.partName(), month, "매출", p.revenue());
            add(rows, "영업 파트", p.partName(), month, "이익률(%)", p.profitRate());
        }
        if (data.pipeline() != null) {
            add(rows, "파이프라인", "진행", today, "기회 수", data.pipeline().activeCount());
            add(rows, "파이프라인", "진행", today, "확률 반영 예상 매출", data.pipeline().weightedExpectedRevenue());
            for (var p : data.pipeline().upcoming()) add(rows, "파이프라인", p.opportunityName(), p.startMonth(),
                "곧 시작 · " + (p.stageName() == null ? "" : p.stageName()) + " 확률(%)", p.probability());
        }
        // 항목은 다른 영역처럼 고정 이름, 부족 경고 글은 지표 뒤에 붙인다(「월말 잔액 · 경고」).
        for (var c : data.cash()) add(rows, "자금 전망", "회사 전체", c.month(),
            c.warning() == null ? "월말 잔액" : "월말 잔액 · " + c.warning(), c.monthEndBalance());
        return rows;
    }
    private static void add(List<List<String>> rows, String region, String item, String period, String metric, Object value) {
        // 값 열에는 숫자/빈칸만 두어 공통 내보내기가 실제 숫자 셀로 생성하게 한다.
        String label = value instanceof String text ? metric + " · " + text : metric;
        rows.add(List.of(region, item == null ? "" : item, period, label,
            value instanceof Number ? value.toString() : ""));
    }
}
