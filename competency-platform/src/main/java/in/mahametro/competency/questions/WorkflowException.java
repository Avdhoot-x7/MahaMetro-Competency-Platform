package in.mahametro.competency.questions;

/** Thrown when a question cannot move to the requested status, or the person is not allowed to move it. */
public class WorkflowException extends RuntimeException {

    public WorkflowException(String message) {
        super(message);
    }
}
