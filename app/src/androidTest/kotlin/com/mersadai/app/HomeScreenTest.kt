package com.mersadai.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeAndArabicNavigationAreVisible() {
        composeRule.onNodeWithText("مِرصد AI").assertIsDisplayed()
        composeRule.onNodeWithText("الرئيسية").assertIsDisplayed()
    }
}
