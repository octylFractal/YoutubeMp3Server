package conversions.videoid;

import conversion.videoid.VideoId;
import conversion.videoid.YoutubeVideoHandler;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class YoutubeVideoHandlerTest {

    private final YoutubeVideoHandler handler = new YoutubeVideoHandler();

    private void assertVideoId(String uri, @Nullable String expectedId) {
        Optional<VideoId> result = handler.findId(uri);
        if (result.isPresent()) {
            VideoId id = result.get();
            assertEquals("youtube", id.getProvider());
            assertTrue("result should not have been generated", expectedId != null);
            assertEquals(expectedId, id.getId());
        } else {
            assertTrue("result should have been generated", expectedId == null);
        }
    }

    @Test
    public void normalYoutubeUrl() throws Exception {
        assertVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    public void normalNoProtocolYoutubeUrl() throws Exception {
        assertVideoId("www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    public void shareYoutubeUrl() throws Exception {
        assertVideoId("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    public void embedYoutubeUrl() throws Exception {
        assertVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ", "dQw4w9WgXcQ");
    }

    @Test
    public void rawId() throws Exception {
        assertVideoId("dQw4w9WgXcQ", null);
    }

}
