plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.content.other.playerBotService)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.hunt)
    implementation(projects.api.registry)
    implementation(projects.api.route)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    implementation(projects.api.invPlugin)
    implementation(projects.engine.module)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.java.websocket)
    implementation(libs.or2.all.cache)
}
