package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.LoanRecordEntity
import java.util.UUID

@Dao
interface WriteLoanRecordDao {
    @Upsert
    suspend fun save(value: LoanRecordEntity)

    @Upsert
    suspend fun saveMany(value: List<LoanRecordEntity>)

    @Query("DELETE FROM loan_records WHERE id = :id")
    suspend fun deleteById(id: UUID)

    // fork 修复：删贷款时清其分期记录（原残留孤儿行随备份永久累积）
    @Query("DELETE FROM loan_records WHERE loanId = :loanId")
    suspend fun deletedByLoanId(loanId: UUID)

    @Query("DELETE FROM loan_records")
    suspend fun deleteAll()
}
