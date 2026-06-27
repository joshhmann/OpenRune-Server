plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.other.playerBotService)
    implementation(projects.api.pluginCommons)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.api.gameProcess)
    implementation(projects.api.registry)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
}
