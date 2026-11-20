package in.mahametro.competency.delivery;

import in.mahametro.competency.questions.Question;
import in.mahametro.competency.questions.QuestionOption;
import in.mahametro.competency.questions.QuestionTextRenderer;
import in.mahametro.competency.questions.QuestionView;
import java.util.List;

/** Prepared item for the test player. */
public class PlayerItemView {

    private final AttemptItem attemptItem;
    private final Question question;
    private final String html;
    private final String mediaUrl;
    private final String mediaKind;
    private final List<QuestionOption> options;

    public PlayerItemView(AttemptItem attemptItem, Question question, List<QuestionOption> options) {
        this.attemptItem = attemptItem;
        this.question = question;
        this.html = QuestionTextRenderer.toHtml(question.getStemEn());
        QuestionView qv = new QuestionView(question, options);
        this.mediaUrl = qv.getMediaUrl();
        this.mediaKind = qv.getMediaKind();
        this.options = options;
    }

    public boolean isAnswered() {
        return (attemptItem.getSelectedOptions() != null && !attemptItem.getSelectedOptions().isBlank())
                || (attemptItem.getAnsweredText() != null && !attemptItem.getAnsweredText().isBlank());
    }

    public AttemptItem getAttemptItem() { return attemptItem; }
    public Question getQuestion() { return question; }
    public String getHtml() { return html; }
    public String getStemMr() { return question.getStemMr(); }
    public String getQtype() { return question.getQtype(); }
    public String getMediaUrl() { return mediaUrl; }
    public String getMediaKind() { return mediaKind; }
    public List<QuestionOption> getOptions() { return options; }
}
