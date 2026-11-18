package in.mahametro.competency.questions;

import in.mahametro.competency.access.CurrentUser;
import in.mahametro.competency.employees.Employee;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves media files to signed-in users and allows authorized staff to upload media.
 */
@Controller
public class MediaController {

    private final MediaService mediaService;
    private final CurrentUser currentUser;

    public MediaController(MediaService mediaService, CurrentUser currentUser) {
        this.mediaService = mediaService;
        this.currentUser = currentUser;
    }

    /**
     * Serves media file by ID. Signed-in users only.
     */
    @GetMapping("/media/{id}")
    public ResponseEntity<Resource> getMedia(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required to access media.");
        }
        Media media = mediaService.findById(id);
        if (media == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found.");
        }
        Resource resource = mediaService.loadAsResource(media);
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on storage.");
        }

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(media.getMime());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getFilename() + "\"")
                .body(resource);
    }

    /**
     * Uploads a media file. Requires question.write or question.manage permission.
     */
    @PostMapping("/questions/media/upload")
    @ResponseBody
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file, Principal principal) {
        Employee actor = currentUser.requireAny(principal, "question.write", "question.manage");
        try {
            Media media = mediaService.store(actor, file);
            return ResponseEntity.ok(Map.of(
                    "id", media.getId(),
                    "url", "/media/" + media.getId(),
                    "filename", media.getFilename(),
                    "kind", media.getKind(),
                    "fileSize", media.getFileSize()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save file: " + e.getMessage()));
        }
    }
}
