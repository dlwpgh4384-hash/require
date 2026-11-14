package com.company.common.clients.service;

import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.clients.dto.ClientContracts.*;
import com.company.common.users.dto.UserContracts.History;
import com.company.common.clients.mapper.ClientMapper;
import com.company.common.clients.mapper.ClientRows;
import com.company.common.users.mapper.UserMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class CommonClientService {
    private final ClientMapper mapper;
    private final UserMapper management;
    private final PaymentAccountCipher cipher;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    private final TransactionTemplate creation;

    public CommonClientService(ClientMapper mapper, UserMapper management, PaymentAccountCipher cipher,
        CurrentBusinessActor actor, BusinessCalendar calendar, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.management = management;
        this.cipher = cipher;
        this.actor = actor;
        this.calendar = calendar;
        this.creation = new TransactionTemplate(transactions);
        this.creation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Items<Item> getCommonClients(Filter filter) {
        return new Items<>(mapper.selectClients(filter, calendar.today()).stream().map(this::item).toList());
    }
    private ClientRows.Client require(String id) {
        if (id == null || id.isBlank() || id.length() > 20) throw ClientRules.invalid("clientId");
        var row = mapper.selectClient(id, calendar.today());
        if (row == null) throw CodeFailure.invalid("clientId", "거래처를 찾을 수 없습니다.");
        return row;
    }
    public Item getCommonClient(String id) { return item(require(id)); }
    public Items<ClientRows.Assignment> getCommonClientAssignments(String id) {
        require(id);
        return new Items<>(mapper.selectAssignments(id, calendar.today()));
    }
    public Items<ClientRows.Change> getCommonClientChangeHistory(String id) {
        require(id);
        return new Items<>(mapper.selectChanges(id));
    }
    /** 지급 계좌 전체 보기(재무 권한). 시안: [보기]를 누르면 전체가 보이고 접근 기록에 남는다(SER-006). */
    @Transactional
    public Account getCommonClientPaymentAccount(String id) {
        if (!ClientPartPolicy.canReadPaymentAccount(actor.roles()))
            throw CodeFailure.invalid("accountNumber", "지급 계좌 전체 보기 권한이 없습니다.");
        var row = require(id);
        var account = new Account(value(row.bankCode), value(cipher.decrypt(row.encryptedAccount)), value(row.accountHolder));
        mapper.insertAccess(actor.userId(), "ACCT_VIEW", "거래처 계좌번호 보기: " + row.clientName + " (" + id + ")");
        return account;
    }
    /** SFR-011 엑셀 다운로드: 지금 조건의 전체 결과. 다운로드는 접근 기록에 남긴다(SER-006). */
    @Transactional
    public List<Item> getCommonClientsForDownload(Filter filter) {
        var items = mapper.selectClients(filter, calendar.today()).stream().map(this::item).toList();
        mapper.insertAccess(actor.userId(), "DNLD", "거래처 목록 " + items.size() + "건");
        return items;
    }
    public String currentUserId() { return actor.userId(); }
    private String summary(ClientRows.Client row) {
        if (row.encryptedAccount == null) return "";
        try {
            return value(row.bankName) + " " + cipher.lastFour(row.encryptedAccount);
        } catch (IllegalStateException unreadable) {
            // 키가 바뀌었거나 값이 망가진 계좌 하나 때문에 목록 전체가 실패하지 않게 한다. 전체 보기에서는 그대로 오류를 알린다.
            return value(row.bankName) + " 확인 필요";
        }
    }
    private Item item(ClientRows.Client row) {
        return new Item(row.clientId, row.clientTypeCode, row.clientName, row.businessNumber,
            value(row.representativeName), value(row.industryCode), value(row.contactName), value(row.contactPhone),
            value(row.taxEmail), value(row.bankCode), row.useYn,
            row.modifiedAt == null ? null : row.modifiedAt.toString(), summary(row), row.projectCount, row.personCount);
    }
    private static String value(String text) { return text == null ? "" : text; }
    private void allowed(Input input) {
        if (!ClientPartPolicy.canCreateClient(actor.roles(), input.clientTypeCode()))
            throw CodeFailure.invalid("clientTypeCode", "이 구분을 저장할 권한이 없습니다.");
        code("CLNT_TYPE", input.clientTypeCode(), "clientTypeCode");
        code("INDUTY", input.industryCode(), "industryCode");
        code("BANK", input.bankCode(), "bankCode");
    }
    private void code(String group, String code, String field) {
        if (code != null && !mapper.codeExists(group, code)) throw ClientRules.invalid(field);
    }
    private List<String> warnings(Input input, String id) {
        return mapper.selectSameNameNumbers(input.clientName(), id).stream()
            .map(number -> "같은 상호의 거래처가 이미 있습니다(사업자번호 " + number + ")").toList();
    }
    private void lock() {
        management.lockManagement();
        mapper.lockClientRelations();
    }
    private void history(String id, String type, String field, String before, String after) {
        management.insertHistory(new History("TB_BS_CLNT", id, type, field, before, after));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Save postCommonClient(Input input) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            try {
                return creation.execute(status -> {
                    lock();
                    allowed(input);
                    String owner = mapper.selectBusinessOwner(input.businessNumber());
                    if (owner != null) throw CodeFailure.invalid("businessNumber",
                        "이미 등록된 사업자번호입니다 (" + owner + ")");
                    var notices = warnings(input, null);
                    if (mapper.insertClient(new Write(id, input, cipher.encrypt(input.accountNumber()),
                        actor.userId(), null)) != 1) throw new IllegalStateException("거래처를 등록하지 못했습니다.");
                    history(id, "NEW", "신규 등록", null, "신규 등록");
                    return new Save(getCommonClient(id), notices);
                });
            } catch (DuplicateKeyException failure) {
                // The failed transaction has already rolled back before retrying an opaque ID collision.
                if (!primaryKeyCollision(failure) || attempt == 2) throw failure;
            }
        }
        throw new IllegalStateException("거래처를 등록하지 못했습니다.");
    }
    private boolean primaryKeyCollision(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("pk_tb_bs_clnt")) return true;
        }
        return false;
    }

    @Transactional
    public Save putCommonClient(String id, Update update) {
        lock();
        var old = mapper.lockClient(id);
        if (old == null) throw CodeFailure.invalid("clientId", "거래처를 찾을 수 없습니다.");
        if (!Objects.equals(old.modifiedAt, update.modifiedAt())) throw CodeFailure.conflict();
        Input input = update.input();
        allowed(input);
        String plain = cipher.decrypt(old.encryptedAccount);
        String encrypted = Objects.equals(plain, input.accountNumber())
            ? old.encryptedAccount : cipher.encrypt(input.accountNumber());
        var before = fields(old);
        var next = new ClientRows.Client();
        next.clientTypeCode = input.clientTypeCode();
        next.clientName = input.clientName();
        next.representativeName = input.representativeName();
        next.industryCode = input.industryCode();
        next.contactName = input.contactName();
        next.contactPhone = input.contactPhone();
        next.taxEmail = input.taxEmail();
        next.bankCode = input.bankCode();
        next.accountHolder = input.accountHolder();
        next.encryptedAccount = encrypted;
        var after = fields(next);
        boolean accountChanged = !Objects.equals(plain, input.accountNumber());
        boolean holderChanged = !Objects.equals(old.accountHolder, input.accountHolder());
        if (!before.equals(after) || accountChanged || holderChanged) {
            if (mapper.updateClient(new Write(id, input, encrypted, actor.userId(), update.modifiedAt())) != 1)
                throw CodeFailure.conflict();
            before.forEach((field, value) -> {
                if (!Objects.equals(value, after.get(field))) history(id, "UPD", field, value, after.get(field));
            });
            var saved = require(id);
            if (accountChanged) history(id, "UPD", "계좌번호", summary(old), summary(saved));
            if (holderChanged) history(id, "UPD", "예금주", summary(old), summary(saved));
        }
        return new Save(getCommonClient(id), warnings(input, id));
    }
    private LinkedHashMap<String, String> fields(ClientRows.Client row) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("구분", row.clientTypeCode);
        fields.put("상호", row.clientName);
        fields.put("대표자", row.representativeName);
        fields.put("업종", row.industryCode);
        fields.put("담당자", row.contactName);
        fields.put("연락처", row.contactPhone);
        fields.put("세금계산서 이메일", row.taxEmail);
        fields.put("은행", row.bankCode);
        return fields;
    }

    @Transactional
    public StateResult putCommonClientUseStatus(StateInput input) {
        if (input.items() == null || input.items().isEmpty()) throw ClientRules.invalid("items");
        lock();
        var locked = new LinkedHashMap<String, ClientRows.Client>();
        for (var version : input.items().stream().sorted(Comparator.comparing(Version::clientId)).toList()) {
            var row = mapper.lockClient(version.clientId());
            if (row != null && !Objects.equals(row.modifiedAt, version.modifiedAt())) throw CodeFailure.conflict();
            locked.put(version.clientId(), row);
        }
        var changed = new ArrayList<String>();
        var failed = new ArrayList<Rejected>();
        for (var version : input.items()) {
            var row = locked.get(version.clientId());
            String reason = row == null ? "거래처를 찾을 수 없습니다." : null;
            if (row != null && "N".equals(input.useYn())) {
                if (ClientRules.customer(row.clientTypeCode) && mapper.hasOngoingProject(row.clientId))
                    reason = "진행 프로젝트가 있어 중지할 수 없습니다.";
                else if (ClientRules.vendor(row.clientTypeCode) && mapper.hasAffiliatedPerson(row.clientId))
                    reason = "소속 인력이 있어 중지할 수 없습니다. 먼저 소속을 바꿔 주세요.";
            }
            if (reason != null) { failed.add(new Rejected(version.clientId(), reason)); continue; }
            if (Objects.equals(row.useYn, input.useYn())) continue;
            if (mapper.updateUseStatus(new StateWrite(version, input.useYn(), actor.userId())) != 1)
                throw CodeFailure.conflict();
            history(row.clientId, "N".equals(input.useYn()) ? "STOP" : "RESUME", "사용 여부", row.useYn, input.useYn());
            changed.add(row.clientId);
        }
        return new StateResult(List.copyOf(changed), List.copyOf(failed));
    }
}
