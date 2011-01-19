/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.integtests

import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import junit.framework.AssertionFailedError

class IdeaIntegrationTest {
    @Rule
    public final GradleDistribution dist = new GradleDistribution()
    @Rule
    public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    public void canCreateAndDeleteMetaData() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
        assertHasExpectedContents('root.iws')
        assertHasExpectedContents('root.iml')
        assertHasExpectedContents('api/api.iml')
        assertHasExpectedContents('webservice/webservice.iml')

        executer.withTasks('cleanIdea').run()
    }

    @Test
    public void worksWithAnEmptyProject() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
        assertHasExpectedContents('root.iml')
    }

    @Test
    public void worksWithASubProjectThatDoesNotHaveTheIdeaPluginApplied() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.ipr')
    }

    @Test
    public void worksWithNonStandardLayout() {
        executer.inDirectory(dist.testDir.file('root')).withTasks('idea').run()

        assertHasExpectedContents('root/root.ipr')
        assertHasExpectedContents('root/root.iml')
        assertHasExpectedContents('top-level.iml')
    }

    @Test
    public void overwritesExistingDependencies() {
        executer.withTasks('idea').run()

        assertHasExpectedContents('root.iml')
    }

    @Test
    public void configuresOutputDirsAccordingToIdeaDefaults() {
        def settingsFile = dist.file("settings.gradle")
        settingsFile << "rootProject.name = 'root'"
        def buildFile = dist.file("build.gradle")
        buildFile << "apply plugin: 'java'; apply plugin: 'idea'"

        executer.usingSettingsFile(settingsFile).usingBuildScript(buildFile).withTasks("idea").run()

        def moduleFile = dist.file("root.iml").assertExists()
        println moduleFile.text
        def module = new XmlSlurper().parse(moduleFile)
        def outputUrl = module.component.output[0].@url
        def testOutputUrl = module.component."output-test"[0].@url

        assert outputUrl.text() == 'file://$MODULE_DIR$/out/production/root'
        assert testOutputUrl.text() == 'file://$MODULE_DIR$/out/test/root'
    }

    def assertHasExpectedContents(String path) {
        TestFile file = dist.testDir.file(path).assertIsFile()
        TestFile expectedFile = dist.testDir.file("expectedFiles/${path}.xml").assertIsFile()

        def cache = dist.userHomeDir.file("cache")
        def cachePath = cache.absolutePath.replace(File.separator, '/')
        def expectedXml = expectedFile.text.replace('@CACHE_DIR@', cachePath)

        Diff diff = new Diff(expectedXml, file.text)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        try {
            XMLAssert.assertXMLEqual(diff, true)
        } catch (AssertionFailedError e) {
            throw new AssertionFailedError("generated file '$path' does not contain the expected contents: ${e.message}.\nExpected:\n${expectedXml}\nActual:\n${file.text}").initCause(e)
        }
    }
}