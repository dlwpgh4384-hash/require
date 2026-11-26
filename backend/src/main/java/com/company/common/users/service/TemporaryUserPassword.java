package com.company.common.users.service;

import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TemporaryUserPassword {
    private final com.company.common.bills.service.BillLocalMailbox mailbox;
    public TemporaryUserPassword() {
        this(new com.company.common.bills.service.BillLocalMailbox(null, ""));
    }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public TemporaryUserPassword(com.company.common.bills.service.BillLocalMailbox mailbox) {
        this.mailbox = mailbox;
    }
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private static final String[] GROUPS = {"abcdefghijklmnopqrstuvwxyz", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789", "!@#$%&*+-=?"};

    public String hashAndDeliver(String email) {
        char[] value = new char[16];
        String alphabet = String.join("", GROUPS);
        for (int i = 0; i < value.length; i++) {
            String source = i < GROUPS.length ? GROUPS[i] : alphabet;
            value[i] = source.charAt(random.nextInt(source.length()));
        }
        for (int i = value.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char swap = value[i]; value[i] = value[index]; value[index] = swap;
        }
        try {
            String hash = encoder.encode(new String(value));
            deliver(email, value);
            return hash;
        } finally {
            java.util.Arrays.fill(value, '\0');
        }
    }

    private void deliver(String email, char[] password) {
        var path = mailbox.deliver(java.util.List.of(
            new com.company.common.bills.mapper.BillRows.Recipient("", "", email)),
            "임시 비밀번호 안내\n임시 비밀번호: " + new String(password) + "\n로그인 후 비밀번호를 변경해 주세요.");
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) mailbox.discard(path);
                    }
                });
        }
    }
}
