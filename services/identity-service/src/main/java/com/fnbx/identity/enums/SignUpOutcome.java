package com.fnbx.identity.enums;

/**
 * What happened at the end of signup.
 *
 * <p>One endpoint has two endings because the caller cannot know in advance which
 * applies, and telling them beforehand would leak whether an address already works
 * at that business. The client branches on this value, not on the HTTP status
 * alone.
 */
public enum SignUpOutcome {

    /** The address belonged to a provisioned account; it is now active and signed in. */
    SESSION_ISSUED,

    /** The address was unknown; a join request is waiting for an ADMIN. */
    PENDING_APPROVAL
}
