package co.kr.qgen.core.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import co.kr.qgen.core.data.source.local.dao.ProblemBookDao
import co.kr.qgen.core.data.source.local.dao.ProblemDao
import co.kr.qgen.core.data.source.local.dao.ProblemSetDao
import co.kr.qgen.core.data.source.local.entity.ChoiceEntity
import co.kr.qgen.core.data.source.local.entity.ProblemBookEntity
import co.kr.qgen.core.data.source.local.entity.ProblemEntity
import co.kr.qgen.core.data.source.local.entity.ProblemSetEntity

@Database(
    entities = [ProblemBookEntity::class, ProblemSetEntity::class, ProblemEntity::class, ChoiceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class QGenDatabase : RoomDatabase() {
    abstract fun problemBookDao(): ProblemBookDao
    abstract fun problemSetDao(): ProblemSetDao
    abstract fun problemDao(): ProblemDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create problem_books table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `problem_books` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
        """)

        // 2. Create a default book for existing data
        val defaultBookId = "default-book"
        val currentTime = System.currentTimeMillis()
        db.execSQL("""
            INSERT INTO `problem_books` (`id`, `title`, `createdAt`, `lastPlayedAt`)
            VALUES ('$defaultBookId', '기본 문제집', $currentTime, NULL)
        """)

        // 3. Add bookId column to problem_sets (temporary table approach for migration)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `problem_sets_new` (
                `id` TEXT NOT NULL,
                `bookId` TEXT NOT NULL,
                `topic` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `language` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `lastPlayedAt` INTEGER,
                `score` INTEGER,
                `isFavorite` INTEGER NOT NULL DEFAULT 0,
                `tags` TEXT,
                `title` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`bookId`) REFERENCES `problem_books`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_problem_sets_bookId` ON `problem_sets_new` (`bookId`)")

        // Copy existing data with default bookId
        db.execSQL("""
            INSERT INTO `problem_sets_new` (`id`, `bookId`, `topic`, `difficulty`, `language`, `count`, `createdAt`, `lastPlayedAt`, `score`, `isFavorite`, `tags`, `title`)
            SELECT `id`, '$defaultBookId', `topic`, `difficulty`, `language`, `count`, `createdAt`, `lastPlayedAt`, `score`, `isFavorite`, `tags`, `title`
            FROM `problem_sets`
        """)

        db.execSQL("DROP TABLE `problem_sets`")
        db.execSQL("ALTER TABLE `problem_sets_new` RENAME TO `problem_sets`")

        // 4. Add statistics columns to problems table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `problems_new` (
                `id` TEXT NOT NULL,
                `problemSetId` TEXT NOT NULL,
                `stem` TEXT NOT NULL,
                `correctChoiceId` TEXT NOT NULL,
                `explanation` TEXT NOT NULL,
                `difficulty` TEXT NOT NULL,
                `solvedCount` INTEGER NOT NULL DEFAULT 0,
                `correctCount` INTEGER NOT NULL DEFAULT 0,
                `lastAnsweredAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`problemSetId`) REFERENCES `problem_sets`(`id`) ON DELETE CASCADE
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_problems_problemSetId` ON `problems_new` (`problemSetId`)")

        // Copy existing data with default statistics
        db.execSQL("""
            INSERT INTO `problems_new` (`id`, `problemSetId`, `stem`, `correctChoiceId`, `explanation`, `difficulty`, `solvedCount`, `correctCount`, `lastAnsweredAt`)
            SELECT `id`, `problemSetId`, `stem`, `correctChoiceId`, `explanation`, `difficulty`, 0, 0, NULL
            FROM `problems`
        """)

        db.execSQL("DROP TABLE `problems`")
        db.execSQL("ALTER TABLE `problems_new` RENAME TO `problems`")
    }
}
