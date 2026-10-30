package com.shopsphere.sellers;

import com.shopsphere.exception.ApiException;
import com.shopsphere.security.CurrentUser;
import com.shopsphere.storage.ImageSniffer;
import com.shopsphere.storage.ImageStorage;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {
    private final SellerService sellers;
    private final ImageStorage storage;
    private final long maxBytes;

    public UploadController(SellerService sellers, ImageStorage storage, @Value("${shopsphere.upload.max-bytes}") long maxBytes) {
        this.sellers = sellers; this.storage = storage; this.maxBytes = maxBytes;
    }

    /** Approved sellers only. Accepts one JPG/PNG/WebP up to 2 MB, verified by content, and returns its URL. */
    @PostMapping(path = "/api/sellers/me/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) throws IOException {
        sellers.requireApproved(CurrentUser.id(jwt));
        if (file.isEmpty()) throw ApiException.badRequest("FILE_EMPTY", "Choose an image file.");
        if (file.getSize() > maxBytes) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Images must be 2 MB or smaller.");
        String ext = ImageSniffer.detect(file.getBytes());
        if (ext == null) throw ApiException.badRequest("FILE_TYPE", "Only JPG, PNG or WebP images are allowed.");
        return Map.of("url", storage.store(file.getBytes(), ext));
    }
}
