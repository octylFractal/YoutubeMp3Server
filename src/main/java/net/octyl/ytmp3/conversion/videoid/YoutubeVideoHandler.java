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

package net.octyl.ytmp3.conversion.videoid;

import com.google.auto.service.AutoService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoService(VideoHandler.class)
public class YoutubeVideoHandler implements VideoHandler {

    @Override
    public Optional<VideoId> findId(String video) {
        return CommonUtilities.asURI(video).map(uri -> {
            String host = uri.getHost();
            if (host.equalsIgnoreCase("www.youtube.com")) {
                List<String> pathParts = CommonUtilities.getPathParts(uri);
                // It should be at the watch path or embed path
                if (pathParts.get(0).equalsIgnoreCase("watch")) {
                    Map<String, String> query = CommonUtilities.getQueryMap(uri);
                    return query.get("v");
                }
                if (pathParts.size() >= 2 && pathParts.get(0).equalsIgnoreCase("embed")) {
                    return pathParts.get(1);
                }
            } else if (host.equalsIgnoreCase("youtu.be")) {
                return CommonUtilities.getPathParts(uri).get(0);
            }
            return null;
        }).map(id -> new VideoId("youtube", id));
    }

}
