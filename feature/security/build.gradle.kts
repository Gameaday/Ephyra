plugins {
    id("mihon.library")
    id("mihon.library.compose")

    id("com.google.devtools.ksp")
}

android {
    namespace = "ephyra.feature.security"
}

dependencies {
    api(projects.core.common)
    api(projects.domain)
    api(projects.data)
    api(projects.sourceApi)
    api(projects.i18n)
    api(projects.presentationCore)
}
