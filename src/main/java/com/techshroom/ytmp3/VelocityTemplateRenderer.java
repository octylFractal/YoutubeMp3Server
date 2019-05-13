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

package com.techshroom.ytmp3;

import com.google.common.io.Resources;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.implement.IncludeRelativePath;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeInstance;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class VelocityTemplateRenderer implements TemplateRenderer {

    private static final RuntimeInstance VELOCITY = new RuntimeInstance();

    static {
        Properties p = new Properties();
        URL propSource = Resources.getResource("com/techshroom/ytmp3/templates/velocity.properties");
        try (Reader in = Resources.asCharSource(propSource, StandardCharsets.UTF_8).openBufferedStream()) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        VELOCITY.init(p);
    }

    public static VelocityTemplateRenderer load(String location) {
        return new VelocityTemplateRenderer(VELOCITY.getTemplate(location, StandardCharsets.UTF_8.name()));
    }

    private static final Context EVENT_CONTEXT = new VelocityContext();

    static {
        EventCartridge events = new EventCartridge();
        events.addIncludeEventHandler(new IncludeRelativePath());
        events.attachToContext(EVENT_CONTEXT);
    }

    private final Template template;

    private VelocityTemplateRenderer(Template template) {
        this.template = template;
    }

    @Override
    public String render(Map<String, Object> parameters) {
        StringWriter writer = new StringWriter();
        Context ctx = new VelocityContext(new HashMap<>(parameters), EVENT_CONTEXT);
        template.merge(ctx, writer);
        return writer.toString();
    }

}
