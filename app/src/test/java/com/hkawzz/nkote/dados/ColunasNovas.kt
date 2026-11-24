package com.hkawzz.nkote.dados

/**
 * Colunas acrescentadas DEPOIS das versões que os testes de migração antigos conferem.
 * Esses testes comparam linha a linha o banco antigo com o banco depois de migrar até a
 * versão atual; colunas novas (que nascem com valor padrão) ficam fora da comparação e
 * são conferidas no teste da própria migração. v0.9: `conta.ordem` (MigracaoV8Test).
 */
val COLUNAS_NOVAS = setOf("ordem")