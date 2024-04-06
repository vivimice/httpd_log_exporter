package com.vivimice.httpdlogexporter;

public class AppContext {
    
    private int port;
    private String logFormat;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLogFormat() {
        return logFormat;
    }

    public void setLogFormat(String logFormat) {
        this.logFormat = logFormat;
    }

}
