# httpd_log_exporter

The httpd_log_exporter is a tool designed for reading Apache2 combined log files and exposing Prometheus metrics. The metrics comprise sent bytes, received bytes, request count and request processing time.

## Quick Start

### Download

Download httpd_log_exporter in [Releases](https://github.com/vivimice/httpd_log_exporter/releases/latest).

### Configuration

You can set up your Apache2 httpd server with the following configuration:

```apache
LogFormat "%t %u %>s %I %B %D \"%U\"" httpd_log_exporter
CustomLog ${APACHE_LOG_DIR}/httpd_log_exporter.log httpd_log_exporter
```

If you prefer to use your own log format, ensure the includes the following format strings: `%u`, `%>s`, `%I`, `%B`, `%D`, `\"%U\"`. See [Custom Log Formats](https://httpd.apache.org/docs/2.4/mod/mod_log_config.html#formats) in Apache2 httpd's documentation.

Then run httpd_log_exporter to monitor the log file with the identical log format string:

```sh
httpd_log_exporter --port 9280 \
    --file /path/to/apache2_log_dir/httpd_log_exporter.log \
    --format '%t %u %>s %I %B %D "%U"'
```

After waiting for a few minutes, you'll be able to see the exported metrics at port 9280:

```sh
curl localhost:9280/metrics
```

## Run as systemd service

Create the following configuration file at `/etc/systemd/system/httpd_log_exporter.service`:

```systemd
[Unit]
Description=httpd_log_exporter
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
DynamicUser=true
Group=adm
ExecStart=/usr/local/bin/httpd_log_exporter \
        --file /var/log/apache2/httpd_log_exporter.log \
        --port 9280 \
        --format '%%t %%u %%>s %%I %%B %%D "%%U"'
Restart=always

[Install]
WantedBy=multi-user.target
```

Note the following:
- When using systemd's service unit file, you have to escape the '%' sign.
- The service must be configured with user permissions sufficient to access the httpd log files.

You can then enable and start your service:

```bash
systemctl daemon-reload
systemctl enable httpd_log_exporter
systemctl start httpd_log_exporter
```

# Metrics

|Metric Name|Type|Labels|Description|
|-|-|-|-|
|httpd_log_count_total|counter|`user`, `path`, `status`|The total numbers of requests monitored since startup|
|httpd_log_time_seconds_total|counter|`user`, `path`, `status`|The total time taken, in seconds, to serve the request. See `%D` log format string.|
|httpd_log_sent_bytes_total|counter|`user`, `path`, `status`|The total size of response, in bytes, excluding HTTP headers. See `%B` log format string.|
|httpd_log_received_bytes_total|counter|`user`, `path`, `status`|The total size of request, in bytes, including request AND headers. See `%I` log format string.|
|httpd_log_uptime_seconds|counter||The total seconds of elapsed since startup.|
|httpd_log_idletime_seconds|gauge||The seconds elapsed since last log line processed.|
|httpd_log_process_error_count_total|counter||The total number of log parse errors.|

# Install from source

## Prerequisits

Firstly, you'll need [GraalVM](https://www.graalvm.org/). It should be equal to or higher than Java 21.

## Compile native image

```bash
git clone https://github.com/vivimice/httpd_log_exporter
cd httpd_log_exporter
JAVA_HOME=/path/to/graalvm ./gradlew nativeCompile
sudo cp build/native/nativeCompile/httpd_log_exporter /usr/local/bin
```

# Security Concerns

- To avoid potential security issues caused by vulnerabilities, it is highly recommended to run this program with the lowest possible privilege level user that has access to log files. By default, the log files of apache2 httpd are owned by root:adm with a permission of 0640, which requires the httpd_log_exporter to be executed at least as an adm group member, avoid using root identity. However, even so, httpd_log_exporter will still have access to most of the log files under /var/log. We strongly recommend adjusting the configuration of apache2 httpd to set the group ownership of log files to an independent group, achieving access permission isolation.

- Since httpd_log_exporter extracts metrics that contain usernames and URL paths, some information could potentially leak. Thus, you need to limit the port's accessibility to unnecessary clients. Ideally, only authorized clients should be access this port.

# Additional Information

- httpd_log_exporter will not issue alerts for file readability problems (like incorrect paths or insufficient permissions). Thus, you must ensure that the file path is accurate and readable by the user who runs httpd_log_exporter.
