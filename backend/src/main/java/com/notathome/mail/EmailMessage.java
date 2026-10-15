package com.notathome.mail;

/** A rendered email: the plain-text and HTML bodies carry the same content. */
public record EmailMessage(String subject, String text, String html) {}
