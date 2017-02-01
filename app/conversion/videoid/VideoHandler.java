package conversion.videoid;

import java.util.Optional;

public interface VideoHandler {

    Optional<VideoId> findId(String video);

}
