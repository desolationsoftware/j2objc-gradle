/*
 * Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.j2objccontrib.j2objcgradle.tasks

import com.github.j2objccontrib.j2objcgradle.J2objcConfig
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Uses 'lipo' binary to combine multiple architecture flavors of a library into a
 * single 'fat' library.
 */
@CompileStatic
class PackLibrariesTask extends DefaultTask {

    // Generated ObjC binaries
    @InputFiles
    ConfigurableFileCollection getLibrariesFiles() {
        String staticLibraryPath = "${project.buildDir}/libs/${project.name}-j2objc/static"
        String lowerCaseBuildType = buildType.toLowerCase()
        return project.files(getActiveArchs().collect { String arch ->
            "$staticLibraryPath/$arch/$lowerCaseBuildType/lib${project.name}-j2objc.a"
        })
    }

    // Debug or Release
    @Input
    String buildType

    @Input
    List<String> getActiveArchs() { return J2objcConfig.from(project).activeArchs }


    @OutputDirectory
    File getOutputLibDirFile() {
        String lowerCaseBuildType = buildType.toLowerCase()
        return project.file("${project.buildDir}/packedLibs/${project.name}-j2objc/static/ios/$lowerCaseBuildType")
    }


    @TaskAction
    void packLibraries() {
        Utils.requireMacOSX('j2objcPackLibraries task')
        assert buildType in ['Debug', 'Release']

        Utils.projectDelete(project, getOutputLibDirFile())
        getOutputLibDirFile().mkdirs()

        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()

        // Source files arguments
        List<String> srcFilesArgs = []
        int srcFilesArgsCharCount = 0
        for (File file in getLibrariesFiles()) {
            String filePath = file.absolutePath
            srcFilesArgs.add(filePath)
            srcFilesArgsCharCount += filePath.length() + 1
        }

        // Handle command line that's too long
        // Allow up to 2,000 characters for command line excluding src files
        // http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javac.html#commandlineargfile
        if (srcFilesArgsCharCount + 2000 > Utils.maxArgs()) {
            File srcFilesArgFile = new File(getTemporaryDir(), "link" + buildType + "SrcFilesArgFile");
            FileWriter writer = new FileWriter(srcFilesArgFile);
            writer.append(srcFilesArgs.join('\n'));
            writer.close()
            // Replace src file arguments by referencing file
            srcFilesArgs = ["@${srcFilesArgFile.path}".toString()]
        }

        try {
            Utils.projectExec(project, stdout, stderr, null, {
                executable 'xcrun'
                args 'lipo'

                args '-create'
                args '-output', project.file("${outputLibDirFile}/lib${project.name}-j2objc.a").absolutePath

                srcFilesArgs.each { String arg ->
                    // Can be list of src files or a single @/../srcFilesArgFile reference
                    args arg
                }

                setErrorOutput stdout
                setStandardOutput stderr
            })

        } catch (Exception exception) {  // NOSONAR
            // TODO: match on common failures and provide useful help
            throw exception
        }
    }
}
