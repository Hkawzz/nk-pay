package com.nkpay.app.dominio

import java.text.Normalizer

/** Ícone (nome do conjunto do app) e cor (ARGB) de uma categoria ou conta. */
data class Aparencia(val icone: String, val cor: Long)

/**
 * Aparência sugerida pelo NOME da categoria (regra 6C, ADR 0018). A comparação ignora
 * maiúsculas, acentos e espaços nas pontas. Nome desconhecido recebe a aparência neutra.
 */
object AparenciaSugerida {
    val NEUTRA = Aparencia("outros", 0xFFA1A1AA)

    private val porNome: Map<String, Aparencia> = mapOf(
        "alimentacao" to Aparencia("carrinho", 0xFF4ADE80),
        "mercado" to Aparencia("carrinho", 0xFF4ADE80),
        "restaurante" to Aparencia("talheres", 0xFFFB923C),
        "refeicao" to Aparencia("talheres", 0xFFFB923C),
        "lanche" to Aparencia("cafe", 0xFFFB923C),
        "moradia" to Aparencia("casa", 0xFF60A5FA),
        "casa" to Aparencia("casa", 0xFF60A5FA),
        "aluguel" to Aparencia("casa", 0xFF60A5FA),
        "contas" to Aparencia("recibo", 0xFF60A5FA),
        "energia" to Aparencia("energia", 0xFFFACC15),
        "luz" to Aparencia("energia", 0xFFFACC15),
        "agua" to Aparencia("agua", 0xFF22D3EE),
        "internet" to Aparencia("wifi", 0xFF22D3EE),
        "transporte" to Aparencia("onibus", 0xFFFACC15),
        "combustivel" to Aparencia("carro", 0xFFFACC15),
        "carro" to Aparencia("carro", 0xFFFACC15),
        "viagem" to Aparencia("aviao", 0xFF2DD4BF),
        "saude" to Aparencia("saude", 0xFFF87171),
        "farmacia" to Aparencia("saude", 0xFFF87171),
        "academia" to Aparencia("academia", 0xFFF87171),
        "lazer" to Aparencia("jogo", 0xFFF472B6),
        "diversao" to Aparencia("jogo", 0xFFF472B6),
        "tecnologia" to Aparencia("computador", 0xFFA78BFA),
        "assinaturas" to Aparencia("assinatura", 0xFFC084FC),
        "streaming" to Aparencia("tv", 0xFFC084FC),
        "educacao" to Aparencia("escola", 0xFF60A5FA),
        "cursos" to Aparencia("escola", 0xFF60A5FA),
        "compras" to Aparencia("sacola", 0xFFF472B6),
        "roupas" to Aparencia("sacola", 0xFFF472B6),
        "presentes" to Aparencia("presente", 0xFFF472B6),
        "beleza" to Aparencia("beleza", 0xFFF472B6),
        "salario" to Aparencia("trabalho", 0xFF4ADE80),
        "trabalho" to Aparencia("trabalho", 0xFF4ADE80),
        "rendimentos" to Aparencia("crescimento", 0xFF4ADE80),
        "investimentos" to Aparencia("crescimento", 0xFF4ADE80),
        "celular" to Aparencia("celular", 0xFFA78BFA),
        "outros" to NEUTRA,
        "encargos financeiros" to Aparencia("crescimento", 0xFFF87171),
    )

    fun normalizar(nome: String): String =
        Normalizer.normalize(nome.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** A sugestão para um nome, ou null se o nome não é conhecido. */
    fun paraNome(nome: String): Aparencia? = porNome[normalizar(nome)]

    /** Sugestão ou, se o nome não é conhecido, a neutra. */
    fun ouNeutra(nome: String): Aparencia = paraNome(nome) ?: NEUTRA

    /** Aparência padrão de uma conta, pela natureza (usada quando a conta não tem ícone). */
    fun daConta(natureza: Natureza, tipoBeneficio: TipoBeneficio?): Aparencia = when (natureza) {
        Natureza.PROPRIO -> Aparencia("carteira", 0xFFA78BFA)
        Natureza.BENEFICIO -> when (tipoBeneficio) {
            TipoBeneficio.VA -> Aparencia("carrinho", 0xFF4ADE80)
            TipoBeneficio.VR -> Aparencia("talheres", 0xFFFB923C)
            else -> Aparencia("presente", 0xFFF472B6)
        }
        Natureza.CARTAO -> Aparencia("cartao", 0xFFC084FC)
        Natureza.CREDIARIO -> Aparencia("recibo", 0xFF60A5FA)
    }
}
