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
package com.vivimice.httpdlogexporter.test;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.vivimice.httpdlogexporter.CombinedLogParser;

public class CombinedLogParserTest {
    
    @Test
    public void test() {
        runTest(
            "%t %u %>s %I %B %D \\\"%U\\\"",
            "[06/Apr/2024:17:00:45 +0800] alice 200 441 856 5010160 \"/foo/bar/baz\"",
            new HashMap<String, String>() {{
                put("t", "[06/Apr/2024:17:00:45 +0800]");
                put("u", "alice");
                put(">s", "200");
                put("I", "441");
                put("B", "856");
                put("D", "5010160");
                put("U", "/foo/bar/baz");
            }}
        );

        runTest(
            "%h %l %u %t",
            "192.168.201.2 - alice [06/Apr/2024:11:28:58 +0800]",
            new HashMap<String, String>() {{
                put("h", "192.168.201.2");
                put("l", "-");
                put("u", "alice");
                put("t", "[06/Apr/2024:11:28:58 +0800]");
            }}
        );

        runTest(
            "%h %l",
            "192.168.201.2 -",
            new HashMap<String, String>() {{
                put("h", "192.168.201.2");
                put("l", "-");
            }}
        );

        runTest(
            "%h %l %u %t \"%r\" %>s %b \"%{Referer}i\" \"%{User-agent}i\"",
            "192.168.201.2 - alice [06/Apr/2024:11:28:58 +0800] \"GET /foo/bar/baz HTTP/2.0\" 200 3649 \"https://example.com/\" \"curl\\\"\"",
            new HashMap<String, String>() {{
                put("h", "192.168.201.2");
                put("l", "-");
                put("u", "alice");
                put("t", "[06/Apr/2024:11:28:58 +0800]");
                put("r", "GET /foo/bar/baz HTTP/2.0");
                put(">s", "200");
                put("b", "3649");
                put("{Referer}i", "https://example.com/");
                put("{User-agent}i", "curl\"");
            }}
        );

        runTest(
            "%h %l %u %t \"%r\" %>s %b \"%{Referer}i\" \"%{User-agent}i\"",
            "192.168.201.2 - alice [06/Apr/2024:11:28:58 +0800] \"GET /foo/bar/baz HTTP/2.0\" 200 3649 \"https://example.com/\" \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36\"",
            new HashMap<String, String>() {{
                put("h", "192.168.201.2");
                put("l", "-");
                put("u", "alice");
                put("t", "[06/Apr/2024:11:28:58 +0800]");
                put("r", "GET /foo/bar/baz HTTP/2.0");
                put(">s", "200");
                put("b", "3649");
                put("{Referer}i", "https://example.com/");
                put("{User-agent}i", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
            }}
        );
    }

    public void runTest(String format, String line, Map<String, String> expected) {
        CombinedLogParser parser = CombinedLogParser.of(format);
        var actual = parser.parse(line);
        assertEquals(expected, actual);
    }

}
