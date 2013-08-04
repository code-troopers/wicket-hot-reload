package codetroopers.wicket.restx;

import codetroopers.wicket.restx.watch.FileWatchEvent;
import com.google.common.base.*;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.hash.Hashing;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.collect.Iterables.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;

/**
 * A compilation manager is responsible for compiling a set of source roots into a
 * destination directory.
 *
 * It is able to scan for changes and compile only modified files, and also watch for changes
 * to automatically compile on changes.
 *
 * It also trigger events whenever a compilation ends.
 */
public class CompilationManager {
    private static final Runnable NO_OP = new Runnable() {
        @Override
        public void run() {
        }
    };

    private static final Logger logger = LoggerFactory.getLogger(CompilationManager.class);
    private final EventBus eventBus;

    private final JavaCompiler javaCompiler;
    private final DiagnosticCollector<JavaFileObject> diagnostics;

    private final Iterable<Path> sourceRoots;
    private final Path destination;

    private final StandardJavaFileManager fileManager;

    // compile executor is a single thread, we can't perform compilations concurrently
    // all compilation will be done by the compile executor thread
    private final ScheduledExecutorService compileExecutor = Executors.newSingleThreadScheduledExecutor();

    private final ConcurrentLinkedDeque<Path> compileQueue = new ConcurrentLinkedDeque<>();

    private final Map<Path, SourceHash> hashes = new HashMap<>();

    private ExecutorService watcherExecutor;

    private ExecutorService compiledClassesExecutor;

    private volatile boolean compiling;
    // these parameters should be overridable, at least with system properties
    private final long compilationTimeout = 60; // in seconds
    private final int autoCompileQuietPeriod = 50; // ms
    private final boolean useLastModifiedTocheckChanges = true;

    public CompilationManager(EventBus eventBus, Iterable<Path> sourceRoots, Path destination) {
        this.eventBus = eventBus;
        this.sourceRoots = sourceRoots;
        this.destination = destination;

        diagnostics = new DiagnosticCollector<>();
        javaCompiler = ToolProvider.getSystemJavaCompiler();
        fileManager = javaCompiler.getStandardFileManager(diagnostics, Locale.ENGLISH, Charsets.UTF_8);
        try {
            if (!destination.toFile().exists()) {
                destination.toFile().mkdirs();
            }

            fileManager.setLocation(StandardLocation.SOURCE_PATH, transform(sourceRoots, MoreFiles.pathToFile));
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, singleton(destination.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        loadHashes();

        eventBus.register(new Object() {
            @Subscribe
            public void onWatchEvent(FileWatchEvent event) {
                WatchEvent.Kind<Path> kind = event.getKind();
                Path source = event.getDir().resolve(event.getPath());
                if (!source.toFile().isFile()) {
                    return;
                }
                if (isClass(source)){
                    //TODO beautify this, the event is fired multiple times (as many times as the number of changed files)
                    final CompilationFinishedEvent finishedEvent =
                            new CompilationFinishedEvent(CompilationManager.this, DateTime.now());
                    finishedEvent.setAffectedSources(Collections.singleton(source));
                    getEventBus().post(finishedEvent);
                }else if (isSource(source)) {
                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY
                            || kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (!queueCompile(source)) {
                            rebuild();
                        }
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        rebuild();
                    } else {
                        rebuild();
                    }
                } else {
                    copyResource(event.getDir(), event.getPath());
                }
            }
        });
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public Path getDestination() {
        return destination;
    }

    public Iterable<Path> getSourceRoots() {
        return sourceRoots;
    }

    private void copyResource(final Path dir, final Path resourcePath) {
        compileExecutor.submit(new Runnable() {
            @Override
            public void run() {
                File source = dir.resolve(resourcePath).toFile();
                if (source.isFile()) {
                    try {
                        File to = destination.resolve(resourcePath).toFile();
                        to.getParentFile().mkdirs();
                        if (!to.exists() || to.lastModified() < source.lastModified()) {
                            com.google.common.io.Files.copy(source, to);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private boolean queueCompile(final Path source) {
        boolean b = compileQueue.offerLast(source);
        if (!b) {
            return false;
        }
        compileExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                // nothing added since submission, quiet period is over
                if (compileQueue.getLast() == source) {
                    Collection<Path> sources = new HashSet<>();
                    while (!compileQueue.isEmpty()) {
                        sources.add(compileQueue.removeFirst());
                    }
                    compile(sources);
                }
            }
        }, autoCompileQuietPeriod, TimeUnit.MILLISECONDS);
        return true;
    }


    /**
     * Returns the path of the .class file containing bytecode for the given class (by name).
     *
     * @param className the class for which the class file should be returned
     * @return the Path of the class file, absent if it doesn't exists.
     */
    public Optional<Path> getClassFile(String className) {
        Path classFilePath = destination.resolve(className.replace('.', '/') + ".class");
        if (classFilePath.toFile().exists()) {
            return Optional.of(classFilePath);
        } else {
            return Optional.absent();
        }
    }

    public void startAutoCompile() {
        synchronized (this) {
            if (watcherExecutor == null) {
                watcherExecutor = Executors.newCachedThreadPool();
                for (Path sourceRoot : sourceRoots) {
                    MoreFiles.watch(sourceRoot, eventBus, watcherExecutor);
                }
            }
        }
    }

    public void stopAutoCompile() {
        synchronized (this) {
            if (watcherExecutor != null) {
                watcherExecutor.shutdownNow();
                watcherExecutor = null;
            }
        }
    }
    
    public void startWatchCompiledClasses() {
        synchronized (this) {
            if (compiledClassesExecutor == null) {
                compiledClassesExecutor = Executors.newCachedThreadPool();
                MoreFiles.watch(destination, eventBus, compiledClassesExecutor);
            }
        }
    }

    public void stopWatchCompiledClasses() {
        synchronized (this) {
            if (compiledClassesExecutor!= null) {
                compiledClassesExecutor.shutdownNow();
                compiledClassesExecutor = null;
            }
        }
    }


    public void awaitAutoCompile() {
        try {
            if (compileQueue.isEmpty()) {
                // nothing in compile queue, we wait for current compilation if any by submitting a noop task
                // and waiting for it
                compileExecutor.submit(NO_OP).get(compilationTimeout, TimeUnit.SECONDS);
            } else {
                // we are in quiet period, let's submit a task after the quiet period and for it
                // if more file changes occur during that period we may miss them, but the purpose of this method
                // is to wait for autoCompile triggered *before* the call.
                compileExecutor.schedule(
                            NO_OP, autoCompileQuietPeriod + 10, TimeUnit.MILLISECONDS)
                        .get(compilationTimeout, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs an incremental compilation.
     */
    public void incrementalCompile() {
        try {
            Exception e = compileExecutor.submit(new Callable<Exception>() {
                @Override
                public Exception call() throws Exception {
                    try {
                        final Collection<Path> sources = new ArrayList<>();
                        for (final Path sourceRoot : sourceRoots) {
                            if (sourceRoot.toFile().exists()) {
                                Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
                                    @Override
                                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                        if (isSource(file)) {
                                            if (hasSourceChanged(sourceRoot, sourceRoot.relativize(file))) {
                                                sources.add(file);
                                            }
                                        } else if (file.toFile().isFile()) {
                                            copyResource(sourceRoot, sourceRoot.relativize(file));
                                        }
                                        return FileVisitResult.CONTINUE;
                                    }
                                });
                            }
                        }
                        compile(sources);
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }
            }).get(compilationTimeout, TimeUnit.SECONDS);
            if (e != null) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isSource(Path file) {
        return file.toString().endsWith(".java");
    }
    
    private boolean isClass(Path file) {
        return file.toString().endsWith(".class");
    }

    /**
     * Clean destination and do a full build.
     */
    public void rebuild() {
        try {
            Exception e = compileExecutor.submit(new Callable<Exception>() {
                @Override
                public Exception call() throws Exception {
                    try {
                        compileQueue.clear();
                        MoreFiles.delete(destination);
                        destination.toFile().mkdirs();


                        final Collection<Path> sources = new ArrayList<>();
                        for (final Path sourceRoot : sourceRoots) {
                            if (sourceRoot.toFile().exists()) {
                                Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
                                    @Override
                                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                            throws IOException {
                                        if (isSource(file)) {
                                            sources.add(file);
                                        } else if (file.toFile().isFile()) {
                                            copyResource(sourceRoot, sourceRoot.relativize(file));
                                        }
                                        return FileVisitResult.CONTINUE;
                                    }
                                });
                            }
                        }

                        compile(sources);
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }
            }).get(compilationTimeout, TimeUnit.SECONDS);
            if (e != null) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void compileSources(final Path... sources) {
        try {
            Exception e = compileExecutor.submit(new Callable<Exception>() {
                @Override
                public Exception call() throws Exception {
                    compile(asList(sources));
                    return null;
                }
            }).get(compilationTimeout, TimeUnit.SECONDS);
            if (e != null) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private void compile(Collection<Path> sources) {
        // MUST BE CALLED in compileExecutor only
        Stopwatch stopwatch = new Stopwatch().start();
        compiling = true;
        try {
            Iterable<? extends JavaFileObject> javaFileObjects =
                    fileManager.getJavaFileObjectsFromFiles(transform(sources, MoreFiles.pathToFile));

            if (isEmpty(javaFileObjects)) {
                logger.debug("compilation finished: up to date");
                return;
            }
            JavaCompiler.CompilationTask compilationTask = javaCompiler.getTask(
                    null, fileManager, diagnostics, null, null, javaFileObjects);

            boolean valid = compilationTask.call();
            if (valid) {
                for (Path source : sources) {
                    Path dir = null;
                    for (Path sourceRoot : sourceRoots) {
                        if ((source.isAbsolute() && source.startsWith(sourceRoot.toAbsolutePath()))
                                || (!source.isAbsolute() && source.startsWith(sourceRoot))) {
                            dir = sourceRoot;
                            break;
                        }
                    }
                    if (dir == null) {
                        logger.warn("can't find sourceRoot for {}", source);
                    } else {
                        SourceHash sourceHash = newSourceHashFor(dir, source.isAbsolute() ?
                                dir.toAbsolutePath().relativize(source) :
                                dir.relativize(source)
                        );
                        hashes.put(source.toAbsolutePath(), sourceHash);
                    }
                }

                saveHashes();

                logger.info("compilation finished: {} sources compiled in {}", sources.size(), stopwatch.stop());
                final CompilationFinishedEvent event = new CompilationFinishedEvent(this, DateTime.now());
                event.setAffectedSources(sources);
                eventBus.post(event);
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    logger.debug("{}", d);
                }
            } else {
                StringBuilder sb = new StringBuilder();
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    sb.append(d).append("\n");
                }
                throw new RuntimeException("Compilation failed:\n" + sb);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            compiling = false;
        }
    }

    private void saveHashes() {
        File hashesFile = hashesFile();
        hashesFile.getParentFile().mkdirs();

        try (Writer w = com.google.common.io.Files.newWriter(hashesFile, Charsets.UTF_8)) {
            for (SourceHash sourceHash : hashes.values()) {
                w.write(sourceHash.serializeAsString());
                w.write("\n");
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadHashes() {
        File hashesFile = hashesFile();
        if (hashesFile.exists()) {
            try (BufferedReader r = com.google.common.io.Files.newReader(hashesFile, Charsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    SourceHash sourceHash = parse(line);
                    hashes.put(sourceHash.getDir().resolve(sourceHash.getSourcePath()).toAbsolutePath(), sourceHash);
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private File hashesFile() {
        return destination.resolve("META-INF/.hashes").toFile();
    }

    /**
     * @return true if this compilation manager is currently performing a compilation task.
     */
    public boolean isCompiling() {
        return compiling;
    }

    private boolean hasSourceChanged(Path dir, Path source) {
        try {
            SourceHash sourceHash = hashes.get(dir.resolve(source).toAbsolutePath());
            if (sourceHash != null) {
                return sourceHash.hasChanged() != sourceHash;
            } else {
                return true;
            }
        } catch (IOException e) {
            return true;
        }
    }

    private class SourceHash {
        private final Path dir;
        private final Path sourcePath;
        private final String hash;
        private final long lastModified;

        private SourceHash(Path dir, Path sourcePath, String hash, long lastModified) {
            this.dir = dir;
            this.sourcePath = sourcePath;
            this.hash = hash;
            this.lastModified = lastModified;
        }

        @Override
        public String toString() {
            return "SourceHash{" +
                    "dir=" + dir +
                    ", sourcePath=" + sourcePath +
                    ", hash='" + hash + '\'' +
                    ", lastModified=" + lastModified +
                    '}';
        }

        public Path getDir() {
            return dir;
        }

        public Path getSourcePath() {
            return sourcePath;
        }

        public String getHash() {
            return hash;
        }

        public long getLastModified() {
            return lastModified;
        }

        public SourceHash hasChanged() throws IOException {
            File sourceFile = dir.resolve(sourcePath).toFile();
            if (useLastModifiedTocheckChanges) {
                if (lastModified < sourceFile.lastModified()) {
                    return new SourceHash(dir, sourcePath,
                            computeHash(), sourceFile.lastModified());
                }
            } else {
                String currentHash = computeHash();
                if (!currentHash.equals(hash)) {
                    return new SourceHash(dir, sourcePath,
                            currentHash, sourceFile.lastModified());
                }
            }
            return this;
        }

        private String computeHash() throws IOException {
            return hash(dir.resolve(sourcePath).toFile());
        }

        public String serializeAsString() throws IOException {
            return Joiner.on("**").join(dir, sourcePath, hash, lastModified);
        }
    }

    private SourceHash newSourceHashFor(Path dir, Path sourcePath) throws IOException {
        File sourceFile = dir.resolve(sourcePath).toFile();
        return new SourceHash(dir, sourcePath, hash(sourceFile), sourceFile.lastModified());
    }

    private String hash(File file) throws IOException {
        return com.google.common.io.Files.hash(file, Hashing.md5()).toString();
    }

    private SourceHash parse(String str) {
        Iterator<String> parts = Splitter.on("**").split(str).iterator();
        FileSystem fileSystem = FileSystems.getDefault();
        return new SourceHash(
                fileSystem.getPath(parts.next()),
                fileSystem.getPath(parts.next()),
                parts.next(),
                Long.parseLong(parts.next())
        );
    }
}
