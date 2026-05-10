

///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS info.picocli:picocli:4.7.7

package recipescaffold;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    static final String VERSION = "0.3.0";

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
        private Path projectDir;

        Path projectDir() {
            return projectDir;
        }
    }

    @Command(
            name = "init",
            description = "Scaffold a new OpenRewrite recipe project from the template.",
            mixinStandardHelpOptions = true
    )
    static class Init implements Callable<Integer> {

        @Option(names = "--group", required = true,
                description = "Maven group, e.g. io.github.acme.")
        private String group;

        @Option(names = "--artifact", required = true,
                description = "Maven artifact id, e.g. acme-rewrite-recipes.")
        private String artifact;

        @Option(names = {"--package", "--root-package"}, required = true,
                description = "Java root package, e.g. io.github.acme. Recipes live at <package>.recipes.")
        private String rootPackage;

        @Option(names = "--initial-version", defaultValue = "0.1",
                description = "First version of the scaffolded project. Default: ${DEFAULT-VALUE}.")
        private String initialVersion;

        @Option(names = "--recipe-name", required = true,
                description = "POM <name>, e.g. \"Acme Recipes\".")
        private String recipeName;

        @Option(names = "--recipe-description", required = true,
                description = "POM <description>.")
        private String recipeDescription;

        @Option(names = "--github-org", required = true,
                description = "GitHub org/user for SCM URLs.")
        private String githubOrg;

        @Option(names = "--github-repo", required = true,
                description = "GitHub repo name for SCM URLs.")
        private String githubRepo;

        @Option(names = "--author-id", required = true,
                description = "POM developer id.")
        private String authorId;

        @Option(names = "--author-name", required = true,
                description = "POM developer name.")
        private String authorName;

        @Option(names = "--author-email", required = true,
                description = "POM developer email.")
        private String authorEmail;

        @Option(names = "--java-target-main", defaultValue = "17",
                description = "release for compileJava. Default: ${DEFAULT-VALUE}.")
        private String javaTargetMain;

        @Option(names = "--java-target-tests", defaultValue = "25",
                description = "release for compileTestJava and toolchain. Default: ${DEFAULT-VALUE}.")
        private String javaTargetTests;

        @Option(names = "--rewrite-plugin-version", defaultValue = "7.32.1",
                description = "Used in docs snippets. Default: ${DEFAULT-VALUE}.")
        private String rewritePluginVersion;

        @Option(names = {"-d", "--directory"},
                description = "Output directory. Default: ./<artifact>.")
        private Path outputDir;

        @Option(names = "--template-dir",
                description = "Template source dir. Default: walks upward from cwd looking for template/build.gradle.kts.")
        private Path templateDir;

        @Option(names = "--force",
                description = "Overwrite an existing output directory.")
        private boolean force;

        @Option(names = "--verify",
                description = "Run ./gradlew check smokeTest after scaffolding.")
        private boolean verify;

        @Override
        public Integer call() throws Exception {
            Path templateRoot = resolveTemplateRoot();
            if (templateRoot == null) {
                return 2;
            }
            Path outputRoot = resolveOutputRoot();
            if (outputRoot == null) {
                return 2;
            }

            System.out.println("scaffolding " + outputRoot + " from " + templateRoot);
            copyTree(templateRoot, outputRoot);
            renamePackageMarkers(outputRoot, rootPackage.replace('.', '/'));
            substituteIn(outputRoot, buildReplacements());

            List<String> residuals = findResiduals(outputRoot);
            if (!residuals.isEmpty()) {
                System.err.println("FAIL: unsubstituted placeholder(s):");
                residuals.forEach(System.err::println);
                return 3;
            }

            writeDropfile(outputRoot);
            System.out.println("OK: scaffold complete at " + outputRoot);

            return verify ? runVerify(outputRoot) : 0;
        }

        private Path resolveTemplateRoot() {
            Path src = templateDir != null
                    ? templateDir.toAbsolutePath().normalize()
                    : findTemplateDir();
            if (Files.isDirectory(src) && Files.isRegularFile(src.resolve("build.gradle.kts"))) {
                return src;
            }
            System.err.println("not a template dir (no build.gradle.kts): " + src);
            System.err.println("pass --template-dir explicitly or run from a checkout of recipescaffold");
            return null;
        }

        private Path resolveOutputRoot() throws IOException {
            Path out = outputDir != null
                    ? outputDir.toAbsolutePath().normalize()
                    : Paths.get(artifact).toAbsolutePath().normalize();
            if (!Files.exists(out)) {
                return out;
            }
            if (!force) {
                System.err.println("output already exists (pass --force to overwrite): " + out);
                return null;
            }
            System.out.println("removing existing " + out);
            deleteRecursively(out);
            return out;
        }

        private int runVerify(Path outputRoot) throws IOException, InterruptedException {
            System.out.println("running ./gradlew check smokeTest in " + outputRoot);
            int exitCode = runGradle(outputRoot, List.of("check", "smokeTest"));
            if (exitCode != 0) {
                System.err.println("FAIL: ./gradlew check smokeTest exited " + exitCode);
                return exitCode;
            }
            System.out.println("OK: gates passed at " + outputRoot);
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
        private String name;

        @Option(names = "--type", defaultValue = "java",
                description = "Recipe skeleton kind: java | scanning | yaml | refaster. "
                        + "Default: ${DEFAULT-VALUE}.")
        private String type;

        @Option(names = "--display-name",
                description = "Override the @DisplayName text. Default: humanised --name.")
        private String displayName;

        @Option(names = "--description",
                description = "Override the @Description text. Default: a TODO sentence.")
        private String description;

        @Option(names = "--package",
                description = "Override the recipe's package. Default: <rootPackage>.recipes from "
                        + DROPFILE + ".")
        private String packageOverride;

        @Mixin
        private ProjectDirectoryMixin projectDirectory = new ProjectDirectoryMixin();

        @Option(names = "--no-tests",
                description = "Skip writing the test file. Default: write the test.")
        private boolean skipTests;

        @Option(names = "--force",
                description = "Overwrite existing recipe/test files.")
        private boolean force;

        @Option(names = "--test-style", defaultValue = "block",
                description = "Test scaffold style: block (multi-line text blocks) | method "
                        + "(one-line before/after pair, tighter for argument-level transforms). "
                        + "Default: ${DEFAULT-VALUE}. method-style is currently only available "
                        + "with --type java|scanning.")
        private String testStyle;

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

            Path projectRoot = resolveProjectRoot(projectDirectory.projectDir());
            if (projectRoot == null) {
                return 2;
            }
            Map<String, String> dropfile = readDropfile(projectRoot.resolve(DROPFILE));
            String dropfileRootPackage = dropfile.get("rootPackage");
            if (dropfileRootPackage == null || dropfileRootPackage.isEmpty()) {
                System.err.println("FAIL: " + DROPFILE + " is missing rootPackage.");
                return 3;
            }

            String pkg = packageOverride != null ? packageOverride : dropfileRootPackage + ".recipes";
            Path snippetsDir = projectRoot.resolve("snippets");
            if (!Files.isDirectory(snippetsDir)) {
                System.err.println("FAIL: snippets dir not found at " + snippetsDir);
                System.err.println("re-scaffold or copy template/snippets/ from recipescaffold.");
                return 3;
            }

            // YAML compositions go at <rootPackage>.<recipeName> per upstream convention; Java/scanning at <package>.<recipeName>.
            String recipeId = kind.mainInResources()
                    ? dropfileRootPackage + "." + name
                    : pkg + "." + name;

            Map<String, String> replacements = new LinkedHashMap<>();
            replacements.put("{{package}}", pkg);
            replacements.put("{{recipeName}}", name);
            replacements.put("{{recipeDisplayName}}", displayName != null ? displayName : humanise(name));
            replacements.put("{{recipeDescription}}",
                    description != null ? description : "TODO: describe what this recipe does.");
            replacements.put("{{recipeId}}", recipeId);
            replacements.put("{{recipeKebab}}", kebabCase(name));

            int recipeRc = writeRecipeFile(projectRoot, snippetsDir, kind, pkg, replacements);
            if (recipeRc != 0) {
                return recipeRc;
            }
            if (skipTests) {
                return 0;
            }
            return writeTestFile(projectRoot, snippetsDir, kind, pkg, replacements);
        }

        private int writeRecipeFile(Path root, Path snippets, RecipeKind kind,
                                    String pkg, Map<String, String> replacements) throws IOException {
            Path mainFile = mainOutputPath(root, kind, pkg, name);
            Files.createDirectories(mainFile.getParent());
            if (Files.exists(mainFile) && !force) {
                System.err.println("refusing to overwrite " + mainFile + " (pass --force).");
                return 2;
            }
            String mainBody = applySubstitutions(
                    Files.readString(snippets.resolve(kind.mainSnippet()), StandardCharsets.UTF_8),
                    replacements);
            Files.writeString(mainFile, mainBody, StandardCharsets.UTF_8);
            System.out.println("wrote " + mainFile);
            return 0;
        }

        private int writeTestFile(Path root, Path snippets, RecipeKind kind,
                                  String pkg, Map<String, String> replacements) throws IOException {
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
                    replacements);
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
        private ProjectDirectoryMixin projectDirectory = new ProjectDirectoryMixin();

        @Override
        public Integer call() throws Exception {
            Path projectRoot = resolveProjectRoot(projectDirectory.projectDir());
            if (projectRoot == null) {
                return 2;
            }
            // Tasks listed explicitly so all three run even when `check` is up-to-date.
            System.out.println("running ./gradlew check integrationTest smokeTest in " + projectRoot);
            int exitCode = runGradle(projectRoot, List.of("check", "integrationTest", "smokeTest"));
            if (exitCode != 0) {
                System.err.println("FAIL: gates exited " + exitCode);
                return exitCode;
            }
            System.out.println("OK: gates passed at " + projectRoot);
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
        private ProjectDirectoryMixin projectDirectory = new ProjectDirectoryMixin();

        @Option(names = "--template-dir",
                description = "Override upstream template source. Default: walks upward from cwd looking for template/build.gradle.kts.")
        private Path templateDir;

        @Option(names = "--dry-run",
                description = "Print what would change without modifying anything.")
        private boolean dryRun;

        @Override
        public Integer call() throws Exception {
            Path projectRoot = resolveProjectRoot(projectDirectory.projectDir());
            if (projectRoot == null) {
                return 2;
            }

            Path upstream = templateDir != null
                    ? templateDir.toAbsolutePath().normalize()
                    : findTemplateDir();
            Path upstreamSkills = upstream.resolve(".claude/skills");
            if (!Files.isDirectory(upstreamSkills)) {
                System.err.println("FAIL: no .claude/skills under " + upstream);
                System.err.println("pass --template-dir explicitly or run from a checkout of recipescaffold");
                return 3;
            }

            Path projectSkills = projectRoot.resolve(".claude/skills");
            Files.createDirectories(projectSkills);

            List<Path> upstreamSkillDirs;
            try (Stream<Path> entries = Files.list(upstreamSkills)) {
                upstreamSkillDirs = entries.filter(Files::isDirectory).toList();
            }
            for (Path skill : upstreamSkillDirs) {
                refreshSkill(skill, projectSkills.resolve(skill.getFileName()));
            }

            int refreshed = upstreamSkillDirs.size();
            System.out.println(dryRun
                    ? "OK: " + refreshed + " skill(s) would be refreshed (dry-run)"
                    : "OK: " + refreshed + " skill(s) refreshed at " + projectSkills);
            return 0;
        }

        private void refreshSkill(Path source, Path target) throws IOException {
            if (dryRun) {
                System.out.println("would refresh " + target);
                return;
            }
            deleteRecursively(target);
            copyDir(source, target);
            System.out.println("refreshed " + target);
        }
    }

    static Path findTemplateDir() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidateRoot = here; candidateRoot != null; candidateRoot = candidateRoot.getParent()) {
            Path candidate = candidateRoot.resolve("template").resolve("build.gradle.kts");
            if (Files.isRegularFile(candidate)) {
                return candidateRoot.resolve("template");
            }
        }
        return here.resolve("template");
    }

    static Path findProjectRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve(DROPFILE))) {
                return candidate;
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

    static void deleteRecursively(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(target)) {
            stream.sorted(Comparator.reverseOrder()).forEach(RecipeScaffold::deleteUnchecked);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void deleteUnchecked(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void copyDir(Path src, Path dst) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(srcPath -> copyEntry(srcPath, dst.resolve(src.relativize(srcPath))));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void copyEntry(Path source, Path destination) {
        try {
            if (Files.isDirectory(source)) {
                Files.createDirectories(destination);
                return;
            }
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination,
                    StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Like copyDir but skips SKIP_DIRS at the top level. SimpleFileVisitor needed because Files.walk
    // cannot prune subtrees mid-stream — without that, we would visit (and copy) skipped dirs' contents.
    static void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SkippingCopyVisitor(src, dst));
    }

    @SuppressWarnings("NullableProblems")
    private static final class SkippingCopyVisitor extends SimpleFileVisitor<Path> {
        private final Path src;
        private final Path dst;

        SkippingCopyVisitor(Path src, Path dst) {
            this.src = src;
            this.dst = dst;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path rel = src.relativize(dir);
            if (rel.getNameCount() > 0 && SKIP_DIRS.contains(rel.getName(0).toString())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            Files.createDirectories(dst.resolve(rel));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path rel = src.relativize(file);
            Path target = dst.resolve(rel);
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            return FileVisitResult.CONTINUE;
        }
    }

    static void renamePackageMarkers(Path out, String pkgPath) throws IOException {
        for (String parent : MARKER_PARENTS) {
            renameMarkerUnder(out.resolve(parent), pkgPath);
        }
    }

    private static void renameMarkerUnder(Path parentDir, String pkgPath) throws IOException {
        Path marker = parentDir.resolve(MARKER_DIR);
        if (!Files.isDirectory(parentDir) || !Files.isDirectory(marker)) {
            return;
        }
        Path target = parentDir.resolve(pkgPath);
        Files.createDirectories(target);
        try (Stream<Path> children = Files.list(marker)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                Files.move(child, target.resolve(marker.relativize(child)));
            }
        }
        Files.delete(marker);
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

    static boolean isTextFile(Path path) {
        String name = path.getFileName().toString();
        if (TEXT_NAMES.contains(name)) {
            return true;
        }
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 && TEXT_EXTS.contains(name.substring(dotIndex));
    }

    // snippets/ ships {{...}} markers consumed by add-recipe; init must not touch them.
    static boolean isUnderSnippets(Path root, Path path) {
        Path rel = root.relativize(path);
        return rel.getNameCount() > 0 && rel.getName(0).toString().equals("snippets");
    }

    static void substituteIn(Path root, Map<String, String> replacements) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(RecipeScaffold::isTextFile)
                    .filter(path -> !isUnderSnippets(root, path))
                    .forEach(path -> applySubstitutionsInPlace(path, replacements));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void applySubstitutionsInPlace(Path path, Map<String, String> replacements) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String updated = applySubstitutions(content, replacements);
            if (!updated.equals(content)) {
                Files.writeString(path, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Negative lookbehind avoids matching GitHub Actions ${{ secrets.X }} in workflow files.
    static final Pattern RESIDUAL_PATTERN = Pattern.compile(
            "(?<!\\$)\\{\\{[a-zA-Z][a-zA-Z0-9]*}}|__ROOT_PACKAGE__");

    static List<String> findResiduals(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(RecipeScaffold::isTextFile)
                    .filter(path -> !isUnderSnippets(root, path))
                    .flatMap(RecipeScaffold::residualsIn)
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Stream<String> residualsIn(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> hits = new ArrayList<>();
        Matcher matcher = RESIDUAL_PATTERN.matcher(content);
        int line = 1;
        int scanned = 0;
        while (matcher.find()) {
            for (int i = scanned; i < matcher.start(); i++) {
                if (content.charAt(i) == '\n') {
                    line++;
                }
            }
            scanned = matcher.start();
            hits.add(path + ":" + line + ": " + matcher.group());
        }
        return hits.stream();
    }

    // Flat key:value pairs only; that is all the dropfile contains.
    static Map<String, String> readDropfile(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(RecipeScaffold::parseDropfileEntry)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> newValue, LinkedHashMap::new));
        }
    }

    private static Map.Entry<String, String> parseDropfileEntry(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex <= 0) {
            return null;
        }
        String key = line.substring(0, colonIndex).strip();
        String value = line.substring(colonIndex + 1).strip();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return Map.entry(key, value);
    }

    static int runGradle(Path workingDir, List<String> tasks) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(wrapperScript());
        command.addAll(tasks);
        return new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .inheritIO()
                .start()
                .waitFor();
    }

    static String wrapperScript() {
        return System.getProperty("os.name").toLowerCase().startsWith("windows")
                ? "gradlew.bat"
                : "./gradlew";
    }

    static String applySubstitutions(String text, Map<String, String> replacements) {
        String result = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    static boolean isPascalCase(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        if (!Character.isUpperCase(identifier.charAt(0))) {
            return false;
        }
        return identifier.chars().allMatch(Character::isLetterOrDigit);
    }

    static String humanise(String pascal) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char ch = pascal.charAt(i);
            if (i > 0 && Character.isUpperCase(ch)) {
                result.append(' ').append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    static String kebabCase(String pascal) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char ch = pascal.charAt(i);
            if (i > 0 && Character.isUpperCase(ch)) {
                result.append('-').append(Character.toLowerCase(ch));
            } else {
                result.append(Character.toLowerCase(ch));
            }
        }
        return result.toString();
    }
}
