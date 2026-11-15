# Multiblock directory regression

The mod-construction loader must work on SecureJarHandler Union paths as well as ordinary paths.
The glob overload `Files.newDirectoryStream(path, "*.json")` calls `UnionFileSystem.getPathMatcher`,
which throws `UnsupportedOperationException` in SecureJarHandler 3.0.8. The production helper
uses the filter overload instead, accepting only regular `.json` files.

Run from the repository root with Java 21+: set `UNION` to the SecureJarHandler 3.0.8 jar,
optionally set `JAVA` and `JAVAC` to executable paths, then run:

```sh
bash devtools/check-multiblock-directory.sh
```

The probe calls the same helper as `MultiblockDataLoader` on a normal directory, a real
Union directory, and a ZIP-backed Union filesystem. It verifies JSON reading, ignores non-JSON
files and JSON-named directories, and cleans its temporary fixtures. The runner supplies the
module access required by SecureJarHandler. This is a directory-scanning regression, not a
complete Minecraft startup or gameplay test.
