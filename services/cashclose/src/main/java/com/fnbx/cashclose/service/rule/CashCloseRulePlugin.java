package com.fnbx.cashclose.service.rule;

import com.fnbx.cashclose.enums.RiskLevel;
import com.fnbx.shared.enums.BusinessType;

/**
 * HOP DONG CO DINH cua tang luat — mau Microkernel (sach Ch.12).
 *
 * <h2>Vi sao Microkernel dung o day</h2>
 * Sach Ch.12 tr.159 mo ta <i>domain/architecture isomorphism</i> qua vi du bao
 * hiem: quy trinh boi thuong la core, <b>luat cua tung bang la plug-in</b>,
 * va canh bao rang nhet het vao mot rules engine se thanh <i>big ball of mud</i>.
 *
 * <p>F&amp;B Nexus co dung hinh dang do:
 * <table>
 *   <caption>Anh xa</caption>
 *   <tr><th>Sach — bao hiem</th><th>F&amp;B Nexus</th></tr>
 *   <tr><td>Quy trinh boi thuong (core)</td>
 *       <td>Quy trinh ket ca: dem ket → giai trinh → nop → duyet</td></tr>
 *   <tr><td>Luat rieng tung bang (plug-in)</td>
 *       <td>Luat rieng tung LOAI HINH (ca phe / pub / nha hang)</td></tr>
 * </table>
 *
 * <p>Cu the: quan ca phe tinh tip khac pub (pub co the chia tip theo gio, co
 * phi phuc vu); nha hang co tien coc ban; pub ban ruou theo chai can kiem ke
 * ca khac han. Nhet het vao {@code CashCloseService} bang
 * {@code if (businessType == PUB)} thi dung la big ball of mud.
 *
 * <p><b>Them loai hinh moi = them MOT class.</b> Core khong sua mot dong.
 */
public interface CashCloseRulePlugin {

    /** Loai hinh kinh doanh ma plug-in nay phu trach. */
    BusinessType businessType();

    /** Kiem tra truoc khi cho phep nop phieu. */
    ValidationResult validate(CashCloseContext ctx);

    /** Assess the risk level of a close. */
    RiskLevel assessRisk(CashCloseContext ctx);
}
