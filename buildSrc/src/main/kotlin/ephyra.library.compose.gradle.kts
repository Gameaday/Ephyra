import com.android.build.api.dsl.LibraryExtension
import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureCompose
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("ephyra.code.lint")
}

extensions.configure<LibraryExtension> {
    configureAndroid(this)
    configureCompose(this)
}

configureTest()

