package conversion;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

import java.io.IOException;

public final class JsonSerializer<T> implements Serializer<T> {

    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        // allow reflection to fields
        JSON.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        JSON.registerModule(new Jdk8Module());
    }

    public static <T> JsonSerializer<T> instance(Class<T> clazz) {
        return new JsonSerializer<>(clazz);
    }

    private final Class<T> clazz;

    private JsonSerializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public void serialize(@NotNull DataOutput2 out, @NotNull T value) throws IOException {
        String json = JSON.writeValueAsString(value);
        out.writeUTF(json);
    }

    @Override
    public T deserialize(@NotNull DataInput2 input, int available) throws IOException {
        String json = input.readUTF();
        return JSON.readValue(json, clazz);
    }
}
