package com.fnbx.files;

import com.fnbx.files.config.GcsStorageProperties;
import com.fnbx.files.entity.StoredFile;
import com.fnbx.files.repository.StoredFileRepository;
import com.fnbx.files.service.FileService;
import com.fnbx.files.storage.FileObjectStorage;
import com.fnbx.shared.enums.FileKind;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileServiceTest {
    private final StoredFileRepository repository = mock(StoredFileRepository.class);
    private final FileObjectStorage storage = mock(FileObjectStorage.class);
    private final BranchAccessGuard access = mock(BranchAccessGuard.class);
    private final GcsStorageProperties config = new GcsStorageProperties();
    private final FileService service = new FileService(repository, storage, config, access);
    private final UUID businessId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void uploadsPrivateFileWithTenantScopedKeyAndReturnsOnlyMetadata() {
        TenantContext.set(TenantContext.of(businessId, staffId));
        config.setObjectPrefix("case-close");
        config.setMaxFileSizeBytes(1024);
        var file = new MockMultipartFile("file", "../proof.jpg", "image/jpeg", new byte[]{1, 2, 3});

        var response = service.upload(branchId, file, FileKind.RECEIPT);

        assertThat(response.fileId()).isNotNull();
        assertThat(response.fileName()).isEqualTo("proof.jpg");
        verify(storage).put(contains("case-close/" + businessId + "/"), any(), eq("image/jpeg"));
        verify(repository).saveAndFlush(argThat(stored -> stored.getBusinessId().equals(businessId)
                && stored.getBranchId().equals(branchId)
                && stored.getStorageProvider().equals("GCS")
                && stored.getSha256().length() == 64));
        verify(access).require(branchId, Permission.CLOSE_EDIT);
    }

    @Test
    void signsOnlyFilesBelongingToTheSelectedBranch() {
        TenantContext.set(TenantContext.of(businessId, staffId));
        UUID fileId = UUID.randomUUID();
        StoredFile file = new StoredFile();
        file.setFileId(fileId);
        file.setBusinessId(businessId);
        file.setBranchId(branchId);
        file.setStorageKey("case-close/key");
        when(repository.findById(fileId)).thenReturn(Optional.of(file));
        when(storage.signedReadUrl("case-close/key")).thenReturn("https://example.com/signed");

        assertThat(service.viewUrl(branchId, fileId).url()).isEqualTo("https://example.com/signed");
        verify(access).require(branchId, Permission.CLOSE_READ);
    }
}
