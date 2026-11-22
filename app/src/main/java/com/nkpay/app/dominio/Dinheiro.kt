package com.nkpay.app.dominio

/**
 * Formatação de valores em dinheiro.
 *
 * Todo valor do app é guardado em CENTAVOS inteiros (Long), nunca em Double:
 * 0,1 + 0,2 em ponto flutuante dá 0,30000000000000004, e em finanças esse
 * erro acumula. Ver docs/decisoes/0002-dinheiro-em-centavos.md.
 *
 * A formatação é feita à mão (e não com NumberFormat) para o resultado ser
 * idêntico em todo aparelho, sem depender da versão do Android ou de espaços
 * especiais que o sistema insere.
 */
object Dinheiro {

    /** 123456 -> "R$ 1.234,56"   -500 -> "-R$ 5,00" */
    fun formatar(centavos: Long): String {
        val negativo = centavos < 0
        // Long.MIN_VALUE não tem positivo correspondente; não é um valor real de dinheiro.
        require(centavos != Long.MIN_VALUE) { "Valor fora do intervalo" }
        val absoluto = if (negativo) -centavos else centavos
        val reais = absoluto / 100
        val resto = absoluto % 100
        val reaisComPontos = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        val texto = "R$ $reaisComPontos,${resto.toString().padStart(2, '0')}"
        return if (negativo) "-$texto" else texto
    }
}
