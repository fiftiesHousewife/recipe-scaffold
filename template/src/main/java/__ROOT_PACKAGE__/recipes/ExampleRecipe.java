package __ROOT_PACKAGE__.recipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.NullMarked;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.time.Duration;
import java.util.Set;

/**
 * Replace this with your first recipe. The shipped implementation is a
 * deliberate no-op so the scaffolded project's {@code ./gradlew check} and
 * {@code ./gradlew smokeTest} both pass before you've written any logic.
 *
 * <p>See {@code .claude/skills/new-recipe/SKILL.md} for the canonical visitor
 * structure, {@code MethodMatcher} usage, and YAML composition patterns.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@NullMarked
public class ExampleRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Example recipe (no-op)";
    }

    @Override
    public String getDescription() {
        return "Placeholder recipe shipped by the template scaffold. Replace with your own.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("example");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return TreeVisitor.noop();
    }
}
