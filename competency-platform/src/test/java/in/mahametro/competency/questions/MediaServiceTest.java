package in.mahametro.competency.questions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.mahametro.competency.employees.Employee;
import in.mahametro.competency.employees.EmployeeRepository;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MediaServiceTest {

    @Autowired
    MediaService mediaService;

    @Autowired
    MediaRepository mediaRepository;

    @Autowired
    EmployeeRepository employees;

    @Test
    void storesAndLoadsImageFile() throws IOException {
        Employee author = employees.save(new Employee("TEST-MED-EMP", "Media Author"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-signal.png",
                "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}
        );

        Media saved = mediaService.store(author, file);
        assertNotNull(saved.getId());
        assertEquals("image", saved.getKind());
        assertEquals("sample-signal.png", saved.getFilename());
        assertEquals("image/png", saved.getMime());
        assertEquals(8L, saved.getFileSize());
        assertEquals(author.getEmpId(), saved.getOwnerEmpId());

        Resource resource = mediaService.loadAsResource(saved);
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void rejectsUnsupportedMimeType() {
        Employee author = employees.save(new Employee("TEST-MED-EMP2", "Media Author 2"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[] {'%', 'P', 'D', 'F'}
        );

        assertThrows(IllegalArgumentException.class, () -> mediaService.store(author, file));
    }

    @Test
    void questionViewResolvesMediaUrls() {
        Question q1 = new Question("C01-Q1", "C01", "Check signal", "EMP1");
        q1.setMediaRef("42");
        q1.setQuestionType("Image");
        QuestionView v1 = new QuestionView(q1, List.of());
        assertEquals("/media/42", v1.getMediaUrl());
        assertEquals("image", v1.getMediaKind());

        Question q2 = new Question("C01-Q2", "C01", "Listen to horn", "EMP1");
        q2.setMediaRef("horn_signal.mp3");
        q2.setSolutionMedia("evacuation_procedure.mp4");
        QuestionView v2 = new QuestionView(q2, List.of());
        assertEquals("horn_signal.mp3", v2.getMediaUrl());
        assertEquals("audio", v2.getMediaKind());
        assertEquals("evacuation_procedure.mp4", v2.getSolutionMediaUrl());
        assertEquals("video", v2.getSolutionMediaKind());
    }
}
