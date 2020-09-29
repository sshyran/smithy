/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.codegen.core.writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolContainer;
import software.amazon.smithy.codegen.core.SymbolDependency;
import software.amazon.smithy.codegen.core.SymbolDependencyContainer;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.utils.CodeWriter;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A {@code CodeGenWriter} is a specialized {@link CodeWriter} that makes it
 * easier to implement code generation that utilizes {@link Symbol}s and
 * {@link SymbolDependency} values.
 *
 * <p>A {@code CodegenWriter} is expected to be subclassed, and the
 * subclass is expected to implement language-specific functionality
 * like writing documentation comments, tracking "imports", and adding
 * any other kinds of helpful functionality for generating source code
 * for a programming language.
 *
 * <p>The following example shows how a subclass of {@code CodegenWriter}
 * should be created. CodegenWriters are expected to define a recursive
 * type signature (notice that {@code MyWriter} is a generic parametric
 * type in its own type definition).
 *
 * <pre>{@code
 * public final class MyWriter extends CodegenWriter<MyWriter, MyImportContainer> {
 *     public MyWriter(String namespace) {
 *         super(new MyDocumentationWriter(), new MyImportContainer(namespace));
 *     }
 *
 *     \@Override
 *     public String toString() {
 *         return getImportContainer().toString() + "\n\n" + super.toString();
 *     }
 *
 *     public MyWriter someCustomMethod() {
 *         // You can implement custom methods that are specific to whatever
 *         // language you're implementing a generator for.
 *         return this;
 *     }
 * }
 * }</pre>
 *
 * @param <T> The concrete type, used to provide a fluent interface.
 * @param <U> The import container used by the writer to manage imports.
 */
@SmithyUnstableApi
public class CodegenWriter<T extends CodegenWriter<T, U>, U extends ImportContainer>
        extends CodeWriter implements SymbolDependencyContainer {

    private static final Logger LOGGER = Logger.getLogger(CodegenWriter.class.getName());

    private final List<SymbolDependency> dependencies = new ArrayList<>();
    private final DocumentationWriter<T> documentationWriter;
    private final U importContainer;

    /**
     * @param documentationWriter Writes out documentation emitted by a {@code Runnable}.
     * @param importContainer Container used to persist and filter imports based on package names.
     */
    public CodegenWriter(DocumentationWriter<T> documentationWriter, U importContainer) {
        this.documentationWriter = documentationWriter;
        this.importContainer = importContainer;
    }

    /**
     * Gets the import container associated with the writer.
     *
     * <p>The {@link #toString()} method of the {@code CodegenWriter} should
     * be overridden so that it includes the import container's contents in
     * the output as appropriate.
     *
     * @return Returns the import container.
     */
    public final U getImportContainer() {
        return importContainer;
    }

    @Override
    public final List<SymbolDependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    /**
     * Adds one or more dependencies to the generated code (represented as
     * a {@link SymbolDependency}).
     *
     * <p>Tracking dependencies on a {@code CodegenWriter} allows dependencies
     * to be automatically aggregated and collected in order to generate
     * configuration files for dependency management tools (e.g., npm,
     * maven, etc).
     *
     * @param dependencies Dependency to add.
     * @return Returns the writer.
     */
    @SuppressWarnings("unchecked")
    public final T addDependency(SymbolDependencyContainer dependencies) {
        List<SymbolDependency> values = dependencies.getDependencies();
        LOGGER.finest(() -> String.format("Adding dependencies from %s: %s", dependencies, values));
        this.dependencies.addAll(values);
        return (T) this;
    }

    /**
     * Imports one or more USE symbols using the name of the symbol
     * (e.g., {@link SymbolReference.ContextOption#USE} references).
     *
     * <p>USE references are only necessary when referring to a symbol, not
     * <em>declaring</em> the symbol. For example, when referring to a
     * {@code List<Foo>}, the USE references would be both the {@code List}
     * type and {@code Foo} type.
     *
     * <p>This method may be overridden as needed.
     *
     * @param container Symbols to add.
     * @return Returns the writer.
     */
    @SuppressWarnings("unchecked")
    public T addUseImports(SymbolContainer container) {
        for (Symbol symbol : container.getSymbols()) {
            addImport(symbol, symbol.getName(), SymbolReference.ContextOption.USE);
        }
        return (T) this;
    }

    /**
     * Imports a USE symbols possibly using an alias of the symbol
     * (e.g., {@link SymbolReference.ContextOption#USE} references).
     *
     * <p>This method may be overridden as needed.
     *
     * @param symbolReference Symbol reference to import.
     * @return Returns the writer.
     * @see #addUseImports(SymbolContainer)
     */
    public T addUseImports(SymbolReference symbolReference) {
        return addImport(symbolReference.getSymbol(), symbolReference.getAlias(), SymbolReference.ContextOption.USE);
    }

    /**
     * Imports a symbol (if necessary) using a specific alias and list of
     * context options.
     *
     * <p>This method automatically adds any dependencies of the {@code symbol}
     * to the writer, calls {@link ImportContainer#importSymbol}, and
     * automatically calls {@link #addImportReferences} for the provided
     * {@code symbol}.
     *
     * <p>When called with no {@code options}, both {@code USE} and
     * {@code DECLARE} symbols are imported from any references the
     * {@code Symbol} might contain.
     *
     * @param symbol Symbol to optionally import.
     * @param alias The alias to refer to the symbol by.
     * @param options The list of context options (e.g., is it a USE or DECLARE symbol).
     * @return Returns the writer.
     */
    @SuppressWarnings("unchecked")
    public final T addImport(Symbol symbol, String alias, SymbolReference.ContextOption... options) {
        LOGGER.finest(() -> String.format("Adding import %s as `%s` (%s)",
                                          symbol.getNamespace(), alias, Arrays.toString(options)));

        // Always add the dependencies of the symbol.
        dependencies.addAll(symbol.getDependencies());

        // Only add an import for the symbol if the symbol is external to the
        // current "namespace" (where "namespace" can mean whatever is need to
        // mean for each target language).
        importContainer.importSymbol(symbol, alias);

        // Even if the symbol is in the same namespace as the current namespace,
        // the symbol references of the given symbol always need to be imported
        // because the assumption is that the symbol is being USED or DECLARED
        // and is required ot refer to other symbols as part of the definition.
        addImportReferences(symbol, options);

        return (T) this;
    }

    /**
     * Adds any imports to the writer by getting all of the references from the
     * symbol that contain one or more of the given {@code options}.
     *
     * @param symbol Symbol to import the references of.
     * @param options The options that must appear on the reference.
     */
    final void addImportReferences(Symbol symbol, SymbolReference.ContextOption... options) {
        for (SymbolReference reference : symbol.getReferences()) {
            if (options.length == 0) {
                addImport(reference.getSymbol(), reference.getAlias(), options);
            } else {
                for (SymbolReference.ContextOption option : options) {
                    if (reference.hasOption(option)) {
                        addImport(reference.getSymbol(), reference.getAlias(), options);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Writes documentation comments.
     *
     * <p>This method is responsible for setting up the writer to begin
     * writing documentation comments. This includes writing any necessary
     * opening tokens (e.g., "/*"), adding tokens to the beginning of lines
     * (e.g., "*"), sanitizing documentation strings, and writing any
     * tokens necessary to close documentation comments (e.g., "*\/").
     *
     * <p>This method <em>does not</em> automatically escape the expression
     * start character ("$" by default). Write calls made by the Runnable
     * should either use {@link CodeWriter#writeWithNoFormatting} or escape
     * the expression start character manually.
     *
     * <p>This method may be overridden as needed.
     *
     * @param runnable Runnable that handles actually writing docs with the writer.
     * @return Returns the writer.
     */
    @SuppressWarnings("unchecked")
    public final T writeDocs(Runnable runnable) {
        pushState();
        documentationWriter.writeDocs((T) this, runnable);
        popState();
        return (T) this;
    }

    /**
     * Writes documentation comments from a string.
     *
     * @param docs Documentation to write.
     * @return Returns the writer.
     */
    @SuppressWarnings("unchecked")
    public final T writeDocs(String docs) {
        writeDocs(() -> writeWithNoFormatting(docs));
        return (T) this;
    }
}
