import com.android.build.api.dsl.TestExtension
import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.test")

    id("mihon.code.lint")
}

extensions.configure<TestExtension> {
    configureAndroid(this)
}

configureTest()
