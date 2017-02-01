package controllers;

import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import com.google.common.collect.ImmutableMap;
import conversion.Conversion;
import conversion.ConversionManager;
import conversion.EventListener;
import conversion.Status;
import play.api.http.HttpErrorHandler;
import play.api.mvc.AnyContent;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.routing.Router;
import play.routing.RoutingDsl;
import views.html.Main.index;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class Server extends Controller {

    @Nullable
    private static Assets assets;
    @Nullable
    private static Materializer materializer;

    private static Http.Context getContext() {
        return Http.Context.current();
    }

    private static Http.Request getRequest() {
        return getContext().request();
    }

    public static Router getRouter(HttpErrorHandler errorHandler, Materializer materializer) {
        // These must be saved in fields to prevent weird errors
        assets = new Assets(errorHandler);
        Server.materializer = materializer;
        return new RoutingDsl()
                .GET("/").routeTo(Server::index)
                .POST("/mp3ify").routeTo(Server::mp3ify)
                .GET("/mp3ify/:id/status").routeTo(Server::mp3ifyStatus)
                .GET("/mp3ify/:id/rawOutput").routeTo(Server::mp3ifyRawOutput)
                .GET("/mp3ify/:id/fileName").routeTo(Server::mp3ifyFileName)
                .GET("/mp3ify/:id/stream").routeTo(Server::mp3ifyStream)
                .GET("/mp3ify/:id/download").routeTo(Server::mp3ifyDownload)
                .GET("/assets/*file").routeAsync((String file) -> {
                    play.api.mvc.Action<AnyContent> action = assets.at("/public", file, false);
                    Http.Request request = getRequest();
                    return action.asJava().apply(request).run(Source.single(request.body().asBytes()), Server.materializer);
                })
                .build();
    }

    private static Result index() {
        return ok(index.render());
    }

    private static Result mp3ify() {
        String[] formVideo = getRequest().body().asFormUrlEncoded().get("video");
        if (formVideo == null) {
            return badRequest("video.not.provided");
        }
        String video = formVideo[0];
        return created(ConversionManager.newConversion(video).getId());
    }

    private static Result mp3ifyStatus(String id) {
        return useConversionAndProperty(id, Conversion::getStatus, (conversion, status) -> {
            ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
            data.put("status", status);
            if (status == Status.FAILED) {
                data.put("reason", checkNotNull(conversion.getFailureReason()));
            }
            return ok(JsonSerializer.serialize(data.build()));
        });
    }

    private static Result mp3ifyRawOutput(String id) {
        return useConversionProperty(id, Conversion::getRawOutput, Results::ok);
    }

    private static Result mp3ifyFileName(String id) {
        return useConversionProperty(id, Conversion::getFileName, Results::ok);
    }

    private static Result mp3ifyStream(String id) {
        return useConversionProperty(id, Conversion::getObservableEvents, events -> {
            String lastEventId = getRequest().getHeader("Last-Event-ID");

            EventListener listener = EventListener.attach(events);

            int skip = 0;
            if (lastEventId != null) {
                try {
                    // ID is number of events passed out
                    skip = Integer.parseInt(lastEventId) + 1;
                } catch (NumberFormatException ignored) {
                    // malformed ID is fine, we'll just ignore it
                }
            }

            try {
                return ok(listener.createInputStream(skip)).as(Http.MimeTypes.EVENT_STREAM);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static Result mp3ifyDownload(String id) {
        return useConversionAndProperty(id, Conversion::getResultFile, (conversion, resultFile) ->
                ok().sendPath(resultFile, false, checkNotNull(conversion.getFileName()))
        );
    }

    private static <T> Result useConversionAndProperty(String id, Function<Conversion, T> propertyGetter,
                                                       BiFunction<Conversion, T, Result> nonNullBlock) {
        return useConversion(id, conversion -> {
            T property = propertyGetter.apply(conversion);
            return nullToNotFound(id, property, t -> nonNullBlock.apply(conversion, t));
        });
    }

    private static <T> Result useConversionProperty(String id, Function<Conversion, T> propertyGetter,
                                                    Function<T, Result> nonNullBlock) {
        return useConversion(id, conversion -> {
            T property = propertyGetter.apply(conversion);
            return nullToNotFound(id, property, nonNullBlock);
        });
    }

    private static Result useConversion(String id, Function<Conversion, Result> nonNullBlock) {
        return nullToNotFound(id, ConversionManager.getConversion(id), nonNullBlock);
    }

    private static <T> Result nullToNotFound(String notFoundMessage, @Nullable T object, Function<T, Result> nonNullBlock) {
        if (object == null) {
            return notFound(notFoundMessage);
        }
        return nonNullBlock.apply(object);
    }

}
