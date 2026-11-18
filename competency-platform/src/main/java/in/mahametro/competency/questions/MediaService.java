package in.mahametro.competency.questions;

import in.mahametro.competency.core.AuditService;
import in.mahametro.competency.employees.Employee;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates, stores, and serves question and solution media files outside the WAR.
 */
@Service
public class MediaService {

    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private static final Map<String, Set<String>> ALLOWED_MIMES = Map.of(
            Media.KIND_IMAGE, Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml"),
            Media.KIND_AUDIO, Set.of("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/ogg", "audio/webm",
                    "audio/mp4", "audio/aac"),
            Media.KIND_VIDEO, Set.of("video/mp4", "video/webm", "video/ogg")
    );

    private static final Map<String, String> EXTENSION_BY_MIME = Map.ofEntries(
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/gif", ".gif"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/mp3", ".mp3"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("audio/x-wav", ".wav"),
            Map.entry("audio/ogg", ".ogg"),
            Map.entry("audio/webm", ".webm"),
            Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/aac", ".aac"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/webm", ".webm"),
            Map.entry("video/ogg", ".ogv")
    );

    private final MediaRepository mediaRepository;
    private final AuditService audit;
    private final Path mediaDir;

    public MediaService(MediaRepository mediaRepository, AuditService audit,
            @Value("${app.media-dir:./data/media}") String mediaDirPath) {
        this.mediaRepository = mediaRepository;
        this.audit = audit;
        this.mediaDir = Paths.get(mediaDirPath).toAbsolutePath().normalize();
    }

    @Transactional
    public Media store(Employee owner, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 50MB limit.");
        }

        String rawMime = file.getContentType();
        String mime = (rawMime == null ? "" : rawMime.split(";")[0].trim().toLowerCase(Locale.ROOT));
        String kind = detectKind(mime);
        if (kind == null) {
            throw new IllegalArgumentException("File type " + mime + " is not supported. Only images, audio, and video are allowed.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "media" + EXTENSION_BY_MIME.getOrDefault(mime, "");
        } else {
            originalFilename = Paths.get(originalFilename).getFileName().toString(); // remove any path elements
        }

        String ext = EXTENSION_BY_MIME.getOrDefault(mime, getExtension(originalFilename));
        String storedName = kind + "-" + UUID.randomUUID() + ext;

        Files.createDirectories(mediaDir);
        Path targetPath = mediaDir.resolve(storedName).normalize();
        if (!targetPath.startsWith(mediaDir)) {
            throw new SecurityException("Invalid target path.");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        Media media = new Media(kind, originalFilename, storedName, mime, file.getSize(), owner.getEmpId());
        media = mediaRepository.save(media);
        audit.record(owner.getEmpId(), "media_uploaded", "media", media.getId().toString(), originalFilename);
        return media;
    }

    public Resource loadAsResource(Media media) {
        Path filePath = mediaDir.resolve(media.getStoredName()).normalize();
        if (!filePath.startsWith(mediaDir) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
            return null;
        }
        return new FileSystemResource(filePath);
    }

    public Media findById(Long id) {
        return mediaRepository.findById(id).orElse(null);
    }

    public Media findByStoredName(String storedName) {
        return mediaRepository.findByStoredName(storedName).orElse(null);
    }

    private String detectKind(String mime) {
        for (Map.Entry<String, Set<String>> entry : ALLOWED_MIMES.entrySet()) {
            if (entry.getValue().contains(mime)) {
                return entry.getKey();
            }
        }
        if (mime.startsWith("image/")) {
            return Media.KIND_IMAGE;
        }
        if (mime.startsWith("audio/")) {
            return Media.KIND_AUDIO;
        }
        if (mime.startsWith("video/")) {
            return Media.KIND_VIDEO;
        }
        return null;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }
}
