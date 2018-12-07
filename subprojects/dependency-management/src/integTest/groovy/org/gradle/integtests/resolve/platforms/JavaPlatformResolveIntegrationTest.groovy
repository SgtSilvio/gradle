/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.platforms

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class JavaPlatformResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    ResolveTestFixture resolve

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: 'java-library'
            
            allprojects {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                group = 'org.test'
                version = '1.9'
            }
        """
    }

    def "can get recommendations from a platform subproject"() {
        def module = mavenHttpRepo.module("org", "foo", "1.1").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
            }
        """)

        buildFile << """
            dependencies {
                api platform(project(":platform"))
                api "org:foo"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                project(":platform", "org.test:platform:1.9") {
                    variant("api", ['org.gradle.usage': 'java-api', 'org.gradle.component.category': 'platform'])
                    constraint("org:foo:1.1")
                    noArtifacts()
                }
                edge('org:foo', 'org:foo:1.1') {
                    byConstraint()
                }
            }
        }
    }

    def "can get different recommendations from a platform runtime subproject"() {
        def module1 = mavenHttpRepo.module("org", "foo", "1.1").publish()
        def module2 = mavenHttpRepo.module("org", "foo", "1.2").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
                runtime "org:foo:1.2"
            }
        """)

        buildFile << """
            dependencies {
                api platform(project(":platform"))
                api "org:foo"
            }
        """
        checkConfiguration("runtimeClasspath")

        when:
        module2.pom.expectGet()
        module2.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                project(":platform", "org.test:platform:1.9") {
                    variant("runtime", ['org.gradle.usage': 'java-runtime', 'org.gradle.component.category': 'platform'])
                    constraint("org:foo:1.1", "org:foo:1.2") // this is just because of the dumb test
                    constraint("org:foo:1.2")
                    noArtifacts()
                }
                edge('org:foo', 'org:foo:1.2') {
                    configuration = "runtime"
                    byConstraint()
                }
            }
        }
    }

    def "reasonable behavior when using a regular project dependency instead of a platform dependency"() {
        def module = mavenHttpRepo.module("org", "foo", "1.1").publish()

        given:
        platformModule("""
            constraints {
                api "org:foo:1.1"
            }
        """)

        buildFile << """
            dependencies {
                api project(":platform")
                api "org:foo"
            }
        """
        checkConfiguration("compileClasspath")

        when:
        module.pom.expectGet()
        module.artifact.expectGet()

        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.9") {
                project(":platform", "org.test:platform:1.9") {
                    variant("api", ['org.gradle.usage': 'java-api', 'org.gradle.component.category': 'platform'])
                    constraint("org:foo:1.1")
                    noArtifacts()
                }
                edge('org:foo', 'org:foo:1.1') {
                    byConstraint()
                }
            }
        }
    }


    private void checkConfiguration(String configuration) {
        resolve = new ResolveTestFixture(buildFile, configuration)
        resolve.expectDefaultConfiguration("compile")
        resolve.prepare()
    }

    private void platformModule(String dependencies) {
        settingsFile << """
            include "platform"
        """
        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }
            
            dependencies {
                $dependencies
            }
        """
    }
}
