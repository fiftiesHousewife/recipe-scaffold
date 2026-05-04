

///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS info.picocli:picocli:4.7.7

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "recipescaffold",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Scaffold an OpenRewrite recipe project from openrewrite-recipe-template-fhw.",
        subcommands = {RecipeScaffold.Init.class}
)
public class RecipeScaffold implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.err);
    }

    public static void main(final String[] args) {
        System.exit(new CommandLine(new RecipeScaffold()).execute(args));
    }

    @Command(
            name = "init",
            description = "Scaffold a new OpenRewrite recipe project from the template.",
            mixinStandardHelpOptions = true
    )
    static class Init implements Callable<Integer> {

        @Option(names = "--group", required = true,
                description = "Maven group, e.g. io.github.acme.")
        String group;

        @Option(names = "--artifact", required = true,
                description = "Maven artifact id, e.g. acme-rewrite-recipes.")
        String artifact;

        @Option(names = {"--package", "--root-package"}, required = true,
                description = "Java root package, e.g. io.github.acme. Recipes live at <package>.recipes.")
        String rootPackage;

        @Option(names = "--initial-version", defaultValue = "0.1",
                description = "First version of the scaffolded project. Default: ${DEFAULT-VALUE}.")
        String initialVersion;

        @Option(names = "--recipe-name", required = true,
                description = "POM <name>, e.g. \"Acme Recipes\".")
        String recipeName;

        @Option(names = "--recipe-description", required = true,
                description = "POM <description>.")
        String recipeDescription;

        @Option(names = "--github-org", required = true,
                description = "GitHub org/user for SCM URLs.")
        String githubOrg;

        @Option(names = "--github-repo", required = true,
                description = "GitHub repo name for SCM URLs.")
        String githubRepo;

        @Option(names = "--author-id", required = true,
                description = "POM developer id.")
        String authorId;

        @Option(names = "--author-name", required = true,
                description = "POM developer name.")
        String authorName;

        @Option(names = "--author-email", required = true,
                description = "POM developer email.")
        String authorEmail;

        @Option(names = "--java-target-main", defaultValue = "17",
                description = "release for compileJava. Default: ${DEFAULT-VALUE}.")
        String javaTargetMain;

        @Option(names = "--java-target-tests", defaultValue = "25",
                description = "release for compileTestJava and toolchain. Default: ${DEFAULT-VALUE}.")
        String javaTargetTests;

        @Option(names = "--rewrite-plugin-version", defaultValue = "7.30.0",
                description = "Used in docs snippets. Default: ${DEFAULT-VALUE}.")
        String rewritePluginVersion;

        @Option(names = {"-d", "--directory"},
                description = "Output directory. Default: ./<artifact>.")
        Path outputDir;

        @Option(names = "--template-dir",
                description = "Template source dir. Default: walks upward from cwd looking for template/build.gradle.kts.")
        Path templateDir;

        @Option(names = "--force",
                description = "Overwrite an existing output directory.")
        boolean force;

        @Option(names = "--verify",
                description = "Run ./gradlew check smokeTest after scaffolding.")
        boolean verify;

        @Override
        public Integer call() throws Exception {
            Path src = templateDir != null
                    ? templateDir.toAbsolutePath().normalize()
                    : findTemplateDir();
            if (!Files.isDirectory(src) || !Files.isRegularFile(src.resolve("build.gradle.kts"))) {
                System.err.println("not a template dir (no build.gradle.kts): " + src);
                System.err.println("pass --template-dir explicitly or run from a checkout of openrewrite-recipe-template-fhw");
                return 2;
            }

            Path out = outputDir != null
                    ? outputDir.toAbsolutePath().normalize()
                    : Paths.get(artifact).toAbsolutePath().normalize();
            if (Files.exists(out)) {
                if (!force) {
                    System.err.println("output already exists (pass --force to overwrite): " + out);
                    return 2;
                }
                System.out.println("removing existing " + out);
                deleteRecursively(out);
            }

            System.out.println("scaffolding " + out + " from " + src);
            copyTree(src, out);

            renamePackageMarkers(out, rootPackage.replace('.', '/'));

            Map<String, String> repl = new LinkedHashMap<>();
            repl.put("{{group}}", group);
            repl.put("{{artifact}}", artifact);
            repl.put("{{rootPackage}}", rootPackage);
            repl.put("{{initialVersion}}", initialVersion);
            repl.put("{{recipeName}}", recipeName);
            repl.put("{{recipeDescription}}", recipeDescription);
            repl.put("{{githubOrg}}", githubOrg);
            repl.put("{{githubRepo}}", githubRepo);
            repl.put("{{authorId}}", authorId);
            repl.put("{{authorName}}", authorName);
            repl.put("{{authorEmail}}", authorEmail);
            repl.put("{{javaTargetMain}}", javaTargetMain);
            repl.put("{{javaTargetTests}}", javaTargetTests);
            repl.put("{{rewritePluginVersion}}", rewritePluginVersion);
            repl.put("__ROOT_PACKAGE__", rootPackage);
            substituteIn(out, repl);

            List<String> residuals = findResiduals(out);
            if (!residuals.isEmpty()) {
                System.err.println("FAIL: unsubstituted placeholder(s):");
                residuals.forEach(System.err::println);
                return 3;
            }

            System.out.println("OK: scaffold complete at " + out);

            if (verify) {
                System.out.println("running ./gradlew check smokeTest in " + out);
                int rc = runGradle(out);
                if (rc != 0) {
                    System.err.println("FAIL: ./gradlew check smokeTest exited " + rc);
                    return rc;
                }
                System.out.println("OK: gates passed at " + out);
            }
            return 0;
        }

        private static Path findTemplateDir() {
            Path here = Paths.get("").toAbsolutePath();
            for (Path p = here; p != null; p = p.getParent()) {
                Path candidate = p.resolve("template").resolve("build.gradle.kts");
                if (Files.isRegularFile(candidate)) {
                    return p.resolve("template");
                }
            }
            return here.resolve("template");
        }

        private static void copyTree(Path src, Path dst) throws IOException {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                    Path rel = src.relativize(d);
                    if (rel.getNameCount() > 0) {
                        String first = rel.getName(0).toString();
                        if (first.equals(".gradle") || first.equals("build") || first.equals(".idea")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    Files.createDirectories(dst.resolve(rel));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Path rel = src.relativize(f);
                    Path target = dst.resolve(rel);
                    Files.createDirectories(target.getParent());
                    Files.copy(f, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        private static void renamePackageMarkers(Path out, String pkgPath) throws IOException {
            for (String parent : new String[]{
                    "src/main/java", "src/test/java",
                    "src/integrationTest/java", "src/smokeTest/java"}) {
                Path p = out.resolve(parent);
                if (!Files.isDirectory(p)) {
                    continue;
                }
                Path marker = p.resolve("__ROOT_PACKAGE__");
                if (!Files.isDirectory(marker)) {
                    continue;
                }
                Path target = p.resolve(pkgPath);
                Files.createDirectories(target);
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(marker)) {
                    for (Path child : ds) {
                        Files.move(child, target.resolve(marker.relativize(child)));
                    }
                }
                Files.delete(marker);
            }
        }

        private static void deleteRecursively(Path p) throws IOException {
            if (!Files.exists(p)) {
                return;
            }
            Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.delete(f);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        private static final Set<String> TEXT_EXTS = Set.of(
                ".kts", ".gradle", ".toml", ".java", ".yml", ".yaml",
                ".md", ".properties", ".sh"
        );
        private static final Set<String> TEXT_NAMES = Set.of(
                ".gitignore"
        );

        private static boolean isTextFile(Path p) {
            String name = p.getFileName().toString();
            if (TEXT_NAMES.contains(name)) {
                return true;
            }
            int dot = name.lastIndexOf('.');
            if (dot < 0) {
                return false;
            }
            return TEXT_EXTS.contains(name.substring(dot));
        }

        private static void substituteIn(Path root, Map<String, String> repl) throws IOException {
            try (var stream = Files.walk(root)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (!Files.isRegularFile(p) || !isTextFile(p)) {
                        continue;
                    }
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    String updated = content;
                    for (Map.Entry<String, String> e : repl.entrySet()) {
                        updated = updated.replace(e.getKey(), e.getValue());
                    }
                    if (!updated.equals(content)) {
                        Files.writeString(p, updated, StandardCharsets.UTF_8);
                    }
                }
            }
        }

        // Match our `{{name}}` placeholders only — not GitHub Actions `${{ secrets.X }}` expressions.
        private static final Pattern RESIDUAL = Pattern.compile("(?<!\\$)\\{\\{[a-zA-Z][a-zA-Z0-9]*\\}\\}|__ROOT_PACKAGE__");

        private static List<String> findResiduals(Path root) throws IOException {
            List<String> hits = new ArrayList<>();
            try (var stream = Files.walk(root)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (!Files.isRegularFile(p) || !isTextFile(p)) {
                        continue;
                    }
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    Matcher m = RESIDUAL.matcher(content);
                    int line = 1;
                    int scanned = 0;
                    while (m.find()) {
                        for (int i = scanned; i < m.start(); i++) {
                            if (content.charAt(i) == '\n') {
                                line++;
                            }
                        }
                        scanned = m.start();
                        hits.add(p + ":" + line + ": " + m.group());
                    }
                }
            }
            return hits;
        }

        private static int runGradle(Path dir) throws IOException, InterruptedException {
            String cmd = System.getProperty("os.name").toLowerCase().startsWith("windows")
                    ? "gradlew.bat"
                    : "./gradlew";
            ProcessBuilder pb = new ProcessBuilder(cmd, "check", "smokeTest")
                    .directory(dir.toFile())
                    .inheritIO();
            return pb.start().waitFor();
        }
    }
}
