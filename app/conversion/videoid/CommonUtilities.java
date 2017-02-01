package conversion.videoid;

import com.google.common.base.Splitter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CommonUtilities {

    private static final Splitter.MapSplitter QUERY_SPLITTER = Splitter.on('&').withKeyValueSeparator('=');
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
