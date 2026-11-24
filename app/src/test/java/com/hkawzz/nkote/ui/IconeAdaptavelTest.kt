package com.hkawzz.nkote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.hkawzz.nkote.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot

/**
 * v0.8.1 (logo 3A): o desenho da frente do ícone adaptável fica todo dentro da zona segura
 * (círculo de 66 dp no centro da tela de 108 dp), então nenhuma máscara corta nada. As
 * capturas mostram o ícone nas máscaras de círculo, quadrado arredondado e "squircle".
 * Os testes rodam no build de debug: a frente testada é a do app de teste, com o selo DEBUG,
 * que contém o logo inteiro e mais o selo.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconeAdaptavelTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val px = 4 // pixels por dp
    private val lado = 108 * px

    private fun desenhar(id: Int, mascara: Path? = null, comFundo: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        mascara?.let { canvas.clipPath(it) }
        if (comFundo) ContextCompat.getDrawable(contexto, R.drawable.ic_launcher_background)!!.apply { setBounds(0, 0, lado, lado) }.draw(canvas)
        ContextCompat.getDrawable(contexto, id)!!.apply { setBounds(0, 0, lado, lado) }.draw(canvas)
        return bmp
    }

    private fun foraDaZonaSegura(bmp: Bitmap): Int {
        val centro = lado / 2f
        val raio = 33f * px
        var fora = 0
        for (y in 0 until lado) for (x in 0 until lado) {
            if ((bmp.getPixel(x, y) ushr 24) > 8 && hypot(x + 0.5f - centro, y + 0.5f - centro) > raio) fora++
        }
        return fora
    }

    @Test
    fun `frente e monocromatica ficam dentro da zona segura de 66 dp`() {
        for (id in listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            val bmp = desenhar(id)
            assertTrue("o ícone não desenhou nada", (0 until lado).any { y -> (0 until lado).any { x -> (bmp.getPixel(x, y) ushr 24) > 8 } })
            assertEquals("pixels fora da zona segura", 0, foraDaZonaSegura(bmp))
        }
    }

    @Test
    fun `icone nas mascaras de circulo, quadrado arredondado e squircle`() {
        val s = lado.toFloat()
        // Área visível do ícone: 72 dp no centro (o resto da tela de 108 dp fica para os efeitos).
        val m = 18f * px
        val r = RectF(m, m, s - m, s - m)
        val mascaras = mapOf(
            "circulo" to Path().apply { addOval(r, Path.Direction.CW) },
            "quadrado-arredondado" to Path().apply { addRoundRect(r, 12f * px, 12f * px, Path.Direction.CW) },
            "squircle" to Path().apply { addRoundRect(r, 24f * px, 24f * px, Path.Direction.CW) },
        )
        for ((nome, mascara) in mascaras) {
            desenhar(R.drawable.ic_launcher_foreground, mascara, comFundo = true).captureRoboImage("build/outputs/roborazzi/icone-$nome.png")
        }
        // A assinatura do Sobre também desenha.
        val assinatura = ContextCompat.getDrawable(contexto, R.drawable.nkote_assinatura)!!
        val bmp = Bitmap.createBitmap(366 * 2, 130 * 2, Bitmap.Config.ARGB_8888)
        assinatura.setBounds(0, 0, bmp.width, bmp.height)
        assinatura.draw(Canvas(bmp))
        bmp.captureRoboImage("build/outputs/roborazzi/assinatura-sobre.png")
    }
}
