package com.fnbx.cashclose.service;

import com.fnbx.cashclose.dto.response.CashMovementResponse;
import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.entity.CashMovement;
import com.fnbx.cashclose.mapper.CashCloseMapper;
import com.fnbx.files.entity.StoredFile;
import com.fnbx.platform.entity.MovementKind;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds ledger lines with everything a client shows next to them: the kind's
 * meaning, the close's day, shift and status, and the receipt file's details.
 *
 * <p>Kinds, closes and files are loaded in one query each for the whole list, so
 * a page costs at most three extra queries however many lines it holds - and the
 * client never has to fetch each line's close to label it.
 */
@Component
@RequiredArgsConstructor
public class MovementResponseAssembler {

    private final CashCloseMapper mapper;
    private final EntityManager entityManager;

    public CashMovementResponse toResponse(CashMovement movement) {
        return toResponses(List.of(movement)).getFirst();
    }

    public List<CashMovementResponse> toResponses(List<CashMovement> movements) {
        if (movements.isEmpty()) return List.of();
        Map<Long, MovementKind> kinds = load(MovementKind.class, "kindSk",
                ids(movements, CashMovement::getKindSk), MovementKind::getKindSk);
        Map<UUID, CashClose> closes = load(CashClose.class, "cashCloseId",
                ids(movements, CashMovement::getCashCloseId), CashClose::getCashCloseId);
        Map<UUID, StoredFile> files = load(StoredFile.class, "fileId",
                ids(movements, CashMovement::getReceiptFileId), StoredFile::getFileId);
        return movements.stream()
                .map(movement -> assemble(movement, lookup(kinds, movement.getKindSk()),
                        lookup(closes, movement.getCashCloseId()), lookup(files, movement.getReceiptFileId())))
                .toList();
    }

    private CashMovementResponse assemble(CashMovement movement, MovementKind kind,
                                          CashClose close, StoredFile receipt) {
        CashMovementResponse response = mapper.toResponse(movement, kind);
        if (close != null) {
            response.setBranchId(close.getBranchId());
            response.setBusinessDate(close.getBusinessDate());
            response.setShiftTypeId(close.getShiftTypeId());
            response.setCloseStatus(close.getStatus().name());
        }
        if (receipt != null) {
            response.setReceiptPublicUrl(receipt.getPublicUrl());
            response.setFileKind(receipt.getFileKind().name());
            response.setProvider(receipt.getStorageProvider());
            response.setContentType(receipt.getContentType());
            response.setUploadedBy(receipt.getUploadedBy());
            response.setUploadedAt(receipt.getUploadedAt());
        }
        return response;
    }

    /** Most lines have no receipt: a null id finds nothing rather than throwing. */
    private static <K, E> E lookup(Map<K, E> loaded, K id) {
        return id == null ? null : loaded.get(id);
    }

    private static <K> Set<K> ids(List<CashMovement> movements, Function<CashMovement, K> id) {
        return movements.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <E, K> Map<K, E> load(Class<E> type, String idField, Collection<K> ids, Function<E, K> id) {
        if (ids.isEmpty()) return Map.of();
        return entityManager.createQuery(
                        "SELECT e FROM " + type.getSimpleName() + " e WHERE e." + idField + " IN :ids", type)
                .setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(id, Function.identity()));
    }
}
