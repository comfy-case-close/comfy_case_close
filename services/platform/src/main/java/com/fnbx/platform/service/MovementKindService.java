package com.fnbx.platform.service;

import com.fnbx.shared.logging.LogOperation;
import com.fnbx.platform.dto.response.MovementKindResponse;
import com.fnbx.platform.repository.MovementKindRepository;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.tenant.TenantContext;
import com.fnbx.shared.utils.PagedResponse;
import com.fnbx.shared.utils.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovementKindService {
    private final MovementKindRepository kinds;
    private final BranchAccessGuard access;

    @Transactional(readOnly = true)
    @LogOperation
    public PagedResponse<MovementKindResponse> list(Pageable pageable) {
        access.requireActiveStaff();
        return PaginationUtils.toPagedResponse(
                kinds.findAllVisible(TenantContext.current().businessId(), pageable),
                kind -> new MovementKindResponse(kind.getKindSk(), kind.getKindCode(), kind.getBusinessId(),
                        kind.getValidFrom(), kind.getValidTo(), kind.getEffectType(), kind.isAffectsDifference(),
                        kind.isAffectsRemaining(), kind.getExpenseCategory(), kind.getDiffReasonGroup(),
                        kind.isRequiresReceipt(), kind.isRequiresNote(), kind.getDisplayName()));
    }
}
