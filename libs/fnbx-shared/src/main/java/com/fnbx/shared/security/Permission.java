package com.fnbx.shared.security;

/** Fixed API capabilities; business grants never imply branch grants. */
public enum Permission {
 CLOSE_READ(Scope.BRANCH),
 CLOSE_OPEN(Scope.BRANCH),
 CLOSE_EDIT(Scope.BRANCH),
 CLOSE_SUBMIT(Scope.BRANCH),
 CLOSE_REVIEW(Scope.BRANCH),
 CLOSE_VOID(Scope.BRANCH),
 DENOMINATION_WRITE(Scope.BRANCH),
 MOVEMENT_ADD(Scope.BRANCH),
 MOVEMENT_REVIEW(Scope.BRANCH),
 WITHDRAWAL_RECORD(Scope.BRANCH),
 FINANCE_READ(Scope.BRANCH),
 REPORT_READ(Scope.BRANCH),
 CONFIG_WRITE(Scope.BRANCH),
 BRANCH_CREATE(Scope.BUSINESS),
 BRANCH_DEACTIVATE(Scope.BUSINESS),
 STAFF_ASSIGN(Scope.BUSINESS),
 JOIN_REQUEST_DECIDE(Scope.BUSINESS),
 BUSINESS_UPDATE(Scope.BUSINESS),
 PERMISSION_GRANT(Scope.BUSINESS);
 public enum Scope { BRANCH, BUSINESS }
 private final Scope scope;
 Permission(Scope scope) { this.scope = scope; }
 public Scope scope() { return scope; }
}
