package com.opencellsoft.utils;

/**
 * {@link FileErrorInfo} class
 *
 * @author mohammed stitane
 */
public class FileErrorInfo extends ErrorInfo {

    private String filename;

    public FileErrorInfo(String fn, ErrorInfo error) {
        super(error.getErrorCode(), error.getMessage());
        filename = fn;
    }

    public FileErrorInfo(String fn, int code, String msg) {
        super(code, msg);
        filename = fn;
    }

    public FileErrorInfo(String fn, String msg) {
        super(msg);
        filename = fn;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(filename).append(super.toString());
        return sb.toString();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
