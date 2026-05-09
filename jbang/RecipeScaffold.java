

///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS info.picocli:picocli:4.7.7

package recipescaffold;

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
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(
        name = "recipescaffold",
        mixinStandardHelpOptions = true,
        version = RecipeScaffold.VERSION,
        description = "Scaffold an OpenRewrite recipe project from recipescaffold.",
        subcommands = {
                RecipeScaffold.Init.class,
                RecipeScaffold.AddRecipe.class,
                RecipeScaffold.VerifyGates.class,
                RecipeScaffold.UpgradeSkills.class
        }
)
public class RecipeScaffold implements Runnable {

    static final String VERSION = "0.2.0";

    static final String DROPFILE = ".recipescaffold.yml";

    static final String MARKER_DIR = "__ROOT_PACKAGE__";

    static final List<String> MARKER_PARENTS = List.of(
            "src/main/java",
            "src/test/java",
            "src/integrationTest/java",
            "src/smokeTest/java");

    static final Set<String> SKIP_DIRS = Set.of(".gradle", "build", ".idea");

    @Override
    public void run() {
        new CommandLine(this).usage(System.err);
    }

    public static void main(final String[] args) {
        System.exit(new CommandLine(new RecipeScaffold()).execute(args));
    }

    static class ProjectDirectoryMixin {
        @Option(names = {"-d", "--directory"},
                description = "Project root. Default: walks upward from cwd looking for " + DROPFILE + ".")
        Path projectDir;
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

        @Option(names = "--rewrite-plugin-version", defaultValue = "7.32.1",
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
                System.err.println("pass --template-dir explicitly or run from a checkout of recipescaffold");
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

            substituteIn(out, buildReplacements());

            List<String> residuals = findResiduals(out);
            if (!residuals.isEmpty()) {
                System.err.println("FAIL: unsubstituted placeholder(s):");
                residuals.forEach(System.err::println);
                return 3;
            }

            writeDropfile(out);

            System.out.println("OK: scaffold complete at " + out);

            if (verify) {
                System.out.println("running ./gradlew check smokeTest in " + out);
                int rc = runGradle(out, List.of("check", "smokeTest"));
                if (rc != 0) {
                    System.err.println("FAIL: ./gradlew check smokeTest exited " + rc);
                    return rc;
                }
                System.out.println("OK: gates passed at " + out);
            }
            return 0;
        }


        Map<String, String> buildReplacements() {
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
            repl.put(MARKER_DIR, rootPackage);
            return repl;
        }

        private void writeDropfile(Path out) throws IOException {
            String yaml = """
                    # recipescaffold dropfile — read by `recipescaffold add-recipe`.
                    # Generated by `recipescaffold init` v%s on first scaffold.
                    # Hand-edit if you rename the package or change Java targets.
                    recipescaffoldVersion: "%s"
                    group: "%s"
                    artifact: "%s"
                    rootPackage: "%s"
                    javaTargetMain: "%s"
                    javaTargetTests: "%s"
                    """.formatted(VERSION, VERSION, group, artifact, rootPackage, javaTargetMain, javaTargetTests);
            Files.writeString(out.resolve(DROPFILE), yaml, StandardCharsets.UTF_8);
        }

    }

    @Command(
            name = "add-recipe",
            description = "Drop a new recipe class (and test) into an existing scaffolded project.",
            mixinStandardHelpOptions = true
    )
    static class AddRecipe implements Callable<Integer> {

        @Option(names = "--name", required = true,
                description = "Recipe class name in PascalCase, e.g. RemoveStaleSuppression.")
        String name;

        @Option(names = "--type", defaultValue = "java",
                description = "Recipe skeleton kind: java | scanning | yaml | refaster. "
                        + "Default: ${DEFAULT-VALUE}.")
        String type;

        @Option(names = "--display-name",
                description = "Override the @DisplayName text. Default: humanised --name.")
        String displayName;

        @Option(names = "--description",
                description = "Override the @Description text. Default: a TODO sentence.")
        String description;

        @Option(names = "--package",
                description = "Override the recipe's package. Default: <rootPackage>.recipes from "
                        + DROPFILE + ".")
        String packageOverride;

        @Mixin
        ProjectDirectoryMixin projectDirectory = new ProjectDirectoryMixin();

        @Option(names = "--no-tests",
                description = "Skip writing the test file. Default: write the test.")
        boolean skipTests;

        @Option(names = "--force",
                description = "Overwrite existing recipe/test files.")
        boolean force;

        @Option(names = "--test-style", defaultValue = "block",
                description = "Test scaffold style: block (multi-line text blocks) | method "
                        + "(one-line before/after pair, tighter for argument-level transforms). "
                        + "Default: ${DEFAULT-VALUE}. method-style is currently only available "
                        + "with --type java|scanning.")
        String testStyle;

        private record RecipeKind(
                String mainSnippet,
                String testSnippet,
                boolean mainInResources) {}

        private static final Map<String, RecipeKind> KINDS = Map.of(
                "java", new RecipeKind(
                        "recipe-class-java.template",
                        "recipe-test.template",
                        false),
                "scanning", new RecipeKind(
                        "recipe-class-scanning.template",
                        "recipe-test.template",
                        false),
                "yaml", new RecipeKind(
                        "yaml-composition-block.template",
                        "recipe-test-yaml.template",
                        true),
                "refaster", new RecipeKind(
                        "recipe-class-refaster.template",
                        "recipe-test-refaster.template",
                        false)
        );

        @Override
        public Integer call() throws Exception {
            RecipeKind kind = KINDS.get(type);
            if (kind == null) {
                System.err.println("--type=" + type + " is not supported. Available: "
                        + String.join(", ", KINDS.keySet()) + ".");
                return 2;
            }
            if (!isPascalCase(name)) {
                System.err.println("--name must be a PascalCase Java identifier; got: " + name);
                return 2;
            }
            if (!"block".equals(testStyle) && !"method".equals(testStyle)) {
                System.err.println("--test-style=" + testStyle + " is not supported. Available: block, method.");
                return 2;
            }
            // method-style assumes `new <Name>()`; yaml uses Environment.builder, refaster references the generated *Recipes type.
            if ("method".equals(testStyle) && !("java".equals(type) || "scanning".equals(type))) {
                System.err.println("--test-style=method is currently only available with --type java|scanning; got --type=" + type + ".");
                return 2;
            }

            Path root = resolveProjectRoot(projectDirectory.projectDir);
            if (root == null) {
                return 2;
            }
            Map<String, String> drop = readDropfile(root.resolve(DROPFILE));
            String rootPackage = drop.get("rootPackage");
            if (rootPackage == null || rootPackage.isEmpty()) {
                System.err.println("FAIL: " + DROPFILE + " is missing rootPackage.");
                return 3;
            }

            String pkg = packageOverride != null ? packageOverride : rootPackage + ".recipes";
            Path snippets = root.resolve("snippets");
            if (!Files.isDirectory(snippets)) {
                System.err.println("FAIL: snippets dir not found at " + snippets);
                System.err.println("re-scaffold or copy template/snippets/ from recipescaffold.");
                return 3;
            }

            // YAML compositions go at <rootPackage>.<recipeName> per upstream convention; Java/scanning at <package>.<recipeName>.
            String recipeId = kind.mainInResources()
                    ? rootPackage + "." + name
                    : pkg + "." + name;

            Map<String, String> repl = new LinkedHashMap<>();
            repl.put("{{package}}", pkg);
            repl.put("{{recipeName}}", name);
            repl.put("{{recipeDisplayName}}", displayName != null ? displayName : humanise(name));
            repl.put("{{recipeDescription}}",
                    description != null ? description : "TODO: describe what this recipe does.");
            repl.put("{{recipeId}}", recipeId);
            repl.put("{{recipeKebab}}", kebabCase(name));

            int rc = writeRecipeFile(root, snippets, kind, pkg, repl);
            if (rc != 0) {
                return rc;
            }
            if (!skipTests) {
                rc = writeTestFile(root, snippets, kind, pkg, repl);
                if (rc != 0) {
                    return rc;
                }
            }
            return 0;
        }

        private int writeRecipeFile(Path root, Path snippets, RecipeKind kind,
                                    String pkg, Map<String, String> repl) throws IOException {
            Path mainFile = mainOutputPath(root, kind, pkg, name);
            Files.createDirectories(mainFile.getParent());
            if (Files.exists(mainFile) && !force) {
                System.err.println("refusing to overwrite " + mainFile + " (pass --force).");
                return 2;
            }
            String mainBody = applySubstitutions(
                    Files.readString(snippets.resolve(kind.mainSnippet()), StandardCharsets.UTF_8),
                    repl);
            Files.writeString(mainFile, mainBody, StandardCharsets.UTF_8);
            System.out.println("wrote " + mainFile);
            return 0;
        }

        private int writeTestFile(Path root, Path snippets, RecipeKind kind,
                                  String pkg, Map<String, String> repl) throws IOException {
            Path testDir = root.resolve("src/test/java").resolve(pkg.replace('.', '/'));
            Files.createDirectories(testDir);
            Path testFile = testDir.resolve(name + "Test.java");
            if (Files.exists(testFile) && !force) {
                System.err.println("refusing to overwrite " + testFile + " (pass --force).");
                return 2;
            }
            String testSnippet = "method".equals(testStyle)
                    ? "recipe-method-test.template"
                    : kind.testSnippet();
            String testBody = applySubstitutions(
                    Files.readString(snippets.resolve(testSnippet), StandardCharsets.UTF_8),
                    repl);
            Files.writeString(testFile, testBody, StandardCharsets.UTF_8);
            System.out.println("wrote " + testFile);
            return 0;
        }

        private static Path mainOutputPath(Path root, RecipeKind kind, String pkg, String name) {
            if (kind.mainInResources()) {
                return root.resolve("src/main/resources/META-INF/rewrite")
                        .resolve(kebabCase(name) + ".yml");
            }
            return root.resolve("src/main/java")
                    .resolve(pkg.replace('.', '/'))
                    .resolve(name + ".java");
        }


    }

    @Command(
            name = "verify-gates",
            description = "Run ./gradlew check integrationTest smokeTest in an existing scaffolded project.",
            mixinStandardHelpOptions = true
    )
    static class VerifyGates implements Callable<Integer> {

        @Mixin
        ProjectDirectoryMixin projectDirectory = new ProjectDirectoryMixin();

        @Override
        public Integer call() throws Exception {
            Path root = resolveProjectRoot(projectDirectory.projectDir);
            if (root == null) {
                return 2;
            }
            // Tasks listed explicitly so all three run even when `check` is up-to-date.
            System.out.println("running ./gradlew check integrationTest smokeTest in " + root);
            int rc = runGradle(root, List.of("check", "integrationTest", "smokeTest"));
            if (rc != 0) {
                System.err.println("FAIL: gates exited " + rc);
                return rc;
            }
            System.out.println("OK: gates passed at " + root);
            return 0;
        }

    }

    @Command(
            name = "upgrade-skills",
            description = "Refresh template/.claude/skills/ in an existing scaffolded project.",
            mixinStandardHelpOptions = true
    )
    static class UpgradeSkills implements Callable<Integer> {

        @Mixin
        ProjectDirectoryMixin projectDirectory = new ProjectDirectoryMixin();

        @Option(names = "--template-dir",
                description = "Override upstream template source. Default: walks upward from cwd looking for template/build.gradle.kts.")
        Path templateDir;

        @Option(names = "--dry-run",
                description = "Print what would change without modifying anything.")
        boolean dryRun;

        @Override
        public Integer call() throws Exception {
            Path root = resolveProjectRoot(projectDirectory.projectDir);
            if (root == null) {
                return 2;
            }

            Path src = templateDir != null
                    ? templateDir.toAbsolutePath().normalize()
                    : findTemplateDir();
            Path upstreamSkills = src.resolve(".claude/skills");
            if (!Files.isDirectory(upstreamSkills)) {
                System.err.println("FAIL: no .claude/skills under " + src);
                System.err.println("pass --template-dir explicitly or run from a checkout of recipescaffold");
                return 3;
            }

            Path projectSkills = root.resolve(".claude/skills");
            Files.createDirectories(projectSkills);

            int refreshed = 0;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(upstreamSkills)) {
                for (Path skill : ds) {
                    if (!Files.isDirectory(skill)) {
                        continue;
                    }
                    Path target = projectSkills.resolve(skill.getFileName());
                    if (dryRun) {
                        System.out.println("would refresh " + target);
                        refreshed++;
                        continue;
                    }
                    deleteRecursively(target);
                    copyDir(skill, target);
                    System.out.println("refreshed " + target);
                    refreshed++;
                }
            }
            System.out.println(dryRun ? "OK: " + refreshed + " skill(s) would be refreshed (dry-run)"
                                       : "OK: " + refreshed + " skill(s) refreshed at " + projectSkills);
            return 0;
        }
    }

    static Path findTemplateDir() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            Path candidate = p.resolve("template").resolve("build.gradle.kts");
            if (Files.isRegularFile(candidate)) {
                return p.resolve("template");
            }
        }
        return here.resolve("template");
    }

    static Path findProjectRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(DROPFILE))) {
                return p;
            }
        }
        return null;
    }

    // Returns null and prints diagnostics on failure so callers can `if (root == null) return 2;`.
    static Path resolveProjectRoot(Path explicit) {
        Path root = explicit != null ? explicit.toAbsolutePath().normalize() : findProjectRoot();
        if (root == null) {
            System.err.println("could not find " + DROPFILE + " walking upward from cwd.");
            System.err.println("pass --directory <project-root> or run from inside a scaffolded project.");
            return null;
        }
        if (!Files.isRegularFile(root.resolve(DROPFILE))) {
            System.err.println("FAIL: " + DROPFILE + " not found at " + root);
            return null;
        }
        return root;
    }

    static void deleteRecursively(Path p) throws IOException {
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

    static void copyDir(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(d)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.copy(f, dst.resolve(src.relativize(f)),
                        StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Like copyDir but skips SKIP_DIRS at the top level. Used by Init in case the template checkout has stale build/.
    static void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Path rel = src.relativize(d);
                if (rel.getNameCount() > 0 && SKIP_DIRS.contains(rel.getName(0).toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
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

    static void renamePackageMarkers(Path out, String pkgPath) throws IOException {
        for (String parent : MARKER_PARENTS) {
            Path p = out.resolve(parent);
            if (!Files.isDirectory(p)) {
                continue;
            }
            Path marker = p.resolve(MARKER_DIR);
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

    // Allowlist, not denylist: brittle by choice so substitution can never touch the wrapper jar.
    static final Set<String> TEXT_EXTS = Set.of(
            ".kts", ".gradle", ".toml", ".java", ".yml", ".yaml",
            ".md", ".properties", ".sh"
    );
    static final Set<String> TEXT_NAMES = Set.of(
            ".gitignore",
            ".editorconfig"
    );

    static boolean isTextFile(Path p) {
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

    // snippets/ ships {{...}} markers consumed by add-recipe; init must not touch them.
    static boolean isUnderSnippets(Path root, Path p) {
        Path rel = root.relativize(p);
        return rel.getNameCount() > 0 && rel.getName(0).toString().equals("snippets");
    }

    static void substituteIn(Path root, Map<String, String> repl) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p) || !isTextFile(p)) {
                    continue;
                }
                if (isUnderSnippets(root, p)) {
                    continue;
                }
                String content = Files.readString(p, StandardCharsets.UTF_8);
                String updated = applySubstitutions(content, repl);
                if (!updated.equals(content)) {
                    Files.writeString(p, updated, StandardCharsets.UTF_8);
                }
            }
        }
    }

    // Negative lookbehind avoids matching GitHub Actions ${{ secrets.X }} in workflow files.
    static final Pattern RESIDUAL_PATTERN = Pattern.compile(
            "(?<!\\$)\\{\\{[a-zA-Z][a-zA-Z0-9]*\\}\\}|__ROOT_PACKAGE__");

    static List<String> findResiduals(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p) || !isTextFile(p)) {
                    continue;
                }
                if (isUnderSnippets(root, p)) {
                    continue;
                }
                String content = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = RESIDUAL_PATTERN.matcher(content);
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

    // Flat key:value pairs only; that is all the dropfile contains.
    static Map<String, String> readDropfile(Path file) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).strip();
            String val = trimmed.substring(colon + 1).strip();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = val.substring(1, val.length() - 1);
            }
            m.put(key, val);
        }
        return m;
    }

    static int runGradle(Path dir, List<String> tasks) throws IOException, InterruptedException {
        String cmd = System.getProperty("os.name").toLowerCase().startsWith("windows")
                ? "gradlew.bat"
                : "./gradlew";
        List<String> argv = new ArrayList<>();
        argv.add(cmd);
        argv.addAll(tasks);
        ProcessBuilder pb = new ProcessBuilder(argv)
                .directory(dir.toFile())
                .inheritIO();
        return pb.start().waitFor();
    }

    static String applySubstitutions(String s, Map<String, String> repl) {
        String out = s;
        for (Map.Entry<String, String> e : repl.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    static boolean isPascalCase(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        if (!Character.isUpperCase(s.charAt(0))) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    static String humanise(String pascal) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append(' ').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String kebabCase(String pascal) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
