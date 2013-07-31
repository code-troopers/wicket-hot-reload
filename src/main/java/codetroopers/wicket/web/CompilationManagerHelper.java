package codetroopers.wicket.web;

import codetroopers.wicket.restx.CompilationManager;
import codetroopers.wicket.restx.MoreFiles;
import com.google.common.base.Splitter;
import com.google.common.eventbus.EventBus;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static com.google.common.collect.Iterables.transform;

/**
 * @author <a href="mailto:cedric@gatay.fr">Cedric Gatay</a>
 */
public final class CompilationManagerHelper {
    private CompilationManagerHelper() {
    }

    public static CompilationManager newAppCompilationManager(EventBus eventBus) {
        return new CompilationManager(eventBus, getSourceRoots(), getTargetClasses());
    }

    public static Path getTargetClasses() {
        return FileSystems.getDefault().getPath(System.getProperty("restx.targetClasses", "tmp/classes"));
    }

    public static Iterable<Path> getSourceRoots() {
        return transform(Splitter.on(',').trimResults().split(
                System.getProperty("restx.sourceRoots",
                                   "src/main/java, src/main/resources")),
                         MoreFiles.strToPath);
    }

}
