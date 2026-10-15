ALTER TABLE conversation_reports DROP CONSTRAINT conversation_report_reason;
ALTER TABLE conversation_reports ADD CONSTRAINT conversation_report_reason
    CHECK (reason IN ('SUSPICIOUS_BEHAVIOR', 'SCAM', 'FRAUD', 'HARASSMENT', 'OTHER'));
