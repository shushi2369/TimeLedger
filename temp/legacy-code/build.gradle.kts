plugins {
    id("ivy.feature")
    id("ivy.room")
}

android {
    namespace = "com.ivy.legacy"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.oldDesign)

    implementation(libs.bundles.activity)
    implementation(libs.bundles.opencsv)
    implementation(libs.bundles.firebase)
    implementation(libs.bundles.ktor)
    implementation(libs.androidx.work)
    implementation(libs.datastore)
    implementation(libs.keval)
    implementation(libs.androidx.recyclerview)
    // fork 修复：缺 androidx.hilt 编译器导致 @HiltWorker 无法实例化（通知功能失效），2026-09-27
    ksp(libs.androidx.hilt.compiler)
}
