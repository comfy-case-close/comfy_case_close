package com.fnbx.files.storage;

/** Byte storage remains behind files-service; callers only receive file IDs. */
public interface FileObjectStorage {
    void put(String key, byte[] bytes, String contentType);
    String signedReadUrl(String key);
    void delete(String key);
}
