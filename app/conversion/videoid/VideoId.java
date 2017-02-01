package conversion.videoid;

public final class VideoId {

    private final String provider;
    private final String id;

    public VideoId(String provider, String id) {
        this.provider = provider;
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Video " + id + " from " + provider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoId)) return false;

        VideoId videoId = (VideoId) o;

        return provider.equals(videoId.provider) && id.equals(videoId.id);
    }

    @Override
    public int hashCode() {
        int result = provider.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }
}
