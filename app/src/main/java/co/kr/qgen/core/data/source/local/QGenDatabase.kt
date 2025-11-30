package co.kr.qgen.core.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import co.kr.qgen.core.data.source.local.dao.ProblemDao
import co.kr.qgen.core.data.source.local.dao.ProblemSetDao
import co.kr.qgen.core.data.source.local.entity.ChoiceEntity
import co.kr.qgen.core.data.source.local.entity.ProblemEntity
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity

@Database(
    entities = [ProblemSetEntity::class, ProblemEntity::class, ChoiceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QGenDatabase : RoomDatabase() {
    abstract fun problemSetDao(): ProblemSetDao
    abstract fun problemDao(): ProblemDao
}
