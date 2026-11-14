package com.company.common.uploads;

import com.company.common.dto.ClientContracts.Input;
import com.company.common.dto.ClientContracts.Write;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.ClientMapper;
import com.company.common.mapper.UserMapper;
import com.company.common.service.CurrentBusinessActor;
import com.company.common.service.PaymentAccountCipher;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 호출자의 전량 반영 트랜잭션에 참여한다. 행별 독립 커밋을 만들지 않는다. */
@Service
public class UploadClientWriter {
    private final ClientMapper clients;
    private final UserMapper management;
    private final PaymentAccountCipher cipher;
    private final CurrentBusinessActor actor;

    public UploadClientWriter(ClientMapper clients, UserMapper management,
        PaymentAccountCipher cipher, CurrentBusinessActor actor) {
        this.clients = clients;
        this.management = management;
        this.cipher = cipher;
        this.actor = actor;
    }
    public void lock() {
        management.lockManagement();
        clients.lockClientRelations();
    }
    public void insert(Input input) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        if (clients.insertClient(new Write(id, input, cipher.encrypt(input.accountNumber()), actor.userId(), null)) != 1)
            throw new IllegalStateException("거래처를 등록하지 못했습니다.");
        management.insertHistory(new History("TB_BS_CLNT", id, "NEW", "신규 등록", null, "신규 등록"));
    }
}
