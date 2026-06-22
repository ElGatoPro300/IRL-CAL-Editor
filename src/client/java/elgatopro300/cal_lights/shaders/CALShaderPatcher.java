package elgatopro300.cal_lights.shaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

public class CALShaderPatcher {

    private static boolean isTextFile(String name) {
        name = name.toLowerCase();
        return name.endsWith(".glsl") || name.endsWith(".vsh") || name.endsWith(".fsh") ||
               name.endsWith(".gsh") || name.endsWith(".csh") || name.endsWith(".properties") ||
               name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".lang") ||
               name.endsWith(".conf");
    }

    private static boolean isSameBinaryFile(Path p1, Path p2) throws IOException {
        if (Files.size(p1) != Files.size(p2)) return false;
        try (InputStream in1 = Files.newInputStream(p1);
             InputStream in2 = Files.newInputStream(p2)) {
            byte[] buf1 = new byte[8192];
            byte[] buf2 = new byte[8192];
            int numRead1;
            while ((numRead1 = in1.read(buf1)) != -1) {
                int numRead2 = 0;
                while (numRead2 < numRead1) {
                    int r = in2.read(buf2, numRead2, numRead1 - numRead2);
                    if (r == -1) return false;
                    numRead2 += r;
                }
                if (!Arrays.equals(buf1, 0, numRead1, buf2, 0, numRead1)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void copyCrossFileSystem(Path source, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (InputStream in = Files.newInputStream(source)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void createPatch(Path originalPack, Path modifiedPack, Path patchOutputFile) throws IOException {
        Files.deleteIfExists(patchOutputFile);

        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI patchUri = URI.create("jar:" + patchOutputFile.toUri());

        try (FileSystem originalFs = getFileSystem(originalPack);
             FileSystem modifiedFs = getFileSystem(modifiedPack);
             FileSystem patchFs = FileSystems.newFileSystem(patchUri, env)) {

            Path originalRoot = getRootPath(originalFs, originalPack);
            Path modifiedRoot = getRootPath(modifiedFs, modifiedPack);
            Path patchRoot = patchFs.getPath("/");

            Files.walkFileTree(modifiedRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path modifiedFile, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = modifiedRoot.relativize(modifiedFile);
                    String relStr = relativePath.toString().replace("\\", "/");
                    if (relStr.startsWith("/")) relStr = relStr.substring(1);

                    Path originalFile = originalRoot.resolve(relativePath);
                    Path destInPatch = patchRoot.resolve(relStr);

                    if (!Files.exists(originalFile)) {
                        // CASO 1: Archivo NUEVO completo -> Copiar completo al parche
                        copyCrossFileSystem(modifiedFile, destInPatch);
                    } else {
                        // CASO 2: Existe en ambos
                        if (isTextFile(relStr)) {
                            List<String> originalLines = Files.readAllLines(originalFile);
                            List<String> modifiedLines = Files.readAllLines(modifiedFile);

                            Patch<String> diff = DiffUtils.diff(originalLines, modifiedLines);
                            if (!diff.getDeltas().isEmpty()) {
                                List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                                        relStr,
                                        relStr,
                                        originalLines,
                                        diff,
                                        3
                                );
                                Path patchFileDest = patchRoot.resolve(relStr + ".patch");
                                if (patchFileDest.getParent() != null) {
                                    Files.createDirectories(patchFileDest.getParent());
                                }
                                Files.write(patchFileDest, unifiedDiff);
                            }
                        } else {
                            // Binario -> Copiar completo solo si difieren
                            if (!isSameBinaryFile(originalFile, modifiedFile)) {
                                copyCrossFileSystem(modifiedFile, destInPatch);
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static void applyPatch(Path originalPack, Path patchFile, Path outputPack) throws IOException {
        if (Files.isDirectory(outputPack)) {
            Files.createDirectories(outputPack);
        } else {
            Files.deleteIfExists(outputPack);
        }

        Map<String, String> createEnv = new HashMap<>();
        createEnv.put("create", "true");

        Map<String, String> readEnv = new HashMap<>();
        readEnv.put("create", "false");

        try (FileSystem originalFs = getFileSystem(originalPack);
             FileSystem patchFs = FileSystems.newFileSystem(patchFile, readEnv);
             FileSystem outputFs = Files.isDirectory(outputPack) ? null : FileSystems.newFileSystem(URI.create("jar:" + outputPack.toUri()), createEnv)) {

            Path originalRoot = getRootPath(originalFs, originalPack);
            Path patchRoot = patchFs.getPath("/");
            Path outputRoot = (outputFs != null) ? outputFs.getPath("/") : outputPack;

            // 1. Copiar todos los archivos originales al destino
            Files.walkFileTree(originalRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = originalRoot.relativize(file);
                    Path destFile = outputRoot.resolve(relativePath.toString().replace("\\", "/"));
                    copyCrossFileSystem(file, destFile);
                    return FileVisitResult.CONTINUE;
                }
            });

            // 2. Procesar el parche y aplicar cambios
            Files.walkFileTree(patchRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = patchRoot.relativize(file);
                    String relStr = relativePath.toString().replace("\\", "/");
                    if (relStr.startsWith("/")) relStr = relStr.substring(1);

                    if (relStr.endsWith(".patch")) {
                        String targetRelPath = relStr.substring(0, relStr.length() - 6);
                        Path fileToPatch = outputRoot.resolve(targetRelPath);

                        if (Files.exists(fileToPatch)) {
                            List<String> targetLines = Files.readAllLines(fileToPatch);
                            List<String> patchLines = Files.readAllLines(file);

                            Patch<String> parsedPatch = UnifiedDiffUtils.parseUnifiedDiff(patchLines);
                            try {
                                List<String> patchedLines = DiffUtils.patch(targetLines, parsedPatch);
                                Files.write(fileToPatch, patchedLines);
                            } catch (PatchFailedException e) {
                                throw new IOException("Patch failed for: " + targetRelPath, e);
                            }
                        }
                    } else if (!relStr.equals("patch.json")) {
                        Path destFile = outputRoot.resolve(relStr);
                        copyCrossFileSystem(file, destFile);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static FileSystem getFileSystem(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return null;
        }
        URI uri = URI.create("jar:" + path.toUri());
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");
        try {
            return FileSystems.newFileSystem(uri, env);
        } catch (FileSystemAlreadyExistsException e) {
            return FileSystems.getFileSystem(uri);
        }
    }

    private static Path getRootPath(FileSystem fs, Path originalPath) {
        return (fs != null) ? fs.getPath("/") : originalPath;
    }
}
