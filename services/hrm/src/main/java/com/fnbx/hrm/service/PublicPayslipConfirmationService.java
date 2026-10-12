package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.ConfirmationViewResponse;
import com.fnbx.hrm.service.confirmation.ClientInfo;

/** Operations reachable from the e-mailed link without signing in; every failure looks the same. */
public interface PublicPayslipConfirmationService {

    ConfirmationViewResponse view(String token, ClientInfo client);

    ConfirmationViewResponse confirm(String token, ClientInfo client);

    ConfirmationViewResponse dispute(String token, String note, ClientInfo client);
}
