package com.comfy.caseclose.integration;

/** Transport-independent message with both plain-text and HTML bodies, adapted from Vakot_BE. */
public record EmailMessage(String from, String to, String subject, String text, String html) {}
