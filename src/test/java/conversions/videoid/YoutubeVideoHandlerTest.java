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

package conversions.videoid;

import net.octyl.ytmp3.conversion.videoid.VideoId;
import net.octyl.ytmp3.conversion.videoid.YoutubeVideoHandler;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class YoutubeVideoHandlerTest {

    private final YoutubeVideoHandler handler = new YoutubeVideoHandler();

    private void assertVideoId(String uri, @Nullable String expectedId) {
        Optional<VideoId> result = handler.findId(uri);
        if (result.isPresent()) {
            VideoId id = result.get();
            assertEquals("youtube", id.getProvider());
            assertNotNull(expectedId, "result should not have been generated");
            assertEquals(expectedId, id.getId());
        } else {
            assertNull(expectedId, "result should have been generated");
        }
    }

    @Test
    void normalYoutubeUrl() {
        assertVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    void normalNoProtocolYoutubeUrl() {
        assertVideoId("www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    void shareYoutubeUrl() {
        assertVideoId("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    void embedYoutubeUrl() {
        assertVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    void rawId() {
        assertVideoId("dQw4w9WgXcQ", null);
    }

}
