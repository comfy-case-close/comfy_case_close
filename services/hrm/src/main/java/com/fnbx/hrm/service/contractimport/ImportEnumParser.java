package com.fnbx.hrm.service.contractimport;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Reads enum cells written either as the enum name or as the Vietnamese label used in the employee sheet. */
@Component
public class ImportEnumParser {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("nam", "MALE"), Map.entry("nu", "FEMALE"),
            Map.entry("doc than", "SINGLE"), Map.entry("da co gia dinh", "MARRIED"), Map.entry("ly di", "DIVORCED"),
            Map.entry("lao dong pho thong", "GENERAL_LABOR"), Map.entry("trung cap", "VOCATIONAL"),
            Map.entry("cao dang", "COLLEGE"), Map.entry("trinh do dai hoc", "UNIVERSITY"), Map.entry("dai hoc", "UNIVERSITY"),
            Map.entry("tien si", "DOCTORATE"),
            Map.entry("xuat sac", "EXCELLENT"), Map.entry("gioi", "GOOD"), Map.entry("kha", "FAIR"),
            Map.entry("trung binh kha", "AVERAGE_FAIR"), Map.entry("trung binh", "AVERAGE"),
            Map.entry("coreteam", "CORE_TEAM"), Map.entry("in-house", "IN_HOUSE"),
            Map.entry("full time", "FULLTIME"), Map.entry("part time", "PARTTIME"),
            Map.entry("thoa thuan thu viec", "PROBATION"),
            Map.entry("hop dong xac dinh thoi gian (full-time)", "FIXED_TERM_FT"),
            Map.entry("hop dong khong xac dinh thoi han (full time)", "INDEFINITE_FT"),
            Map.entry("thoa thuan lam viec (part-time)", "PART_TIME_AGREEMENT"),
            Map.entry("hop dong dich vu", "SERVICE"),
            Map.entry("dat", "PASSED"), Map.entry("khong dat", "FAILED"),
            Map.entry("head / director", "HEAD_DIRECTOR"), Map.entry("e", "ENTRY_LEVEL"), Map.entry("j", "JUNIOR"),
            Map.entry("s", "SENIOR"), Map.entry("sp", "SPECIALIST"), Map.entry("tl", "TEAM_LEADER"),
            Map.entry("sv", "SUPERVISOR"), Map.entry("jm", "JUNIOR_MANAGER"), Map.entry("sm", "SENIOR_MANAGER"),
            Map.entry("hd", "HEAD_DIRECTOR"), Map.entry("m", "MANAGER"));

    public <E extends Enum<E>> Optional<E> parse(Class<E> type, String cell) {
        if (cell == null || cell.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(cell);
        String target = ALIASES.getOrDefault(normalized, cell.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(target) || normalize(constant.name().replace('_', ' ')).equals(normalized)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }

    private String normalize(String text) {
        String ascii = Normalizer.normalize(text.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return ascii.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
