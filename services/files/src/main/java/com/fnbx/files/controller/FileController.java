package com.fnbx.files.controller;

import com.fnbx.files.service.FileService;
import com.fnbx.shared.enums.FileKind;
import com.fnbx.shared.security.BranchHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService files;

    @PostMapping(value = "/cash-close-attachments", consumes = "multipart/form-data")
    public ResponseEntity<FileService.FileResponse> upload(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("kind") FileKind kind) {
        return ResponseEntity.status(HttpStatus.CREATED).body(files.upload(branchId, file, kind));
    }

    @GetMapping("/{fileId}/view-url")
    public FileService.ViewUrlResponse viewUrl(
            @RequestHeader(BranchHeader.NAME) UUID branchId,
            @PathVariable UUID fileId) {
        return files.viewUrl(branchId, fileId);
    }
}
