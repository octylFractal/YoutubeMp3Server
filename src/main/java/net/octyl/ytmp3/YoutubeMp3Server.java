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

package net.octyl.ytmp3;

import com.google.common.collect.ImmutableList;
import com.techshroom.jungle.Loaders;
import com.techshroom.jungle.PropOrEnvConfigOption;
import com.techshroom.jungle.PropOrEnvNamespace;
import com.techshroom.lettar.Router;
import com.techshroom.lettar.pipe.PipelineRouterInitializer;
import com.techshroom.templar.HttpInitializer;
import com.techshroom.templar.HttpRouterHandler;
import com.techshroom.templar.HttpServerBootstrap;
import net.octyl.ytmp3.controllers.RouteContainer;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YoutubeMp3Server {

    private static final PropOrEnvNamespace CONFIG = PropOrEnvNamespace.create("ytmp3");
    private static final PropOrEnvConfigOption<String> HOST =
        CONFIG.create("host", Loaders.forString(), "0.0.0.0");
    private static final PropOrEnvConfigOption<Integer> PORT =
        CONFIG.create("port", Loaders.forIntInRange(0, 65565), 80);
    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeMp3Server.class);

    public static void main(String[] args) {
        Router<ByteBuf, Object> router = new PipelineRouterInitializer()
            .newRouter(ImmutableList.of(new RouteContainer()));

        HttpServerBootstrap bootstrap = new HttpServerBootstrap(
            HOST.get(), PORT.get(), () -> new HttpInitializer(new HttpRouterHandler(router))
        );
        LOGGER.info("Starting YoutubeMp3Server on {}:{}", HOST.get(), PORT.get());
        bootstrap.start();
    }
}
