package c_y.meknh_core.api.multiblock;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** File enumeration shared by the loader and its real-filesystem regression probe. */
final class MultiblockDefinitionFiles {
    private MultiblockDefinitionFiles() {}

    static DirectoryStream<Path> open(Path directory) throws IOException {
        // SecureJarHandler UnionFileSystem does not implement getPathMatcher.
        // The Filter overload avoids glob compilation on both development and packaged mod paths.
        return Files.newDirectoryStream(directory, file ->
                file.getFileName().toString().endsWith(".json") && Files.isRegularFile(file));
    }
}
