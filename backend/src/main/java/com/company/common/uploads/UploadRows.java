package com.company.common.uploads;

import java.time.Instant;

public final class UploadRows {
    private UploadRows() {}
    public static class Upload {
        public long uploadId;
        public String formTypeCode;
        public String fileName;
        public String statusCode;
        public String statusName;
        public int successCount;
        public int warningCount;
        public int errorCount;
        public Instant modifiedAt;
    }
    public static class Issue {
        public int row;
        public String column;
        public String inputValue;
        public String reason;
        public String severity;
    }
}
