package com.fnbx.cashclose.service.rule;

import com.fnbx.shared.enums.BusinessType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * So dang ky plug-in. Core tra cuu plug-in ma KHONG biet chi tiet tung cai.
 *
 * <p>Spring tu inject moi {@link CashCloseRulePlugin} co trong context — nen
 * them mot class moi la du, khong phai dang ky o dau ca.
 */
@Component
public class RuleRegistry {

    private final Map<BusinessType, CashCloseRulePlugin> byType;
    private final CashCloseRulePlugin fallback;

    public RuleRegistry(List<CashCloseRulePlugin> plugins) {
        this.byType = plugins.stream()
                .collect(Collectors.toMap(CashCloseRulePlugin::businessType, Function.identity()));
        this.fallback = byType.get(BusinessType.CAFE);
    }

    /**
     * Tra plug-in cho loai hinh. Neu chua co plug-in rieng, dung luat ca phe
     * (tap luat co ban nhat) thay vi nem loi — mot khach hang loai
     * BAKERY khong nen bi chan chi vi ta chua viet plug-in cho ho.
     */
    public CashCloseRulePlugin forBusinessType(BusinessType type) {
        return byType.getOrDefault(type, fallback);
    }

    public int registeredCount() { return byType.size(); }
}
