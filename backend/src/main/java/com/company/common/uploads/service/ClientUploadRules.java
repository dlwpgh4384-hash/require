package com.company.common.uploads.service;

import com.company.common.clients.dto.ClientContracts.Input;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.clients.mapper.ClientMapper;
import com.company.common.clients.service.ClientPartPolicy;
import com.company.common.clients.service.ClientRules;
import com.company.common.support.service.CodeFailure;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.support.service.CurrentBusinessActor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import static com.company.common.uploads.dto.UploadContracts.*;

@Service
public class ClientUploadRules {
    public static final List<String> HEADERS = List.of("구분", "상호", "사업자등록번호", "대표자", "업종",
        "담당자", "연락처", "이메일", "은행", "계좌번호", "예금주");
    private final ClientMapper clients;
    private final CodeTextResolver resolver;
    private final CurrentBusinessActor actor;

    public ClientUploadRules(ClientMapper clients, CodeTextResolver resolver, CurrentBusinessActor actor) {
        this.clients = clients;
        this.resolver = resolver;
        this.actor = actor;
    }
    public void form(String code) {
        if (!"B7".equals(code) || !clients.codeExists("FORM_TYPE", code))
            throw CodeFailure.invalid("formTypeCode", "거래처 B.7 양식을 선택해 주세요.");
    }
    public Inspection inspect(List<TabularRow> rows) {
        var frequencies = new HashMap<String, Integer>();
        for (var row : rows) frequencies.merge(row.values().get(2).trim(), 1, Integer::sum);
        var issues = new ArrayList<Issue>();
        var inputs = new ArrayList<Input>();
        int success = 0, warnings = 0, errors = 0;
        for (var row : rows) {
            var local = new ArrayList<Issue>();
            var values = row.values().stream().map(String::trim).toList();
            for (int index : row.formulaColumns()) issue(local, row, index, "수식은 사용할 수 없습니다.", "오류");
            String type = resolve(row, 0, "CLNT_TYPE", local);
            String industry = resolve(row, 4, "INDUTY", local);
            String bank = resolve(row, 8, "BANK", local);
            boolean customer = ClientRules.customer(type), vendor = ClientRules.vendor(type);
            required(local, row, 0);
            required(local, row, 1);
            required(local, row, 2);
            if (type != null && !ClientPartPolicy.canCreateClient(actor.roles(), type))
                issue(local, row, 0, "이 구분을 저장할 권한이 없습니다.", "오류");
            if (customer) required(local, row, 7);
            if (vendor) {
                required(local, row, 8);
                required(local, row, 9);
                required(local, row, 10);
            }
            int[] lengths = {100, 50, 12, 20, 100, 20, 20, 100, 100, 20, 30};
            for (int column = 0; column < lengths.length; column++) {
                String value = values.get(column);
                if (value.codePointCount(0, value.length()) > lengths[column])
                    issue(local, row, column, lengths[column] + "자 이내로 입력해 주세요.", "오류");
            }
            String number = values.get(2);
            if (!number.isEmpty() && !number.matches("[0-9]{3}-[0-9]{2}-[0-9]{5}"))
                issue(local, row, 2, "사업자등록번호 형식을 확인해 주세요.", "오류");
            if (!number.isEmpty() && frequencies.get(number) > 1)
                issue(local, row, 2, "파일 안에 같은 사업자등록번호가 있습니다.", "오류");
            if (!number.isEmpty() && clients.selectBusinessOwner(number) != null)
                issue(local, row, 2, "이미 등록된 사업자등록번호입니다. 신규 이관만 가능합니다.", "오류");
            if (customer && !values.get(7).isEmpty() && !values.get(7).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                issue(local, row, 7, "이메일 형식을 확인해 주세요.", "오류");
            if (vendor && !values.get(9).isEmpty() && !values.get(9).matches("[0-9-]{8,20}"))
                issue(local, row, 9, "계좌번호는 숫자와 하이픈 8~20자로 입력해 주세요.", "오류");
            boolean bad = local.stream().anyMatch(value -> "오류".equals(value.severity()));
            if (bad) errors++;
            else {
                if (local.isEmpty()) success++; else warnings++;
                inputs.add(new Input(type, values.get(1), number, nullable(values.get(3)), industry,
                    nullable(values.get(5)), nullable(values.get(6)), customer ? nullable(values.get(7)) : null,
                    vendor ? bank : null, vendor ? nullable(values.get(9)) : null,
                    vendor ? nullable(values.get(10)) : null));
            }
            issues.addAll(local);
        }
        return new Inspection(List.copyOf(inputs), List.copyOf(issues), success, warnings, errors, rows.size());
    }
    private String resolve(TabularRow row, int column, String group, List<Issue> issues) {
        String text = row.values().get(column).trim();
        if (text.isEmpty() || row.formulaColumns().contains(column)) return null;
        try {
            var result = resolver.resolve(group, text);
            if (result.warning() != null) issue(issues, row, column, result.warning(), "경고");
            return result.code();
        } catch (CodeFailure failure) {
            issue(issues, row, column, failure.getMessage(), "오류");
            return null;
        }
    }
    private static String nullable(String value) { return value.isEmpty() ? null : value; }
    private void required(List<Issue> issues, TabularRow row, int column) {
        if (row.values().get(column).isBlank()) issue(issues, row, column, "필수 입력입니다.", "오류");
    }
    private void issue(List<Issue> issues, TabularRow row, int column, String reason, String severity) {
        // 계좌 원문은 검사 기록·응답·내려받기에도 보존하지 않는다.
        String value = column == 9 ? "(계좌번호 숨김)" : row.values().get(column);
        issues.add(new Issue(row.rowNumber(), HEADERS.get(column), clip(value, 200), clip(reason, 300), severity));
    }
    private static String clip(String value, int limit) {
        return value.codePointCount(0, value.length()) <= limit ? value
            : value.substring(0, value.offsetByCodePoints(0, limit));
    }
}
