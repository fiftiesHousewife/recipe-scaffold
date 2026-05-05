package recipescaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Pure-function unit tests for the helpers extracted to top-level.
// The end-to-end ScaffoldHarnessTest covers the I/O-heavy paths via
// GradleRunner; this class focuses on the deterministic in-memory
// helpers so we get fast feedback when their logic regresses.
class RecipeScaffoldUnitTest {

    @ParameterizedTest
    @CsvSource({
            "Foo, foo",
            "FooBar, foo-bar",
            "RemoveStaleSuppression, remove-stale-suppression",
            "ABC, a-b-c",
            "X, x"
    })
    void kebabCase_lowercasesAndHyphenatesEveryUpperRunBoundary(String input, String expected) {
        assertThat(RecipeScaffold.kebabCase(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Foo, Foo",
            "FooBar, Foo bar",
            "RemoveStaleSuppression, Remove stale suppression"
    })
    void humanise_inserts_space_before_internal_uppers_lowercase_only_those(String input, String expected) {
        assertThat(RecipeScaffold.humanise(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Foo", "FooBar", "F", "F1", "Foo123Bar"})
    void isPascalCase_acceptsValidIdentifiers(String name) {
        assertThat(RecipeScaffold.isPascalCase(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "fooBar", "1Foo", "Foo Bar", "Foo-Bar", "Foo_Bar", ""})
    void isPascalCase_rejectsInvalidIdentifiers(String name) {
        assertThat(RecipeScaffold.isPascalCase(name)).isFalse();
    }

    @Test
    void isPascalCase_rejectsNull() {
        assertThat(RecipeScaffold.isPascalCase(null)).isFalse();
    }

    @Test
    void applySubstitutions_replacesEveryKey() {
        Map<String, String> repl = new LinkedHashMap<>();
        repl.put("{{a}}", "X");
        repl.put("{{b}}", "Y");
        assertThat(RecipeScaffold.applySubstitutions("[{{a}}][{{b}}][{{a}}]", repl))
                .isEqualTo("[X][Y][X]");
    }

    @Test
    void applySubstitutions_preservesUnknownKeys() {
        Map<String, String> repl = Map.of("{{a}}", "X");
        assertThat(RecipeScaffold.applySubstitutions("{{a}} {{b}}", repl))
                .isEqualTo("X {{b}}");
    }

    @Test
    void applySubstitutions_appliesInOrder() {
        // Order matters when one replacement is a prefix of another's value.
        Map<String, String> repl = new LinkedHashMap<>();
        repl.put("X", "Y");
        repl.put("Y", "Z");
        assertThat(RecipeScaffold.applySubstitutions("X", repl)).isEqualTo("Z");
    }

    @Test
    void initBuildReplacements_includesAllInitTimePlaceholdersAndMarker() throws Exception {
        RecipeScaffold.Init init = new RecipeScaffold.Init();
        setField(init, "group", "io.github.acme");
        setField(init, "artifact", "acme-rewrite-recipes");
        setField(init, "rootPackage", "io.github.acme");
        setField(init, "initialVersion", "0.1");
        setField(init, "recipeName", "Acme Recipes");
        setField(init, "recipeDescription", "Cleanup recipes");
        setField(init, "githubOrg", "acme");
        setField(init, "githubRepo", "acme-rewrite-recipes");
        setField(init, "authorId", "acmebot");
        setField(init, "authorName", "Acme Bot");
        setField(init, "authorEmail", "bot@acme.example");
        setField(init, "javaTargetMain", "17");
        setField(init, "javaTargetTests", "25");
        setField(init, "rewritePluginVersion", "7.30.0");

        Map<String, String> repl = init.buildReplacements();

        assertThat(repl)
                .containsEntry("{{group}}", "io.github.acme")
                .containsEntry("{{artifact}}", "acme-rewrite-recipes")
                .containsEntry("{{rootPackage}}", "io.github.acme")
                .containsEntry("{{initialVersion}}", "0.1")
                .containsEntry("{{recipeName}}", "Acme Recipes")
                .containsEntry("{{recipeDescription}}", "Cleanup recipes")
                .containsEntry("{{githubOrg}}", "acme")
                .containsEntry("{{githubRepo}}", "acme-rewrite-recipes")
                .containsEntry("{{authorId}}", "acmebot")
                .containsEntry("{{authorName}}", "Acme Bot")
                .containsEntry("{{authorEmail}}", "bot@acme.example")
                .containsEntry("{{javaTargetMain}}", "17")
                .containsEntry("{{javaTargetTests}}", "25")
                .containsEntry("{{rewritePluginVersion}}", "7.30.0")
                .containsEntry(RecipeScaffold.MARKER_DIR, "io.github.acme");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
