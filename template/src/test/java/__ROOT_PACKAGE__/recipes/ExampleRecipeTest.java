package __ROOT_PACKAGE__.recipes;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ExampleRecipeTest implements RewriteTest {

    @Override
    public void defaults(final RecipeSpec spec) {
        spec.recipe(new ExampleRecipe());
    }

    @Test
    void noOpRecipeLeavesSourceUnchanged() {
        rewriteRun(java("""
                package com.example;

                public class Example {
                    public String greet(String name) {
                        return "Hello, " + name;
                    }
                }
                """));
    }
}
