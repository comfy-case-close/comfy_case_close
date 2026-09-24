package com.fnbx.shared.security;

/**
 * The header that names the branch a caller is acting in.
 *
 * <p>It is a SCOPE SELECTOR, never a grant. The gateway strips
 * {@code X-Business-Id}, {@code X-User-Id}, {@code X-Role} and {@code X-Staff-Id}
 * because a client that can name its own identity has no security at all. This
 * header is different in kind: it says <i>which</i> branch the caller means, and
 * the service then proves the caller may act there - against the live assignment
 * in {@code identity.staff_branch_role}, not against anything the client sent.
 *
 * <p>So it is forwarded rather than stripped, and it is safe only for as long as
 * every endpoint that reads it also verifies it. There is no code path that
 * trusts this value on its own.
 *
 * <p>It must also appear in the CORS allow-list
 * ({@link ServletSecurityConfiguration#cors}) or the browser preflight fails
 * before the request is ever made.
 */
public final class BranchHeader {
    private BranchHeader() {}

    public static final String NAME = "X-Branch-Id";
}
