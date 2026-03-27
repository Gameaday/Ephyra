import com.android.build.api.dsl.LibraryExtension
import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.library")

    id("mihon.code.lint")
}

extensions.configure<LibraryExtension> {
    configureAndroid(this)
}

configureTest()
