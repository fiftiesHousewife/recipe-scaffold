package __ROOT_PACKAGE__.smoketest;

import java.util.List;

/**
 * A single Java source file dropped into the throwaway project before the
 * recipe runs. The recipe transforms the body; we only verify that the
 * post-rewrite project still compiles.
 *
 * <p>{@code prerequisiteImplementationDeps} declares any classpath deps the
 * fixture's <em>original</em> source needs to compile (e.g. a fixture that
 * imports {@code LogManager} would need {@code log4j-api} on the smoke
 * project's {@code implementation} configuration before the rewrite runs —
 * otherwise OpenRewrite can't resolve the types and the recipe matches
 * nothing).
 *
 * <p><b>EDIT THIS FILE for your recipe.</b> Replace {@code EXAMPLE} with one
 * fixture per input pattern your recipe transforms. The shipped fixture is a
 * minimal no-op so a freshly scaffolded project's smokeTest passes.
 */
record Fixture(String relativePath, String body, List<String> prerequisiteImplementationDeps) {

    static final Fixture EXAMPLE = new Fixture(
            "src/main/java/com/example/Example.java",
            """
                    package com.example;

                    public class Example {
                        public String greet(String name) {
                            return "Hello, " + name;
                        }
                    }
                    """,
            List.of());
}
