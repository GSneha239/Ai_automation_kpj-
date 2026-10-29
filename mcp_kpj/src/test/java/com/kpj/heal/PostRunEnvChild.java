package com.kpj.heal;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Harmless child program for {@link PostRunCoordinatorTest}: writes the value of {@code DEVHIS_POSTRUN} (or {@code <unset>}) it was
 * launched with to the file named by its first argument. Stands in for {@code mvn} - no Maven, browser or DevHIS involved.
 */
public final class PostRunEnvChild {
    public static void main(String[] args) throws Exception {
        String v = System.getenv("DEVHIS_POSTRUN");
        Files.writeString(Paths.get(args[0]), v == null ? "<unset>" : v);
    }
}
