plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // Capturas de tela geradas na JVM (sem celular), para revisar o visual.
    alias(libs.plugins.roborazzi)
}

// Room grava o esquema de cada versão do banco em app/schemas/ (versionado no Git).
// É com esses arquivos que as migrações são escritas e testadas: uma atualização do
// app nunca pode apagar os dados. Ver docs/decisoes/0008-banco-de-dados.md.
room {
    schemaDirectory("$projectDir/schemas")
}

// ---------------------------------------------------------------------------
// Versão do app
//
// O CI passa a versão a partir da tag (ex.: tag v0.1.0 -> -PnkpayVersion=0.1.0).
// O versionCode é derivado dela: MAIOR * 10000 + MENOR * 100 + CORREÇÃO.
//   0.0.1 -> 1    0.1.0 -> 100    1.2.3 -> 10203
// Assim ele sempre cresce junto com a versão, e o Android aceita a atualização.
// Ver docs/decisoes/0004-versao-derivada-da-tag.md.
// ---------------------------------------------------------------------------
val nkpayVersion: String = (findProperty("nkpayVersion") as String?) ?: "0.0.0-dev"

fun versionCodeFrom(version: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(version) ?: return 1
    val (major, minor, patch) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100) { "Menor e correção precisam ser < 100" }
    return maxOf(1, major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt())
}

// ---------------------------------------------------------------------------
// Assinatura de release
//
// A keystore NUNCA fica no repositório. No CI, o release.yml decodifica o
// GitHub Secret em um arquivo temporário e preenche estas variáveis de ambiente.
// Sem elas, o APK de release sai sem assinatura (e não pode ser instalado),
// o que é intencional: nunca usamos a chave de debug para distribuir.
// Ver docs/decisoes/0003-assinatura-e-releases.md.
// ---------------------------------------------------------------------------
val keystoreFile: String? = System.getenv("NKPAY_KEYSTORE_FILE")

android {
    namespace = "com.hkawzz.nkote"
    // As bibliotecas do Compose atuais exigem compilar contra o Android 37.2 (API 37, versão menor 2).
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        // ATENÇÃO: o applicationId é permanente. Trocar faz o celular instalar outro app.
        // v0.8 (ADR 0030): trocado de com.nkpay.app para com.hkawzz.nkote, a única janela antes da Play.
        applicationId = "com.hkawzz.nkote"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeFrom(nkpayVersion)
        versionName = nkpayVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("NKPAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NKPAY_KEY_ALIAS")
                keyPassword = System.getenv("NKPAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Permite ter a versão de desenvolvimento instalada ao lado da oficial,
            // sem tocar nos dados reais.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Necessário para os testes do banco (Robolectric) rodarem na JVM, sem emulador.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
