package com.fnbx.cashclose.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The complete denomination count for a close.
 *
 * <h2>Why PUT and why the whole set</h2>
 * A count is one act: somebody opened the drawer and counted what was in it. It is
 * not a stream of increments. PATCHing one line would let two half-counts from two
 * people merge into a total nobody ever saw.
 *
 * <p>This also removes the exact failure Comfy's real data contained. In the old
 * spreadsheet 16 of 181 closes had a stored total that disagreed with their own
 * count, because a re-count APPENDED rows instead of replacing them. Replace-the-set
 * cannot append.
 *
 * <p>An empty list is legal and clears the count. It does not corrupt anything: a
 * close with no lines simply cannot be submitted, which both the service and the
 * {@code fn_close_before_update} trigger already enforce.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplaceDenominationsRequest {

    @NotNull(message = "counts is required; send an empty list to clear the count")
    private List<@Valid DenominationCountRequest> counts;
}
