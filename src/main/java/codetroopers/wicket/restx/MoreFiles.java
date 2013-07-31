package codetroopers.wicket.restx;

import codetroopers.wicket.restx.watch.WatcherServiceLoader;
import com.google.common.base.Function;
import com.google.common.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;

/**
 */
public class MoreFiles {
    public static final Function<? super Path, ? extends File> pathToFile = new Function<Path, File>() {
        @Override
        public File apply(Path input) {
            return input.toFile();
        }
    };
    public static final Function<String, Path> strToPath = new Function<String, Path>() {
        @Override
        public Path apply(String input) {
            return FileSystems.getDefault().getPath(input);
        }
    };

    public static void delete(Path path) throws IOException {
        if (path.toFile().isDirectory()) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    // try to delete the file anyway, even if its attributes
                    // could not be read, since delete-only access is
                    // theoretically possible
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        // directory iteration failed; propagate exception
                        throw exc;
                    }
                }
            });
        } else {
            Files.deleteIfExists(path);
        }
    }

    public static void watch(final Path dir, final EventBus eventBus, ExecutorService executor) {
        WatcherServiceLoader.getWatcherService().watch(eventBus, executor, dir, true);
    }

    public static void copyDir(final Path sourceDir, final Path targetDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, targetDir.resolve(sourceDir.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
