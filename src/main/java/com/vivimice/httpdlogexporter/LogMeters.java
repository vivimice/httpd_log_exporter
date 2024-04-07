package com.vivimice.httpdlogexporter;

import io.prometheus.metrics.core.metrics.Counter;

public class LogMeters {
    
    public static final Counter countsCounter = Counter.builder()
        .name("httpd_log_count_total")
        .labelNames("user", "path", "status")
        .register();

    public static final Counter timeSecondsCounter = Counter.builder()
        .name("httpd_log_time_seconds_total")
        .labelNames("user", "path", "status")
        .register();

    public static final Counter receivedBytesCounter = Counter.builder()
        .name("httpd_log_received_bytes_total")
        .labelNames("user", "path", "status")
        .register();

    public static final Counter sentBytesCounter = Counter.builder()
        .name("httpd_log_sent_bytes_total")
        .labelNames("user", "path", "status")
        .register();

}
