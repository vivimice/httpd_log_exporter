/*
 * Copyright 2024 vivimice@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
