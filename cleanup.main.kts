#!/usr/bin/env kotlin

import java.io.File

val mavenFileName = "maven-metadata.xml"
val latestVersionRegex = "<latest>(\\d{1,3}\\.\\d{1,3}\\.\\d{1,4})(?:.*)<\\/latest>".toRegex()
val versionRegex =
    "<version>(((?:[a-z,0-9]{7})|(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,4})).*)<\\/version>".toRegex(RegexOption.MULTILINE)
val gitHashVersion = "([a-z,0-9]{7})".toRegex()
val semanticVersion = "((\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,4}))".toRegex()

val snapshots = searchFolders("snapshots")

printHeader("Snapshot versions")
val snapshotVersions = searchLatestVersions(snapshots)
printFooter()

printHeader("Clean Snapshot Versions")
cleanVersions(snapshots)
printFooter()

fun cleanVersions(folders: Sequence<File>) {
    folders.forEach { folder ->
        val name = dependencyName(folder)
        printHeader("Cleaning: $name")

        val mavenFile = File(folder, mavenFileName).readText().lines()
        val versions = mavenFile
            .filter { line -> versionRegex.containsMatchIn(line) }
            .map { line ->
                val result = versionRegex.find(line)!!
                result.groupValues[2] to result.groupValues[1]
            }

        val latestVersion = snapshotVersions[name]
            ?: throw UnsupportedOperationException("No latest version found")
        println("Latest Version: $latestVersion")
        printLine()

        val versionsToRemove: List<Pair<String, String>> = when {
            semanticVersion.matches(latestVersion) -> {
                searchSemanticVersionsToRemove(latestVersion, versions)
            }
            gitHashVersion.matches(latestVersion) -> {
                println("WARNING: can't handle git hash version type: $latestVersion")
                emptyList()
            }
            else -> {
                println("WARNING: unknown version type: $latestVersion")
                emptyList()
            }
        }

        if (versionsToRemove.isNotEmpty()) {
            println(">>> Removing ${versionsToRemove.count()} versions:")
            cleanMavenFile(folder, mavenFile, versionsToRemove)
            cleanVersionFolders(folder, versionsToRemove)
        } else {
            println("> Nothing to do")
        }

        printFooter()
    }
}

fun searchFolders(path: String): Sequence<File> {
    return File(path).walk()
        .filter { it.listFiles()?.contains(File(it, mavenFileName)) ?: false }
        .filter { it.listFiles().any { file -> file.isDirectory } }
}

fun searchLatestVersions(folders: Sequence<File>): Map<String, String> {
    val versions = mutableMapOf<String, String>()
    folders.forEach { folder ->
        File(folder, mavenFileName).readText().lines().forEach { line ->
            if (latestVersionRegex.containsMatchIn(line)) {
                val name = dependencyName(folder)
                val latestVersion = latestVersionRegex.find(line)!!.groupValues[1]
                versions[name] = latestVersion
                println("$name: $latestVersion")
            }
        }
    }
    return versions
}

fun dependencyName(folder: File) = "${folder.parentFile.name}/${folder.name}"

fun searchSemanticVersionsToRemove(
    latestVersion: String,
    versions: List<Pair<String, String>>
): List<Pair<String, String>> {
    val (_, latestMajor, latestMinor, latestPatch) = semanticVersion.matchEntire(latestVersion)!!.destructured
    return versions.filter { (version, _) ->
        if (semanticVersion.matches(version)) {
            val (_, major, minor, patch) = semanticVersion.matchEntire(version)!!.destructured
            if (latestMajor == major) {
                if (latestMinor.toInt() > minor.toInt().plus(1)) {
                    return@filter true
                } else if (latestMinor.toInt() > minor.toInt()) {
                    if (latestPatch.toInt() > patch.toInt().plus(2)) return@filter true
                }
            } else {
                println("WARNING: can't handle major version difference: $latestVersion vs $version")
                //TODO
            }
        } else if (gitHashVersion.matches(version)) {
            println("WARNING: can't handle gitHash version: $latestVersion vs $version")
            //TODO
        }
        return@filter false
    }
}

fun cleanMavenFile(folder: File, mavenFile: List<String>, versionsToRemove: List<Pair<String, String>>) {
    val cleanedFile = mavenFile.filter { line ->
        if (versionRegex.containsMatchIn(line)) {
            val result = versionRegex.find(line)!!
            if (versionsToRemove.contains(result.groupValues[2] to result.groupValues[1])) {
                return@filter false
            }
        }
        return@filter true
    }

    File(folder, mavenFileName).writeText(cleanedFile.joinToString(separator = "\n"))
}

fun cleanVersionFolders(folder: File, versionsToRemove: List<Pair<String, String>>) {
    versionsToRemove.forEach { (_, versionfolderName) ->
        println("$versionfolderName is removed!")

        File("${folder.path}/$versionfolderName").deleteRecursively()
    }
}

fun printHeader(title: String) {
    printLine()
    println(title)
    printLine()
}

fun printFooter() {
    printLine()
    println("")
}

fun printLine() {
    println("-----------------------------------------------------")
}
