package com.fnbx.cashclose.repository;

import com.fnbx.platform.entity.Denomination;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * The note-and-coin catalogue, read from {@code platform}.
 *
 * <p>Global, not tenant-scoped: {@code platform.denomination} has no
 * {@code business_id} and therefore no RLS policy. Every business counts the same
 * banknotes.
 *
 * <p>Read only, for the same reason as other cross-schema catalog repositories: this
 * service has SELECT on {@code platform} and nothing more.
 */
public interface DenominationRepository extends Repository<Denomination, Short> {

    /** Denominations that may be counted today. */
    List<Denomination> findByCurrencyCodeAndActiveTrue(String currencyCode);

    /**
     * Every denomination, active or not.
     *
     * <p>Used when rendering a count that already exists: a note withdrawn from
     * circulation is deactivated, not deleted, and a close from before that day must
     * still display the line it recorded.
     */
    List<Denomination> findByCurrencyCode(String currencyCode);
}
