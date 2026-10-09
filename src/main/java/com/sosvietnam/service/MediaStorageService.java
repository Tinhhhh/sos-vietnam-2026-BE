package com.sosvietnam.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Where incident photos/videos live. Pick the implementation with
 * app.storage.provider: "local" (uploads/ folder) or "r2" (Cloudflare R2).
 */
public interface MediaStorageService {
    /** Saves the file under the folder and returns the generated file name. */
    String store(String folder, MultipartFile file, String extension);

    MediaAccess load(String folder, String fileName);
}
