package com.fnbx.hrm.service.employee;

import java.util.List;

public record ProfileCompleteness(int percent, List<String> missingFields) {
}
