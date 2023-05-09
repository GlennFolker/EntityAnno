package ent;

import org.gradle.api.provider.*;

import java.io.*;

/**
 * Necessary extension data for {@link EntityAnnoPlugin}.
 * @author GlennFolker
 */
public interface EntityAnnoExtension{
    /** @return The mod's internal name. */
    Property<String> getModName();

    /** @return The location to store entity revision data. */
    Property<File> getRevisionDir();

    /** @return Package name for fetched vanilla component classes, typically {@code modname.fetched}. Excluded in the JAR. */
    Property<String> getFetchPackage();
    /** @return Package name for "origin" component classes, typically {@code modname.entities.comp}. Excluded in the JAR. */
    Property<String> getGenSrcPackage();
    /** @return Package name for root generated package, typically {@code modname.gen}. */
    Property<String> getRootPackage();
}
