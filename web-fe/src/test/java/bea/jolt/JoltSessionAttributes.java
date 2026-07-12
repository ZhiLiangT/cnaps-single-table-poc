package bea.jolt;

public class JoltSessionAttributes {
    public static final Integer APPADDRESS = 1;
    public static final Integer RECVTIMEOUT = 2;
    private static Integer lastReceiveTimeout;

    public static void reset() {
        lastReceiveTimeout = null;
    }

    public static Integer lastReceiveTimeout() {
        return lastReceiveTimeout;
    }

    public void setString(Integer key, String value) {
    }

    public void setInt(Integer key, int value) {
        if (RECVTIMEOUT.equals(key)) {
            lastReceiveTimeout = value;
        }
    }
}
