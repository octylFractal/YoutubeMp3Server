/*
 * This file is part of YoutubeMp3Server, licensed under the MIT License (MIT).
 *
 * Copyright (c) Octavia Togami <https://octyl.net/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.octyl.ytmp3.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class HttpFileName {

    public static String encodeDisposition(String filename) {
        var content = new StringBuilder("attachment; ");
        var encoder = StandardCharsets.US_ASCII.newEncoder();
        if (encoder.canEncode(filename)) {
            // Use shorter form for ASCII-compatible
            content.append("filename=\"").append(filename).append("\"");
        } else {
            // Encode as UTF-8, in two parts for maximum compatibility
            var urlEncodedUtf8 = encodeUrlUtf8(filename);
            // Legacy filename= parameter, some browsers do not support net filename*
            content.append("filename=\"").append(urlEncodedUtf8).append("\"; ");
            // New filename*= parameter
            content.append("filename*=utf8''").append(urlEncodedUtf8);
        }
        return content.toString();
    }

    private static CharSequence encodeUrlUtf8(String filename) {
        var baseEncode = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        // Make sure spaces are emitted right
        return baseEncode.replace("+", "%20");
    }

    private HttpFileName() {
    }
}
