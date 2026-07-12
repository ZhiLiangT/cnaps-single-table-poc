package bea.jolt;

public class ApplicationException extends Exception {
    private final Object object;

    public ApplicationException(Object object) {
        super("application error");
        this.object = object;
    }

    public Object getObject() {
        return object;
    }
}
