plugins {
    id("com.android.application") version "8.8.0" apply false
    kotlin("android") version "1.9.22" apply false
    kotlin("jvm") version "1.9.22" apply false
    kotlin("kapt") version "1.9.22" apply false
    kotlin("plugin.serialization") version "1.9.22" apply false
}

val bossBuildRoot = providers.gradleProperty("bossBuildRoot").orNull
subprojects {
    if (bossBuildRoot != null) layout.buildDirectory.set(file(bossBuildRoot + "/" + project.name))
}
