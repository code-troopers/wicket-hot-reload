package codetroopers.wicket.web;

import codetroopers.wicket.restx.CompilationManager;
import codetroopers.wicket.restx.MoreFiles;
import com.google.common.base.Splitter;
import com.google.common.eventbus.EventBus;
import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static com.google.common.collect.Iterables.*;

/**
 * @author <a href="mailto:cedric@gatay.fr">Cedric Gatay</a>
 */
public final class HotReloadingUtils {

    public static final String KEY_TARGET = "wicket.hotreload.targetClasses";
    public static final String KEY_SOURCES = "wicket.hotreload.sourceRoots";
    public static final String KEY_ROOTPKG = "wicket.hotreload.rootPackage";
    public static final String KEY_AUTO = "wicket.hotreload.auto";
    public static final String KEY_WATCH = "wicket.hotreload.watch";
    public static final String KEY_ENABLED = "wicket.hotreload.enabled";

    private static final Logger LOGGER = LoggerFactory.getLogger(HotReloadingUtils.class);

    private HotReloadingUtils() {
    }

    public static CompilationManager newAppCompilationManager(EventBus eventBus) {
        return new CompilationManager(eventBus, getSourceRoots(), getTargetClasses());
    }

    public static Path getTargetClasses() {
        return FileSystems.getDefault().getPath(System.getProperty(KEY_TARGET, "tmp/classes"));
    }

    public static Iterable<Path> getSourceRoots() {
        return transform(Splitter.on(',').trimResults().split(
                System.getProperty(KEY_SOURCES,
                                   "src/main/java, src/main/resources")),
                         MoreFiles.strToPath);
    }
    
    public static String getRootPackageName(){
        return System.getProperty(KEY_ROOTPKG, "");
    }

    public static boolean isAutoCompileEnabled(){
        return Boolean.parseBoolean(System.getProperty(KEY_AUTO, "false"));
    }
    
    public static boolean isWatchCompileEnabled(){
        return Boolean.parseBoolean(System.getProperty(KEY_WATCH, "false"));
    }
    
    public static boolean isHotReloadEnabled(){
        return isAutoCompileEnabled() || isWatchCompileEnabled()
               //allow to enable hot reload without autocompile, need to check IDE compile paths
               || Boolean.parseBoolean(System.getProperty(KEY_ENABLED, "false"));
    }

    /**
     * Wicket does not use the class resolver of the application to load the HomePage class.
     * This method allows to load the class using the correct resolver, so it could be reloaded
     * @param application application 
     * @param pageClazz pageClass to load
     * @return the homepage of the application
     */
    public static Class<? extends Page> reloadableHomePage(final Application application, 
                                                           final Class<? extends Page> pageClazz){
        try {
            return (Class<? extends Page>)application.getApplicationSettings().getClassResolver().resolveClass(pageClazz.getName());
        } catch (ClassNotFoundException e) {
            LOGGER.error("Unable to resolve your homePageClazz, application will not start", e);
            return null;
        }
    }

}
