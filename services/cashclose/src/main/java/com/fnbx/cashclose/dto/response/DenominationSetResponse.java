package com.fnbx.cashclose.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A close's complete denomination count, with the total it produces.
 *
 * <p><b>{@code countedCash} is read back from {@code cashclose.v_close_calc}, not
 * added up in Java.</b> The client needs the number immediately after a PUT, and
 * the honest way to give it is to ask the same view the rest of the system reads.
 * Summing the lines here would be a second implementation of counted cash - which
 * is the precise thing this schema removed the column to avoid.
 *
 * <p>{@code status} rides along because the client's next decision depends on it:
 * a DRAFT can still be recounted, anything else cannot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DenominationSetResponse {

    private UUID cashCloseId;
    private String status;
    private List<DenominationLineResponse> lines;
    private BigDecimal countedCash;
}
