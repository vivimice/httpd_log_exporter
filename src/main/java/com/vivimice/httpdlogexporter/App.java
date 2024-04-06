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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import jakarta.annotation.Nonnull;

public class App {

    private static final Counter processErrorCounter = Counter.builder()
        .name("httpd_log_process_error_count_total")
        .register();
    @Nonnull
    private final AppContext context;
    private volatile long lastLogProcessedAt = 0;
    private HTTPServer server;
    private Thread tailerThread;

    private App(@Nonnull AppContext context) {
        this.context = context;
    }

    private void run() {
        installCommonMeters();
        startMetricServer();
        runTailer();
    }

    private void join() throws InterruptedException {
        tailerThread.join();
        server.stop();
    }

    private void installCommonMeters() {
        long startUpAt = System.currentTimeMillis();
        GaugeWithCallback.builder()
            .name("httpd_log_uptime_seconds")
            .callback(callback -> {
                callback.call((System.currentTimeMillis() - startUpAt) / 1000d);
            })
            .register();

        GaugeWithCallback.builder()
            .name("httpd_log_idletime_seconds")
            .callback(callback -> {
                if (lastLogProcessedAt == 0) {
                    callback.call(0);
                } else {
                    callback.call((System.currentTimeMillis() - lastLogProcessedAt) / 1000d);
                }
            })
            .register();
    }

    private void startMetricServer() {
        try {
            server = HTTPServer.builder()
                .port(context.getPort())
                .buildAndStart();
        } catch (IOException ex) {
            throw die("Start metric server failed: %s", ex.getMessage());
        }
    }

    private CombinedLogParser createParser() {
        var parser = CombinedLogParser.of(context.getLogFormat());

        // check required fields
        Set<String> requiredFields = new HashSet<>(Arrays.asList(
            "B", // Size of response in bytes, excluding HTTP headers.
            "u", // Remote user if the request was authenticated
            "D", // The time taken to serve the request, in microseconds.
            ">s", // Status
            "I", // Bytes received, including request and headers. Cannot be zero. You need to enable mod_logio to use this.
            "U" // The URL path requested, not including any query string.
            ));

        requiredFields.removeAll(parser.getFieldNames());
        if (!requiredFields.isEmpty()) {
            throw die("Missing required format fields: %s", requiredFields.stream().collect(Collectors.joining(", ")));
        }

        return parser;
    }

    private void runTailer() {
        var parser = createParser();

        var tailer = Tailer.builder()
            .setFile(context.getFilePath())
            .setReOpen(true)
            .setTailFromEnd(true)
            .setTailerListener(new TailerListenerAdapter() {
                @Override
                public void handle(String line) {
                    try {
                        processLogLine(parser, line);
                    } catch (RuntimeException ex) {
                        processErrorCounter.inc();
                        System.err.println("Unhandled error while processing log line: " + line);
                        ex.printStackTrace();
                    }
                }
            })
            .get();

        tailer.run();
    }

    private void processLogLine(CombinedLogParser parser, String line) {
        var fieldMap = parser.parse(line);
        if (fieldMap == null) {
            return;
        }
            
        String user = fieldMap.get("u");
        String path = fieldMap.get("U");
        String status = fieldMap.get(">s");

        long receivedBytes;
        long sentBytes;
        long microSeconds;
        try {
            receivedBytes = Long.parseLong(fieldMap.get("I"));
            sentBytes = Long.parseLong(fieldMap.get("B"));
            microSeconds = Long.parseLong(fieldMap.get("D"));
        } catch (NumberFormatException ex) {
            System.err.printf("Unable parse received/sent bytes or process time from log (%s): %S", ex.getMessage(), line).println();
            return;
        }

        getMetric(LogMeters.countsCounter::labelValues, user, path, status)
            .inc();

        getMetric(LogMeters.timeSecondsCounter::labelValues, user, path, status)
            .inc(microSeconds / 1000000d);

        getMetric(LogMeters.receivedBytesCounter::labelValues, user, path, status)
            .inc(receivedBytes);

        getMetric(LogMeters.sentBytesCounter::labelValues, user, path, status)
            .inc(sentBytes);

        lastLogProcessedAt = System.currentTimeMillis();
    }

    private <T> T getMetric(Function<String[], T> func, String user, String path, String status) {
        return func.apply(new String[] { user, path, status });
    }

    public static void main(String[] args) throws InterruptedException {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            System.err.print("Unhandled exception occured. Exit.");
            System.exit(2);
        });

        var context = new AppContext();
        parseArgs(context, args);

        var app = new App(context);
        app.run();
        app.join();
        
        System.out.println("Exit.");
    }

    private static void parseArgs(AppContext context, String[] args) {
        for (int i = 0; i < args.length; i++) {
            var arg = args[i];
            var nextArg = (i + 1 < args.length) ? args[i + 1] : null;
            Runnable assertHasNext = () -> {
                if (nextArg == null) {
                    die("Missing parameter of argument: %s", arg);
                }
            };
            
            if ("--port".equals(arg)) {
                assertHasNext.run();
                try {
                    context.setPort(Integer.parseInt(nextArg));
                } catch (RuntimeException ex) {
                    throw die("Bad port: %s", nextArg);
                }
                i++;
            }

            if ("--format".equals(arg)) {
                assertHasNext.run();
                context.setLogFormat(nextArg);
                i++;
            }

            if ("--file".equals(arg)) {
                assertHasNext.run();
                context.setFilePath(nextArg);
                i++;
            }
        }

        if (context.getLogFormat() == null) {
            die("format not specified.");
        }

        if (context.getPort() == 0) {
            die("port not specified.");
        }

        if (context.getFilePath() == null) {
            die("file not specified.");
        }
    }

    public static RuntimeException die(String format, Object ...args) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(String.format(format, args));
        } catch (Throwable t) {
            sb.append("Bad format: ").append(format);
            for (Object arg : args) {
                sb.append(' ').append(String.valueOf(arg));
            }
        }
        System.err.println(sb);
        System.exit(1);
        return new RuntimeException();
    }

}
