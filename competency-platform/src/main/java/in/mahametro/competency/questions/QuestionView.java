package in.mahametro.competency.questions;

import java.util.List;
import java.util.Locale;

/** A question prepared for display: text and solution already turned into safe HTML, with its answers and media. */
public class QuestionView {

    private final Question question;
    private final String html;
    private final String solutionHtml;
    private final List<QuestionOption> options;

    public QuestionView(Question question, List<QuestionOption> options) {
        this.question = question;
        this.html = QuestionTextRenderer.toHtml(question.getStemEn());
        this.solutionHtml = QuestionTextRenderer.toHtml(question.getSolutionText());
        this.options = options;
    }

    public Question getQuestion() {
        return question;
    }

    public String getHtml() {
        return html;
    }

    public String getSolutionHtml() {
        return solutionHtml;
    }

    public List<QuestionOption> getOptions() {
        return options;
    }

    public String getMediaUrl() {
        return resolveMediaUrl(question.getMediaRef());
    }

    public String getMediaKind() {
        return resolveMediaKind(question.getMediaRef(), question.getQuestionType());
    }

    public String getSolutionMediaUrl() {
        return resolveMediaUrl(question.getSolutionMedia());
    }

    public String getSolutionMediaKind() {
        return resolveMediaKind(question.getSolutionMedia(), "");
    }

    private String resolveMediaUrl(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String trimmed = ref.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.matches("^\\d+$")) {
            return "/media/" + trimmed;
        }
        return trimmed;
    }

    private String resolveMediaKind(String ref, String fallbackType) {
        if (ref != null && !ref.isBlank()) {
            String lower = ref.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")) {
                return "image";
            }
            if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")
                    || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                return "audio";
            }
            if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".ogv")) {
                return "video";
            }
        }
        if (fallbackType != null) {
            String lowerType = fallbackType.toLowerCase(Locale.ROOT);
            if (lowerType.contains("image")) {
                return "image";
            }
            if (lowerType.contains("audio")) {
                return "audio";
            }
            if (lowerType.contains("video")) {
                return "video";
            }
        }
        return "file";
    }
}
