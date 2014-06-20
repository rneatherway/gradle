/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.compile;

import org.gradle.api.AntBuilder;
import org.gradle.api.Incubating;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.IncrementalJavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.incremental.cache.CompilationCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.DefaultLocalCompilationCaches;
import org.gradle.api.internal.tasks.compile.incremental.cache.LocalCompilationCaches;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.Factory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.runtime.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.runtime.jvm.toolchain.JavaToolChain;
import org.gradle.util.SingleMessageLogger;

import javax.inject.Inject;
import java.io.File;

/**
 * Compiles Java source files.
 *
 * <pre autoTested=''>
 *     apply plugin: 'java'
 *     compileJava {
 *         //enable compilation in a separate daemon process
 *         options.fork = true
 *     }
 * </pre>
 */
public class JavaCompile extends AbstractCompile {
    private File dependencyCacheDir;
    private final CompileOptions compileOptions = new CompileOptions();
    private JavaToolChain toolChain;

    /**
     * Returns the tool chain that will be used to compile the Java source.
     *
     * @return The tool chain.
     */
    @Incubating
    public JavaToolChain getToolChain() {
        return toolChain;
    }

    /**
     * Sets the tool chain that should be used to compile the Java source.
     *
     * @param toolChain The tool chain.
     */
    @Incubating
    public void setToolChain(JavaToolChain toolChain) {
        this.toolChain = toolChain;
    }

    @TaskAction
    protected void compile(IncrementalTaskInputs inputs) {
        if (!compileOptions.isIncremental()) {
            compile();
            return;
        }

        SingleMessageLogger.incubatingFeatureUsed("Incremental java compilation");

        DefaultJavaCompileSpec spec = createSpec();
        CompilationCaches caches = getServices().get(CompilationCaches.class); //TODO SF inject lazily
        LocalCompilationCaches localCaches = new DefaultLocalCompilationCaches(getServices().get(CacheRepository.class), this); //TODO SF inject lazily
        IncrementalJavaCompilerFactory factory = new IncrementalJavaCompilerFactory(
                getProject(), getPath(), createCompiler(spec), source, caches, localCaches);
        Compiler<JavaCompileSpec> compiler = factory.createCompiler(inputs);
        performCompilation(spec, compiler);
    }

    protected void compile() {
        DefaultJavaCompileSpec spec = createSpec();
        performCompilation(spec, createCompiler(spec));
    }

    @Inject
    protected Factory<AntBuilder> getAntBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    private CleaningJavaCompiler createCompiler(JavaCompileSpec spec) {
        Compiler<JavaCompileSpec> javaCompiler = ((JavaToolChainInternal) getToolChain()).newCompiler(spec);
        return new CleaningJavaCompiler(javaCompiler, getAntBuilderFactory(), getOutputs());
    }

    private void performCompilation(JavaCompileSpec spec, Compiler<JavaCompileSpec> compiler) {
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    private DefaultJavaCompileSpec createSpec() {
        DefaultJavaCompileSpec spec = new DefaultJavaCompileSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setWorkingDir(getProject().getProjectDir());
        spec.setTempDir(getTemporaryDir());
        spec.setClasspath(getClasspath());
        spec.setDependencyCacheDir(getDependencyCacheDir());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(compileOptions);
        return spec;
    }

    @OutputDirectory
    public File getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }

    /**
     * Returns the compilation options.
     *
     * @return The compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }
}
