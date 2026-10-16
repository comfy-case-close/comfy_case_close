package com.fnbx.shared.logging;

import java.lang.annotation.*;

/** Logs operation identity/timing only. Arguments and return values are never serialized. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {
    boolean write() default false;
}
