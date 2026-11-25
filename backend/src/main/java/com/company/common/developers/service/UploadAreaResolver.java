package com.company.common.developers.service;

import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.developers.mapper.DeveloperMapper;
import com.company.common.developers.mapper.DeveloperRows;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 엑셀 업로드의 행정구역 세 칸(시·도, 시·군·구, 동)을 고른 마지막 단계의 코드로 바꾼다(SFR-021-04).
 * 개발자 거주지(B.8)와 프로젝트 근무지(B.9, 2026-11-25)가 같은 규칙을 쓴다.
 * 시·도 정식·줄임 이름을 같이 보고, 행정동은 법정동으로 바꾸며, 못 찾으면 찾은 단계까지만 저장하고 경고(오류 아님).
 */
@Component
public class UploadAreaResolver {
    /** 경고 한 건: 칸 번호(시·도 칸 기준 +0·+1·+2), 입력값, 이유, 결과 창의 칸 이름(예: 「거주지(시·도)」). */
    public interface Warnings {
        void warn(int column, String value, String reason, String label);
    }

    /** 합쳐지기 전 이름도 같은 시·도로 본다(광주·전남 → 전남광주, 2026-11-02). */
    private static final Set<String> MERGED = Set.of("광주광역시", "광주시", "광주", "전라남도", "전남");
    private final DeveloperMapper mapper;

    public UploadAreaResolver(DeveloperMapper mapper) {
        this.mapper = mapper;
    }

    private List<DeveloperRows.Area> sido(String text) {
        String name = MERGED.contains(text) ? "전남광주" : text;
        var rows = mapper.selectAreasByName("SIDO", null, name);
        // 「줄임 이름+시」(서울시 → 서울)
        if (rows.isEmpty() && name.endsWith("시") && name.length() > 1)
            rows = mapper.selectAreasByName("SIDO", null, name.substring(0, name.length() - 1));
        return rows;
    }

    /**
     * subject: 「거주지」·「근무지」. sidoColumn: 시·도 칸 번호(시·군·구·동은 바로 뒤 두 칸).
     * 세 칸이 모두 비면 경고 없이 null(지역 없음).
     */
    public String resolve(String sidoText, String sggText, String dongText, int sidoColumn, String subject, Warnings warnings) {
        String sido = sidoText == null ? "" : sidoText.trim();
        String sgg = sggText == null ? "" : sggText.trim();
        String dong = dongText == null ? "" : dongText.trim();
        if (sido.isEmpty()) {
            if (!sgg.isEmpty() || !dong.isEmpty())
                warnings.warn(sidoColumn, "(빈칸)", subject + "를 못 찾아 비웠습니다.", subject + "(시·도)");
            return null;
        }
        var sidoRows = sido(sido);
        if (sidoRows.isEmpty()) {
            warnings.warn(sidoColumn, sido, subject + "를 못 찾아 비웠습니다.", subject + "(시·도)");
            return null;
        }
        var top = sidoRows.get(0);
        if (sgg.isEmpty()) {
            if (!dong.isEmpty()) return emd(top.areaCode, top.areaName, dong, sidoColumn + 2, subject, warnings);
            return top.areaCode;
        }
        var sggRows = mapper.selectAreasByName("SGG", top.areaCode, sgg);
        if (sggRows.isEmpty()) {
            warnings.warn(sidoColumn + 1, sgg, "시·군·구를 못 찾아 비웠습니다. 「" + top.areaName + "」까지 저장합니다.",
                subject + "(시·군·구)");
            return top.areaCode;
        }
        var middle = sggRows.get(0);
        if (dong.isEmpty()) return middle.areaCode;
        return emd(middle.areaCode, middle.areaName, dong, sidoColumn + 2, subject, warnings);
    }

    private String emd(String parent, String parentName, String dong, int column, String subject, Warnings warnings) {
        var direct = mapper.selectAreasByName("EMD", parent, dong);
        if (direct.size() == 1) return direct.get(0).areaCode;
        String normalized = dong.replaceAll("제([0-9])", "$1");
        var mapped = mapper.selectHdongAreas(parent, normalized);
        if (mapped.size() == 1) {
            warnings.warn(column, dong, CodeTextResolver.readAs(mapped.get(0).areaName), subject + "(동)");
            return mapped.get(0).areaCode;
        }
        warnings.warn(column, dong, "동을 못 찾아 비웠습니다. 「" + parentName + "」까지 저장합니다.", subject + "(동)");
        return parent;
    }
}
