package com.techshroom.ytmp3.conversion;

import com.techshroom.lettar.addons.sse.ServerSentEvent;

public class JsonSSEvent {
    
    public static JsonSSEvent from(ServerSentEvent lettarEvent) {
        JsonSSEvent event = new JsonSSEvent();
        lettarEvent.getName().ifPresent(event::setName);
        lettarEvent.getId().ifPresent(event::setId);
        lettarEvent.getData().ifPresent(event::setData);
        return event;
    }

    private String name;
    private String id;
    private String data;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
    
    public ServerSentEvent toLettarEvent() {
        return ServerSentEvent.of(name, id, data);
    }

}
