/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.JavaVersion

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsVariantsReport

class RequestedVariantsReportTaskIntegrationTest extends AbstractIntegrationSpec implements InspectsVariantsReport {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "if no configurations present, requested variants task produces empty report"() {
        expect:
        succeeds ':requestedVariants'
        outputContains('There are no resolvable configurations on project myLib')
    }

    def "if only custom configuration present, requested variants task reports it"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false
            }
        """

        when:
        succeeds ':requestedVariants'

        then:
        outputContains """> Task :requestedVariants
--------------------------------------------------
Configuration custom
--------------------------------------------------
Description = My custom configuration

Capabilities
    - :myLib:unspecified (default capability)
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    def "if only custom configuration present with attributes, requested variants task reports it and them"() {
        given:
        buildFile << """
            configurations.create("custom") {
                description = "My custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }
        """

        when:
        succeeds ':requestedVariants'

        then:
        outputContains """> Task :requestedVariants
--------------------------------------------------
Configuration custom
--------------------------------------------------
Description = My custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    def "Multiple custom configurations present with attributes, requested variants task reports them all"() {
        given:
        buildFile << """
            configurations.create("someConf") {
                description = "My first custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EXTERNAL))
                }
            }

            configurations.create("otherConf") {
                description = "My second custom configuration"
                canBeResolved = true
                canBeConsumed = false

                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.DOCUMENTATION));
                }
            }
        """

        when:
        succeeds ':requestedVariants'

        then:
        outputContains """> Task :requestedVariants
--------------------------------------------------
Configuration otherConf
--------------------------------------------------
Description = My second custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category = documentation

--------------------------------------------------
Configuration someConf
--------------------------------------------------
Description = My first custom configuration

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.dependency.bundling = external
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    def "if only custom legacy configuration present, requested variants task does not report it"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        expect:
        succeeds ':requestedVariants'
        outputContains('There are no resolvable configurations on project myLib')
    }

    def "if only custom legacy configuration present, requested variants task reports it if -all flag is set"() {
        given:
        buildFile << """
            configurations.create("legacy") {
                description = "My custom legacy configuration"
                canBeResolved = true
                canBeConsumed = true
            }
        """

        when:
        executer.expectDeprecationWarning('(l) Legacy or deprecated configuration. Those are variants created for backwards compatibility which are both resolvable and consumable.')
        run ':requestedVariants', '--all'

        then:
        outputContains """> Task :requestedVariants
--------------------------------------------------
Configuration legacy (l)
--------------------------------------------------
Description = My custom legacy configuration

Capabilities
    - :myLib:unspecified (default capability)
"""

        and:
        hasLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }

    def "reports requested variants of a Java Library with module dependencies"() {
        given:
        buildFile << """
            plugins { id 'java-library' }

            ${mavenCentralRepository()}

            dependencies {
                api 'org.apache.commons:commons-lang3:3.5'
                implementation 'org.apache.commons:commons-compress:1.19'
            }
        """

        when:
        succeeds ':requestedVariants'

        then:
        outputContains """> Task :requestedVariants
--------------------------------------------------
Configuration annotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'main'.

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration compileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'main'.

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api

--------------------------------------------------
Configuration runtimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'main'.

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testAnnotationProcessor
--------------------------------------------------
Description = Annotation processors and their dependencies for source set 'test'.

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime

--------------------------------------------------
Configuration testCompileClasspath
--------------------------------------------------
Description = Compile classpath for source set 'test'.

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = classes
    - org.gradle.usage               = java-api

--------------------------------------------------
Configuration testRuntimeClasspath
--------------------------------------------------
Description = Runtime classpath of source set 'test'.

Capabilities
    - :myLib:unspecified (default capability)
Attributes
    - org.gradle.category            = library
    - org.gradle.dependency.bundling = external
    - org.gradle.jvm.environment     = standard-jvm
    - org.gradle.jvm.version         = ${JavaVersion.current().majorVersion}
    - org.gradle.libraryelements     = jar
    - org.gradle.usage               = java-runtime
"""

        and:
        doesNotHaveLegacyVariantsLegend()
        doesNotHaveIncubatingVariantsLegend()
    }
}
