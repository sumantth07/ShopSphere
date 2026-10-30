package com.shopsphere.storage;

import java.io.IOException;

/** Where uploaded product images live. Swap the implementation (S3, Cloudinary…) without touching controllers. */
public interface ImageStorage {
    /** Stores validated image bytes and returns a URL/path the frontend can use (e.g. "/uploads/abc.jpg"). */
    String store(byte[] data, String extension) throws IOException;
}
