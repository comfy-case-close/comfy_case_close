package com.fnbx.hrm.service.contractimport;

public record ImportIssue(Severity severity, String code, String message) {

    public enum Severity { ERROR, WARNING }

    public static ImportIssue error(String code, String message) {
        return new ImportIssue(Severity.ERROR, code, message);
    }

    public static ImportIssue warning(String code, String message) {
        return new ImportIssue(Severity.WARNING, code, message);
    }
}
