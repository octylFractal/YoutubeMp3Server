package conversion.videoid;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import play.Logger;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

public class VideoIdFinder {

    private static final Logger.ALogger LOGGER = Logger.of(VideoIdFinder.class);

    private static final List<VideoHandler> HANDLERS = ImmutableList.copyOf(ServiceLoader.load(VideoHandler.class));

    static {
        LOGGER.info("Found VideoHandlers " + Lists.transform(HANDLERS, Object::getClass));
    }

    public static Optional<VideoId> findId(String video) {
        return HANDLERS.stream()
                .map(h -> h.findId(video))
                .filter(Optional::isPresent)
                .findFirst()
                .flatMap(Function.identity());
    }


}
