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

import codetroopers.wicket.restx.CompilationFinishedEvent;
import codetroopers.wicket.restx.CompilationManager;
import codetroopers.wicket.HotReloadingClassResolver;
import codetroopers.wicket.restx.HotReloadingClassLoader;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.wicket.core.util.resource.ClassPathResourceFinder;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WicketFilter;
import org.apache.wicket.util.file.IResourceFinder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;


/**
 * Simple wicket filter allowing hot reload and autocompile of sources à la play!
 * Proof of concept
 *
 * TODO :
 *  # use wicket application mode to trigger incremental compile only when needed
 *  # test test test test
 *
 * @author <a href="mailto:cedric@gatay.fr">Cedric Gatay</a>
 */
public class HotReloadingWicketFilter extends WicketFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotReloadingWicketFilter.class);

    private final CompilationManager compilationManager;
    private final EventBus eventBus;
    private final boolean hotReloadEnabled;
    private final boolean watchCompileEnabled;
    private final boolean autoCompileEnabled;
    private HotReloadingClassLoader reloadingClassLoader;

    /**
     * Instantiate the reloading class loader
     */
    public HotReloadingWicketFilter() {
        eventBus = new EventBus();
        //TODO things are becoming messy here, need a settings parser
        hotReloadEnabled = HotReloadingUtils.isHotReloadEnabled();
        if (hotReloadEnabled) {
            compilationManager = HotReloadingUtils.newAppCompilationManager(eventBus);
            rebuildClassLoader();
            watchCompileEnabled = HotReloadingUtils.isWatchCompileEnabled();
            autoCompileEnabled = HotReloadingUtils.isAutoCompileEnabled();
            if (watchCompileEnabled) {
                compilationManager.startAutoCompile();
            }else if (!autoCompileEnabled){
                compilationManager.startWatchCompiledClasses();
            }
        } else {
            compilationManager = null;
            watchCompileEnabled = false;
            autoCompileEnabled = false;
        }
    }

    @Override
    public void destroy() {
        if (compilationManager != null) {
            compilationManager.stopAutoCompile();
            compilationManager.stopWatchCompiledClasses();
        }
        super.destroy();
    }

    private void rebuildClassLoader() {
        reloadingClassLoader = new HotReloadingClassLoader(getClass().getClassLoader(),
                                                           HotReloadingUtils.getRootPackageName()) {
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
        if (hotReloadEnabled) {
            return reloadingClassLoader;
        }
        return super.getClassLoader();
    }

    /**
     * @see org.apache.wicket.protocol.http.WicketFilter#init(boolean, javax.servlet.FilterConfig)
     */
    @Override
    public void init(final boolean isServlet, final FilterConfig filterConfig)
            throws ServletException {
        if (hotReloadEnabled) {
            eventBus.register(new Object() {
                @Subscribe
                public void onEvent(CompilationFinishedEvent event) {
                    if (event.getAffectedSources() != null) {
                        LOGGER.info("Rebuilt the following sources : {}",
                                    Joiner.on(",").join(event.getAffectedSources()));
                    }
                    rebuildClassLoader();
                    resetClassAndResourcesCaches();
                }
            });
        }
        displayHotReloadUsageInfo();
        super.init(isServlet, filterConfig);
        if (hotReloadEnabled) {
            resetClassAndResourcesCaches();
        }
    }

    /**
     * Reset the class resolver, it removes stale classes.
     * Optimization is possible by removing from the map only changed classes
     *
     * NOTICE :  as long as we don't touch the HomePage, this seems to work
     */
    private void resetClassAndResourcesCaches() {
        final WebApplication application = getApplication();
        if (application != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Resetting the Application's ClassResolver and Resource Finders");
            }
            application.getApplicationSettings().setClassResolver(new HotReloadingClassResolver());
            //we reset the resourceStreamLocator cache by recreating resource finders
            List<IResourceFinder> resourceFinders = Lists.newArrayList();
            resourceFinders.add(new org.apache.wicket.util.file.Path(compilationManager.getDestination().toString()));
            resourceFinders.add(new ClassPathResourceFinder(""));
            application.getResourceSettings().setResourceFinders(resourceFinders);
        }
    }

    private void displayHotReloadUsageInfo() {
        final String delimiter = "\n###################################################################";
        StringBuilder info =
                new StringBuilder(delimiter)
                        .append("\n#  You are using a WicketFilter allowing hot reload and auto compilation of sources.");
        if (hotReloadEnabled) {
            info.append("\n#  Hot reload is currently enabled for classes in the package '")
                    .append(HotReloadingUtils.getRootPackageName()).append("'");
            info.append("\n#  Sources are scanned in the following directories '")
                    .append(Joiner.on(',').join(compilationManager.getSourceRoots())).append("'");
            if (autoCompileEnabled) {
                info.append("\n#  Compilation will be performed if necessary every time a http request is made");
            } else if (watchCompileEnabled) {
                info.append("\n#  Compilation will be performed automatically as you change sources.");
            }
        } else {
            info.append("\n#  to use it you need to set a bunch of system properties for your environment");
            info.append("\n#  \t * ").append(HotReloadingUtils.KEY_AUTO)
                    .append(" (true|false) to enable auto compile when your application is accessed");
            info.append("\n#  \t * ").append(HotReloadingUtils.KEY_WATCH)
                    .append(" (true|false) to enable auto compile when sources are modified");
            info.append("\n#  \t * ").append(HotReloadingUtils.KEY_ENABLED)
                    .append(" (true|false) to enable hot reloading without auto compile");
            info.append("\n#  \t * ").append(HotReloadingUtils.KEY_ROOTPKG)
                    .append(" the name of the root package where reloading should be active");
            info.append("\n#  \t * ").append(HotReloadingUtils.KEY_SOURCES)
                    .append(" comma separated list of directories containing the sources you want to compile");
            info.append("\n#  \t * ").append(HotReloadingUtils.KEY_TARGET)
                    .append(" directory used as compilation target (defaults to tmp/classes)");
        }
        info.append(delimiter);
        LOGGER.info(info.toString());
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (autoCompileEnabled) {
            compilationManager.incrementalCompile();
        } else if (watchCompileEnabled) {
            compilationManager.awaitAutoCompile();
        } else if (hotReloadEnabled) {
            
        }
        super.doFilter(request, response, chain);
    }
}
