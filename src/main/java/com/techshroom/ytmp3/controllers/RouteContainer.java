/*
 * This file is part of YoutubeMp3Server, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.techshroom.ytmp3.controllers;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.techshroom.lettar.Request;
import com.techshroom.lettar.Response;
import com.techshroom.lettar.SimpleResponse;
import com.techshroom.lettar.addons.sse.ServerSentEvent;
import com.techshroom.lettar.annotation.NotFoundHandler;
import com.techshroom.lettar.annotation.ServerErrorHandler;
import com.techshroom.lettar.pipe.builtins.accept.Produces;
import com.techshroom.lettar.pipe.builtins.method.Method;
import com.techshroom.lettar.pipe.builtins.path.Path;
import com.techshroom.lettar.routing.HttpMethod;
import com.techshroom.templar.jackson.JsonBodyCodec;
import com.techshroom.ytmp3.TemplateRenderer;
import com.techshroom.ytmp3.VelocityTemplateRenderer;
import com.techshroom.ytmp3.conversion.Conversion;
import com.techshroom.ytmp3.conversion.ConversionManager;
import com.techshroom.ytmp3.conversion.Status;
import io.netty.handler.codec.http.HttpResponseStatus;
import javafx.collections.ObservableList;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

public class RouteContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteContainer.class);

    private final TemplateRenderer index = VelocityTemplateRenderer.load("com/techshroom/ytmp3/templates/index.html.vm");
    private final AssetCacher assets = new AssetCacher();

    @Path("/")
    @Produces("text/html")
    public Response<String> index() {
        return SimpleResponse.of(200, index.render(ImmutableMap.of()));
    }

    @Path("/mp3ify")
    @JsonBodyCodec
    public Response<Object> mp3ifyList(Request<?> request) {
        long from;
        try {
            from = Long.parseLong(request.getQueryParts().getSingleValueOrDefault("from", ""));
            if (from < 0) {
                throw new IllegalArgumentException("from.too.small");
            }
        } catch (NumberFormatException e) {
            from = Long.MIN_VALUE;
        }
        long to;
        try {
            to = Long.parseLong(request.getQueryParts().getSingleValueOrDefault("to", ""));
            if (to < from) {
                throw new IllegalArgumentException("to.too.small");
            }
        } catch (NumberFormatException e) {
            to = Long.MAX_VALUE;
        }

        long f = from;
        long t = to;
        return SimpleResponse.of(200, ConversionManager.conversions()
            .filter(c -> c.getStatus() == Status.SUCCESSFUL)
            .filter(c -> {
                long millis = c.getEndTime().toMillis();
                return millis >= f && millis < t;
            })
            .sorted(Comparator.comparing(Conversion::getEndTime).reversed())
            .map(c -> ImmutableMap.of(
                "id", c.getId(),
                "name", c.getFileName()))
            .collect(toImmutableList()));
    }

    @Method(HttpMethod.POST)
    @Path("/mp3ify")
    @JsonBodyCodec
    public Response<Object> mp3ify(Request<Mp3ifyBody> request) {
        if (request.getBody() == null) {
            return SimpleResponse.of(400, "body.not.provided");
        }
        String video = request.getBody().getVideo();
        if (Strings.isNullOrEmpty(video)) {
            return SimpleResponse.of(400, "video.not.provided");
        }
        return SimpleResponse.of(201, ConversionManager.newConversion(video).getId());
    }

    @Path("/mp3ify/{*}/status")
    @JsonBodyCodec
    public Response<Object> mp3ifyStatus(String id) {
        return conversionAndPropertyMap(id, Conversion::getStatus, (conversion, status) -> {
            ImmutableMap.Builder<String, Object> data = ImmutableMap.builder();
            data.put("status", status);
            if (status == Status.FAILED) {
                data.put("reason", checkNotNull(conversion.getFailureReason()));
            }
            return SimpleResponse.<Object>of(200, data.build());
        }).orElseGet(() -> SimpleResponse.of(404, id));
    }

    @Path("/mp3ify/{*}/rawOutput")
    @JsonBodyCodec
    public Response<Object> mp3ifyRawOutput(String id) {
        return conversion(id)
            .map(Conversion::getRawOutput)
            .map(b -> SimpleResponse.<Object>of(200, b))
            .orElseGet(() -> SimpleResponse.of(404, id));
    }

    @Path("/mp3ify/{*}/fileName")
    @JsonBodyCodec
    public Response<Object> mp3ifyFileName(String id) {
        return conversion(id)
            .map(Conversion::getFileName)
            .map(b -> SimpleResponse.<Object>of(200, b))
            .orElseGet(() -> SimpleResponse.of(404, id));
    }

    @Path("/mp3ify/{*}/stream")
    @Produces("text/event-stream")
    public CompletionStage<Response<Object>> mp3ifyStream(Request<Object> request, String id) {
        return conversion(id)
            .map(Conversion::getObservableEvents)
            .map(events -> makeStream(request, events))
            .orElseGet(() -> {
                CompletionStage<Response<Object>> respStage = CompletableFuture.completedFuture(SimpleResponse.of(404, id));
                return respStage;
            });
    }

    private CompletionStage<Response<Object>> makeStream(Request<Object> request, ObservableList<ServerSentEvent> events) {
        String lastEventId = request.getHeaders().getSingleValue("Last-Event-ID").orElse(null);

        int skip = 0;
        if (lastEventId != null) {
            try {
                // ID is number of events passed out
                skip = Integer.parseInt(lastEventId) + 1;
            } catch (NumberFormatException ignored) {
                // malformed ID is fine, we'll just ignore it
            }
        }

        @SuppressWarnings("unchecked")
        CompletionStage<Response<Object>> response = (CompletionStage<Response<Object>>) EventListStreamer.subscribe(events, skip);
        return response;
    }

    @Path("/mp3ify/{*}/download")
    @Produces("application/octet-stream")
    public Response<Object> mp3ifyDownload(String id) throws IOException {
        Conversion conversion = ConversionManager.getConversion(id);
        if (conversion == null) {
            return SimpleResponse.of(404, id);
        }
        switch (conversion.getStatus()) {
            case SUCCESSFUL:
                break;
            case FAILED:
                return SimpleResponse.of(409, ImmutableMap.of(
                    "error", "conversion.failed"));
            default:
                return SimpleResponse.of(409, ImmutableMap.of(
                    "error", "conversion.not.finished",
                    "status", conversion.getStatus().toString()));
        }
        InputStream stream = Files.newInputStream(conversion.getResultFile());
        stream = new BufferedInputStream(stream, 8192);
        return SimpleResponse.builder()
            .ok_200()
            .body(stream)
            .headers(ImmutableMap.of(
                "content-disposition", "attachment; filename=\"" + conversion.getFileName() + "\"",
                "content-length", String.valueOf(Files.size(conversion.getResultFile())),
                // ensure netty gzip is not applied
                "content-encoding", "identity"))
            .build();
    }

    @Path("/assets/{**}")
    public Response<Object> assets(String path) throws IOException {
        URL resource;
        try {
            resource = Resources.getResource(path);
        } catch (IllegalArgumentException notFound) {
            return SimpleResponse.of(404, path);
        }

        File fileResource = assets.getAsset(resource).toFile();

        return SimpleResponse.builder()
            .headers(ImmutableMap.of(
                "content-type", getContentType(fileResource),
                "content-length", String.valueOf(fileResource.length())))
            .ok_200()
            .body(new BufferedInputStream(new FileInputStream(fileResource)))
            .build();
    }

    private final Detector detector = new DefaultDetector();

    private String getContentType(File resource) {
        Metadata metadata = new Metadata();
        metadata.add(Metadata.RESOURCE_NAME_KEY, resource.getName());
        try (InputStream stream = new BufferedInputStream(new FileInputStream(resource))) {
            return detector.detect(stream, metadata).toString();
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private static Optional<Conversion> conversion(String id) {
        return Optional.ofNullable(ConversionManager.getConversion(id));
    }

    private static <T, R> Optional<R> conversionAndPropertyMap(String id, Function<Conversion, T> propertyGetter, BiFunction<Conversion, T, R> func) {
        return conversion(id).map(conv -> {
            T prop = propertyGetter.apply(conv);
            return func.apply(conv, prop);
        });
    }

    @NotFoundHandler
    @JsonBodyCodec
    public Response<Object> notFound() {
        return SimpleResponse.of(404, ImmutableMap.of("error", "not.found"));
    }

    @ServerErrorHandler
    @JsonBodyCodec
    public Response<Object> error(Throwable t) {
        if (t instanceof IllegalArgumentException) {
            return SimpleResponse.of(HttpResponseStatus.BAD_REQUEST.code(), ImmutableMap.of("error", "bad.request", "message", t.getMessage()));
        }
        LOGGER.error("Error processing request", t);
        return SimpleResponse.of(500, ImmutableMap.of("error", "server.error"));
    }

}
