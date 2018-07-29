/*
 * This file is part of YoutubeMp3Server, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me/>
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
package com.techshroom.ytmp3.conversion.videoid;

import com.google.common.base.Splitter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CommonUtilities {

    private static final Splitter.MapSplitter QUERY_SPLITTER = Splitter.on('&').withKeyValueSeparator(Splitter.on('=').limit(2));
    private static final Splitter PATH_SPLITTER = Splitter.on('/').omitEmptyStrings();

    private static final Pattern PROTOCOL_REGEX = Pattern.compile("[a-zA-Z]+://");

    public static Optional<URI> asURI(String uri) {
        String finalUri = uri;
        if (!PROTOCOL_REGEX.matcher(uri).lookingAt()) {
            // No protocol -- use HTTPS
            finalUri = "https://" + finalUri;
        }
        try {
            return Optional.of(new URI(finalUri));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    public static Map<String, String> getQueryMap(URI uri) {
        return QUERY_SPLITTER.split(uri.getQuery());
    }

    public static List<String> getPathParts(URI uri) {
        return PATH_SPLITTER.splitToList(uri.getPath());
    }

    private CommonUtilities() {
    }

}
