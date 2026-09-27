package com.fnbx.cashclose.service.rule;

import java.util.ArrayList;
import java.util.List;

/** Ket qua kiem tra cua mot plug-in luat. */
public final class ValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public static ValidationResult ok() { return new ValidationResult(); }

    public ValidationResult error(String msg)   { errors.add(msg);   return this; }
    public ValidationResult warn(String msg)    { warnings.add(msg); return this; }

    public boolean isValid()       { return errors.isEmpty(); }
    public List<String> errors()   { return List.copyOf(errors); }
    public List<String> warnings() { return List.copyOf(warnings); }
}
