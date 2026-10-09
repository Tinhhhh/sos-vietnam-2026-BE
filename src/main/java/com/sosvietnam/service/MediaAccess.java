package com.sosvietnam.service;

import org.springframework.core.io.Resource;

import java.net.URI;

/**
 * How a stored file reaches the browser: either streamed by this server
 * (local disk) or through a short-lived signed link to the bucket (R2).
 * Exactly one of the two fields is set.
 */
public record MediaAccess(Resource file, URI redirectUrl) {

    public static MediaAccess stream(Resource file) {
        return new MediaAccess(file, null);
    }

    public static MediaAccess redirect(URI url) {
        return new MediaAccess(null, url);
    }

    public boolean isRedirect() {
        return redirectUrl != null;
    }
}
