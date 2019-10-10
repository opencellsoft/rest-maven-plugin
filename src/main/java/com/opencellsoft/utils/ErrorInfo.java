package com.opencellsoft.utils;

/**
 * javadoc for class
 */
public class ErrorInfo {

    private int errorCode;
    private String message;

    public ErrorInfo(int code, String msg) {
        errorCode = code;
        message = msg;
    }

    public ErrorInfo(String msg) {
        errorCode = -1;
        message = msg;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" [").append(errorCode).append(":").append(message).append("]");
        return sb.toString();
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
