package com.sosvietnam.util;

import com.sosvietnam.model.payload.exception.SosException;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.regex.Pattern;

/** Naming rules shared by every media storage backend. */
public final class MediaKeys {

    // Only names we generated can be read back: blocks ../ traversal and guessing.
    private static final Pattern SAFE_NAME = Pattern.compile("[a-f0-9-]{36}\\.[a-z0-9]{2,5}");
    private static final Pattern SAFE_FOLDER = Pattern.compile("[A-Za-z0-9/_-]{1,120}");

    private MediaKeys() {
    }

    public static String newFileName(String extension) {
        return UUID.randomUUID() + "." + extension;
    }

    /** "incidents/SOS-20261009-6E0588" + "uuid.jpg" -> object key / relative path. */
    public static String key(String folder, String fileName) {
        if (!SAFE_FOLDER.matcher(folder).matches() || folder.contains("..")) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Thư mục không hợp lệ");
        }
        if (!SAFE_NAME.matcher(fileName).matches()) {
            throw new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp");
        }
        return folder + "/" + fileName;
    }
}
