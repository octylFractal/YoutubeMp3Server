package conversion.videoid;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
