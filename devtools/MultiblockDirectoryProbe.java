package c_y.meknh_core.api.multiblock;

import cpw.mods.niofs.union.UnionFileSystemProvider;
import java.nio.file.*;
import java.util.*;

/** Regression for the actual directory operation used during mod construction. */
public final class MultiblockDirectoryProbe {
    static void check(Path directory, String label) throws Exception {
        Set<String> found = new TreeSet<>();
        try (DirectoryStream<Path> files = MultiblockDefinitionFiles.open(directory)) {
            for (Path file : files) {
                found.add(file.getFileName().toString());
                if (!Files.readString(file).equals("{}")) throw new AssertionError("unreadable JSON");
            }
        }
        if (!found.equals(Set.of("pump.json"))) throw new AssertionError(label + ": " + found);
        System.out.println("PASS " + label + " JSON filtering and reading");
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("meknh-union-regression-");
        try {
            Files.writeString(root.resolve("pump.json"), "{}");
            Files.writeString(root.resolve("ignore.txt"), "ignored");
            Files.createDirectory(root.resolve("directory.json"));
            check(root, "default filesystem");
            try (var union = new UnionFileSystemProvider().newFileSystem(null, root)) {
                check(union.getRoot(), "NeoForge Union filesystem");
            }
            Path archive = root.resolve("fixture.zip");
            try (var zip = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
                Files.writeString(zip.getPath("/pump.json"), "{}");
                Files.writeString(zip.getPath("/ignore.txt"), "ignored");
                Files.createDirectory(zip.getPath("/directory.json"));
            }
            try (var union = new UnionFileSystemProvider().newFileSystem(null, archive)) {
                check(union.getRoot(), "packaged ZIP-backed Union filesystem");
            }
            System.out.println("MultiblockDirectoryProbe: all checks passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
