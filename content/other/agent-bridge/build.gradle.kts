plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.db)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.pwHash)
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
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.java.websocket)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.or2.all.cache)
}
