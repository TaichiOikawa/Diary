package com.amanospica.diary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.amanospica.diary.data.local.converter.DiaryTypeConverters
import com.amanospica.diary.data.local.dao.DiaryDao
import com.amanospica.diary.data.local.entity.DiaryEntity

@Database(
    entities = [DiaryEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(DiaryTypeConverters::class)
abstract class DiaryDatabase : RoomDatabase() {

    abstract fun diaryDao(): DiaryDao

    companion object {
        private const val DATABASE_NAME = "diary.db"

        /**
         * v2: 書きかけの日記を見分けるための `isDraft` を追加。
         * 既にある日記は書き終わったものとして扱いたいので 0（＝下書きでない）で埋める。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE diaries ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v3: お気に入り（星）を見分けるための `isFavorite` を追加。
         * 既にある日記は星なしとして 0 で埋める。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE diaries ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var instance: DiaryDatabase? = null

        fun getInstance(context: Context): DiaryDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): DiaryDatabase =
            Room.databaseBuilder(context, DiaryDatabase::class.java, DATABASE_NAME)
                // 完全ローカル完結アプリのため、破壊的マイグレーションは使わない。
                // スキーマ変更時は必ず Migration を追加すること（schemas/ に JSON を出力済み）。
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
