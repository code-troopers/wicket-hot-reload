/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package codetroopers.wicket.web;

import static com.google.common.collect.Iterables.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.wicket.protocol.http.WicketFilter;

import codetroopers.wicket.restx.CompilationFinishedEvent;
import codetroopers.wicket.restx.CompilationManager;
import codetroopers.wicket.restx.HotReloadingClassLoader;
import codetroopers.wicket.restx.MoreFiles;

import com.google.common.base.Splitter;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;


/**
 * Simple wicket filter allowing hot reload and autocompile of sources à la play!
 * Proof of concept
 * 
 * TODO :
 *  # extract parameters through system property
 *  # rename restx.* parameters to wicket.*
 *  # use wicket application mode to trigger incremental compile only when needed
 *  # test test test test
 * 
 * @author <a href="mailto:cedric@gatay.fr">Cedric Gatay</a>
 */
public class HotReloadingWicketFilter extends WicketFilter {
    private final CompilationManager compilationManager;
    private final EventBus eventBus;
    private HotReloadingClassLoader reloadingClassLoader;

    /**
     * Instantiate the reloading class loader
     */
    public HotReloadingWicketFilter() {
        eventBus = new EventBus();
        compilationManager = CompilationManagerHelper.newAppCompilationManager(eventBus);
        rebuildClassLoader();
    }

    private void rebuildClassLoader() {
        reloadingClassLoader = new HotReloadingClassLoader(getClass().getClassLoader(), "codetroopers")
        {
            @Override
            protected InputStream getInputStream(String path) {
                try {
                    return Files.newInputStream(compilationManager.getDestination().resolve(path));
                } catch (IOException e) {
                    return null;
                }
            }
        };
    }

    /**
     * @see org.apache.wicket.protocol.http.WicketFilter#getClassLoader()
     */
    @Override
    protected ClassLoader getClassLoader() {
        return reloadingClassLoader;
    }

    /**
     * @see org.apache.wicket.protocol.http.WicketFilter#init(boolean, javax.servlet.FilterConfig)
     */
    @Override
    public void init(final boolean isServlet, final FilterConfig filterConfig)
            throws ServletException {
        //we need a better way of doing this, it resets the entire application !
        eventBus.register(new Object() {
            @Subscribe
            public void onEvent(CompilationFinishedEvent event) {
                destroy();
                rebuildClassLoader();
                try {
                    HotReloadingWicketFilter.super.init(filterConfig);
                } catch (ServletException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        super.init(isServlet, filterConfig);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        compilationManager.incrementalCompile();
        super.doFilter(request, response, chain);
    }
}
