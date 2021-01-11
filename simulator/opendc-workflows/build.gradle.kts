/*
 * Copyright (c) 2019 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

description = "Workflow service for OpenDC"

/* Build configuration */
plugins {
    `kotlin-library-convention`
}

dependencies {
    api(project(":opendc-core"))
    api(project(":opendc-compute:opendc-compute-core"))
    api(project(":opendc-trace:opendc-trace-core"))
    implementation(project(":opendc-utils"))
    implementation("io.github.microutils:kotlin-logging:1.7.9")

    testImplementation(project(":opendc-simulator:opendc-simulator-core"))
    testImplementation(project(":opendc-compute:opendc-compute-simulator"))
    testImplementation(project(":opendc-format"))
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.8") {
        exclude("org.jetbrains.kotlin", module = "kotlin-reflect")
    }
    testImplementation(kotlin("reflect"))
    testRuntimeOnly("org.slf4j:slf4j-simple:${Library.SLF4J}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Library.JUNIT_JUPITER}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${Library.JUNIT_JUPITER}")
    testImplementation("org.junit.platform:junit-platform-launcher:${Library.JUNIT_PLATFORM}")
}
