package com.hkawzz.nkote.dados

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// v0.7 (banco v6, ADR 0028): objetivos e movimentos de reserva.

@Dao
interface ObjetivoDao {
    @Query("SELECT * FROM objetivo WHERE excluidoEm IS NULL ORDER BY nome COLLATE NOCASE, id")
    fun observarValidos(): Flow<List<ObjetivoEntidade>>

    @Query("SELECT * FROM objetivo WHERE excluidoEm IS NULL ORDER BY nome COLLATE NOCASE, id")
    suspend fun validos(): List<ObjetivoEntidade>

    @Query("SELECT * FROM objetivo WHERE id = :id") suspend fun buscar(id: String): ObjetivoEntidade?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(o: ObjetivoEntidade)
    @Update suspend fun atualizar(o: ObjetivoEntidade)
}

@Dao
interface MovimentoReservaDao {
    /** Movimentos válidos (não excluídos) de objetivos não excluídos. */
    @Query(
        "SELECT m.* FROM movimento_reserva m JOIN objetivo o ON o.id = m.objetivoId " +
            "WHERE m.excluidoEm IS NULL AND o.excluidoEm IS NULL ORDER BY m.data, m.criadoEm, m.id",
    )
    fun observarValidos(): Flow<List<MovimentoReservaEntidade>>

    @Query(
        "SELECT m.* FROM movimento_reserva m JOIN objetivo o ON o.id = m.objetivoId " +
            "WHERE m.excluidoEm IS NULL AND o.excluidoEm IS NULL ORDER BY m.data, m.criadoEm, m.id",
    )
    suspend fun validos(): List<MovimentoReservaEntidade>

    @Query("SELECT * FROM movimento_reserva WHERE grupoId = :grupoId AND excluidoEm IS NULL")
    suspend fun doGrupo(grupoId: String): List<MovimentoReservaEntidade>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun inserir(m: MovimentoReservaEntidade)
    @Update suspend fun atualizar(m: MovimentoReservaEntidade)
}
