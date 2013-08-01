package codetroopers.wicket.web;

import codetroopers.wicket.restx.CompilationManager;
import codetroopers.wicket.restx.MoreFiles;
import com.google.common.base.Splitter;
import com.google.common.eventbus.EventBus;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static com.google.common.collect.Iterables.*;

/**
 * @author <a href="mailto:cedric@gatay.fr">Cedric Gatay</a>
 */
public final class CompilationManagerHelper {

    public static final String KEY_TARGET = "wicket.hotreload.targetClasses";
    public static final String KEY_SOURCES = "wicket.hotreload.sourceRoots";
    public static final String KEY_ROOTPKG = "wicket.hotreload.rootPackage";
    public static final String KEY_AUTO = "wicket.hotreload.auto";
    public static final String KEY_WATCH = "wicket.hotreload.watch";
    public static final String KEY_ENABLED = "wicket.hotreload.enabled";

    private CompilationManagerHelper() {
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

}
