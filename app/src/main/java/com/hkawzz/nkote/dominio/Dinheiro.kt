package com.hkawzz.nkote.dominio

/**
 * Conversão entre centavos e texto.
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

    /** Maior valor aceito na digitação: R$ 999.999.999,99. */
    const val LIMITE_CENTAVOS = 99_999_999_999L

    /** 123456 -> "R$ 1.234,56"   -500 -> "-R$ 5,00" */
    fun formatar(centavos: Long): String {
        val negativo = centavos < 0
        return (if (negativo) "-" else "") + "R$ " + formatarSemSimbolo(centavos)
    }

    /** 123456 -> "1.234,56" (sem sinal e sem "R$"; o sinal é perdido). */
    fun formatarSemSimbolo(centavos: Long): String {
        // Long.MIN_VALUE não tem positivo correspondente; não é um valor real de dinheiro.
        require(centavos != Long.MIN_VALUE) { "Valor fora do intervalo" }
        val absoluto = if (centavos < 0) -centavos else centavos
        val reais = absoluto / 100
        val resto = absoluto % 100
        val reaisComPontos = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        return "$reaisComPontos,${resto.toString().padStart(2, '0')}"
    }

    /** Com sinal explícito: +R$ 5,00 / -R$ 5,00 / R$ 0,00. */
    fun formatarComSinal(centavos: Long): String = when {
        centavos > 0 -> "+" + formatar(centavos)
        else -> formatar(centavos)
    }

    /**
     * Lê o que a pessoa digitou e devolve centavos, ou `null` se não for um valor válido.
     *
     * Aceita: "1234,56"  "1.234,56"  "R$ 1.234,56"  "12"  "12,5"  "-12,50"  "0,99".
     * Rejeita: texto vazio, letras, mais de 2 casas decimais, valores gigantes.
     * A vírgula é o separador decimal; o ponto é separador de milhar (padrão brasileiro).
     */
    fun ler(texto: String): Long? {
        var t = texto.trim().replace("R$", "").replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        val negativo = t.startsWith("-")
        if (negativo) t = t.substring(1)
        if (t.isEmpty()) return null

        val partes = t.split(',')
        if (partes.size > 2) return null
        val inteiraComPontos = partes[0]
        val decimal = partes.getOrNull(1) ?: ""

        // Pontos só como separador de milhar, em grupos de 3 dígitos.
        val inteira = if (inteiraComPontos.contains('.')) {
            if (!Regex("""^\d{1,3}(\.\d{3})+$""").matches(inteiraComPontos)) return null
            inteiraComPontos.replace(".", "")
        } else {
            inteiraComPontos
        }
        if (inteira.isEmpty() && decimal.isEmpty()) return null
        if (!inteira.all { it.isDigit() } || !decimal.all { it.isDigit() }) return null
        if (decimal.length > 2) return null
        if (inteira.length > 9) return null

        val reais = if (inteira.isEmpty()) 0L else inteira.toLong()
        val centavos = if (decimal.isEmpty()) 0L else decimal.padEnd(2, '0').toLong()
        val total = reais * 100 + centavos
        if (total > LIMITE_CENTAVOS) return null
        return if (negativo) -total else total
    }
}
