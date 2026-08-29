package com.amanospica.diary.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * スキーマ変更で既存の日記が失われないことを確かめる。
 * 完全ローカル完結アプリなので、消えたデータは取り戻せない。
 */
@RunWith(AndroidJUnit4::class)
class DiaryDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DiaryDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsExistingDiariesAndMarksThemAsNotDraft() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO diaries (id, date, emoji, title, blocksJson, createdAt, updatedAt)
                VALUES ('d1', '2026-08-02', '😊', '海までドライブ', '[]', 100, 100)
                """
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            DiaryDatabase.MIGRATION_1_2,
        )

        db.query("SELECT title, isDraft FROM diaries WHERE id = 'd1'").use { cursor ->
            assert(cursor.moveToFirst())
            assertEquals("海までドライブ", cursor.getString(0))
            // v1 までに書いた日記は「書き終わったもの」として扱う
            assertEquals(0, cursor.getInt(1))
        }
    }

    @Test
    fun migrate2To3KeepsExistingDiariesAndMarksThemAsNotFavorite() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO diaries (id, date, emoji, title, blocksJson, isDraft, createdAt, updatedAt)
                VALUES ('d1', '2026-08-02', '😊', '海までドライブ', '[]', 1, 100, 100)
                """
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            DiaryDatabase.MIGRATION_2_3,
        )

        db.query("SELECT title, isDraft, isFavorite FROM diaries WHERE id = 'd1'").use { cursor ->
            assert(cursor.moveToFirst())
            assertEquals("海までドライブ", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            // v2 までに書いた日記には星が付いていない
            assertEquals(0, cursor.getInt(2))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
