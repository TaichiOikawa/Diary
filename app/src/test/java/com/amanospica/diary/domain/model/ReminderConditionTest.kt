package com.amanospica.diary.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderConditionTest {

    private val today = LocalDate.of(2026, 8, 30)

    private fun diary(isDraft: Boolean) = Diary(date = today, emoji = "😀", isDraft = isDraft)

    @Test
    fun `毎日必ずは日記があっても通知する`() {
        assertTrue(ReminderCondition.ALWAYS.shouldNotify(emptyList()))
        assertTrue(ReminderCondition.ALWAYS.shouldNotify(listOf(diary(isDraft = false))))
        assertTrue(ReminderCondition.ALWAYS.shouldNotify(listOf(diary(isDraft = true))))
    }

    @Test
    fun `書いていないときだけは日記が無ければ通知する`() {
        assertTrue(ReminderCondition.WHEN_UNWRITTEN.shouldNotify(emptyList()))
    }

    @Test
    fun `書いていないときだけは下書きしか無ければ通知する`() {
        val drafts = listOf(diary(isDraft = true), diary(isDraft = true))
        assertTrue(ReminderCondition.WHEN_UNWRITTEN.shouldNotify(drafts))
    }

    @Test
    fun `書いていないときだけは書き終わった日記があれば通知しない`() {
        assertFalse(ReminderCondition.WHEN_UNWRITTEN.shouldNotify(listOf(diary(isDraft = false))))
    }

    @Test
    fun `下書きに混じって書き終わった日記が1件でもあれば通知しない`() {
        val diaries = listOf(diary(isDraft = true), diary(isDraft = false))
        assertFalse(ReminderCondition.WHEN_UNWRITTEN.shouldNotify(diaries))
    }
}
