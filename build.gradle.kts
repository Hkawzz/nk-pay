// Arquivo de build da raiz: só declara os plugins usados pelo módulo `app`.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
