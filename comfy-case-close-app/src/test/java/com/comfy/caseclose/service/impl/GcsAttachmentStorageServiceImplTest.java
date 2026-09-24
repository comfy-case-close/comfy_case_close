package com.comfy.caseclose.service.impl;

import com.comfy.caseclose.config.GoogleCloudStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcsAttachmentStorageServiceImplTest {

    private static final String BUCKET = "comfy-uploads";
    private static final String URL_PREFIX = "https://storage.googleapis.com/" + BUCKET + "/";

    @TempDir Path tempDir;

    private GoogleCloudStorageProperties properties;
    private GcsAttachmentStorageServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new GoogleCloudStorageProperties();
        properties.setEnabled(true);
        properties.setBucketName(BUCKET);
        properties.setServiceAccountKeyPath(writeFakeServiceAccountKey().toString());
        service = new GcsAttachmentStorageServiceImpl(properties);
    }

    @Test
    void objectKeyFromUrl_returnsKeyForOwnUrl() {
        assertEquals("case-close/2026/09/TX/POS_1.jpg",
                service.objectKeyFromUrl(URL_PREFIX + "case-close/2026/09/TX/POS_1.jpg"));
    }

    @Test
    void objectKeyFromUrl_stripsQueryStringFromAnEchoedSignedUrl() {
        assertEquals("case-close/2026/09/TX/POS_1.jpg",
                service.objectKeyFromUrl(URL_PREFIX + "case-close/2026/09/TX/POS_1.jpg?X-Goog-Signature=abc"));
    }

    @Test
    void objectKeyFromUrl_returnsNullForForeignOrBlankUrl() {
        assertNull(service.objectKeyFromUrl("https://example.com/x.jpg"));
        assertNull(service.objectKeyFromUrl(""));
        assertNull(service.objectKeyFromUrl(null));
    }

    @Test
    void viewUrlFor_signsOwnUrlWithV4AndKeepsKeyUnderPrefix() {
        String signed = service.viewUrlFor(URL_PREFIX + "case-close/2026/09/TX/POS_1.jpg");

        assertNotNull(signed);
        assertTrue(signed.startsWith(URL_PREFIX + "case-close/2026/09/TX/POS_1.jpg?"), signed);
        assertTrue(signed.contains("X-Goog-Algorithm=GOOG4-RSA-SHA256"), signed);
        assertTrue(signed.contains("X-Goog-Signature="), signed);
        assertTrue(signed.contains("X-Goog-Expires=3600"), signed);
    }

    @Test
    void viewUrlFor_honoursConfiguredTtl() {
        properties.setSignedUrlTtlMinutes(5);

        String signed = service.viewUrlFor(URL_PREFIX + "case-close/a.jpg");

        assertNotNull(signed);
        assertTrue(signed.contains("X-Goog-Expires=300"), signed);
    }

    @Test
    void viewUrlFor_returnsNullForForeignUrl() {
        assertNull(service.viewUrlFor("https://example.com/x.jpg"));
        assertNull(service.viewUrlFor(null));
    }

    @Test
    void viewUrlFor_returnsNullInsteadOfThrowingWhenStorageDisabled() {
        properties.setEnabled(false);

        assertNull(service.viewUrlFor(URL_PREFIX + "case-close/a.jpg"));
    }

    @Test
    void viewUrlFor_returnsNullInsteadOfThrowingWhenKeyIsUnreadable() {
        properties.setServiceAccountKeyPath(tempDir.resolve("missing.json").toString());

        assertNull(service.viewUrlFor(URL_PREFIX + "case-close/a.jpg"));
    }

    @Test
    void viewUrlFor_findsRelativeKeyPathInAParentOfTheWorkingDir() throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();
        String name = "comfy-test-" + java.util.UUID.randomUUID() + ".json";
        Path keyInParent = repoRoot.resolve(name);
        Files.copy(Path.of(properties.getServiceAccountKeyPath()), keyInParent);
        try {
            properties.setServiceAccountKeyPath(name);

            assertNotNull(service.viewUrlFor(URL_PREFIX + "case-close/a.jpg"));
        } finally {
            Files.deleteIfExists(keyInParent);
        }
    }

    private Path writeFakeServiceAccountKey() throws IOException, java.security.NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = "{"
                + "\"type\":\"service_account\","
                + "\"project_id\":\"test-project\","
                + "\"private_key_id\":\"test-key-id\","
                + "\"private_key\":\"" + pem.replace("\n", "\\n") + "\","
                + "\"client_email\":\"case-close@test-project.iam.gserviceaccount.com\","
                + "\"client_id\":\"1234567890\""
                + "}";
        Path file = tempDir.resolve("sa-key.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }
}
