package conversion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Splitter;
import play.libs.EventSource;

import java.util.List;

public class Event {

    private static final Splitter LINE_SPLITTER = Splitter.on('\n');

    public static Event fromPlayEvent(EventSource.Event e) {
        String formatted = e.formatted();
        List<String> lines = LINE_SPLITTER.splitToList(formatted);
        String type = "";
        StringBuilder data = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("event")) {
                type = line.substring("event: ".length());
            } else if (line.startsWith("data")) {
                data.append(line.substring("data: ".length())).append('\n');
            }
        }
        data.setLength(data.length() - 1);
        return new Event(type, data.toString());
    }

    private final String type;
    private final String message;

    @JsonCreator
    public Event(@JsonProperty("type") String type, @JsonProperty("message") String message) {
        this.type = type;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public EventSource.Event toSseEvent(String id) {
        return EventSource.Event.event(message).withName(type).withId(id);
    }

    @Override
    public String toString() {
        return type + "Event(" + message + ")";
    }

}
