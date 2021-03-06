/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java

import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIncrementalBuildIntegrationTest
import org.gradle.language.fixtures.TestJavaComponent

class JavaLanguageIncrementalBuildIntegrationTest extends AbstractJvmLanguageIncrementalBuildIntegrationTest {
    TestJvmComponent testComponent = new TestJavaComponent()

    def "rebuilds jar when input property changed"() {
        given:
        run "mainJar"

        when:
        buildFile << """
    tasks.withType(JavaCompile) {
        options.debug = false
    }
"""
        run "mainJar"

        then:
        executedAndNotSkipped ":compileMainJarMainJava", ":createMainJar", ":mainJar"
    }

    def "task outcome is up to date when no recompilation necessary"() {
        given:
        buildFile.text = ""
        multiProjectBuild('incremental', ['library', 'app']) {
            buildFile << """
                subprojects {
                    apply plugin: 'jvm-component'
                    apply plugin: '${testComponent.languageName}-lang'
                    
                    repositories {
                        mavenCentral()
                    }
                
                    tasks.withType(org.gradle.api.tasks.compile.AbstractCompile) {
                        it.options.incremental = true
                    }
                }
                project(':library') {
                    model {
                        components {
                            main(JvmLibrarySpec)
                        }
                    }                    
                }
                
                project(':app') {
                    model {
                        components {
                            main(JvmLibrarySpec) {
                                dependencies {
                                    project(':library')
                                }
                            }
                        }
                    }
                }
            """.stripIndent()
        }
        mainCompileTaskName = ":app${mainCompileTaskName}"
        sourceFiles = testComponent.writeSources(file("app/src/main"))
        resourceFiles = testComponent.writeResources(file("app/src/main/resources"))

        when:
        succeeds mainCompileTaskName

        then:
        executedAndNotSkipped mainCompileTaskName

        when:
        file('library/src/main/java/Unused.java') << 'public class Unused {}'

        and:
        executer.withArgument('-i')
        succeeds mainCompileTaskName

        then:
        result.output.contains "None of the classes needs to be compiled!"
        result.output.contains "${mainCompileTaskName} UP-TO-DATE"
    }
}
