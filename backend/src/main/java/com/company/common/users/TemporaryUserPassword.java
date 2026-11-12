package com.company.common.users;

import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TemporaryUserPassword {
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

    // In-process delivery boundary only: no transport, storage or logging.
    private void deliver(String email, char[] password) {}
}
