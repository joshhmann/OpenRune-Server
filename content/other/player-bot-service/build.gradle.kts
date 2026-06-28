plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.invPlugin)
    implementation(projects.api.account)
    implementation(projects.api.db)
    implementation(projects.api.pwHash)
    implementation(projects.api.registry)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
}
