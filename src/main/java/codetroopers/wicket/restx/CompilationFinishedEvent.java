package codetroopers.wicket.restx;

import org.joda.time.DateTime;

import java.nio.file.Path;
import java.util.Collection;

/**
* User: xavierhanin
* Date: 7/26/13
* Time: 11:25 PM
*/
public class CompilationFinishedEvent {
    private final CompilationManager compilationManager;
    private final DateTime endTime;
    private Collection<Path> affectedSources;

    public CompilationFinishedEvent(CompilationManager compilationManager, DateTime endTime) {
        this.compilationManager = compilationManager;
        this.endTime = endTime;
    }

    public CompilationManager getCompilationManager() {
        return compilationManager;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public void setAffectedSources(final Collection<Path> affectedSources) {
        this.affectedSources = affectedSources;
    }

    public Collection<Path> getAffectedSources() {
        return affectedSources;
    }
}
