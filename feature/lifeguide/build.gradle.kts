plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.lifeguide"
}

dependencies {
    implementation(projects.shared.data.core)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    // 旧设计系统 UI/主题色/IvyIcon 等在 legacy 与 old-design 模块
    implementation(projects.temp.legacyCode)
    implementation(projects.temp.oldDesign)
}
