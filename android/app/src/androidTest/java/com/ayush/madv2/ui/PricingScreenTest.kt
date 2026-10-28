package com.ayush.madv2.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ayush.madv2.ui.screens.CreditPackScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PricingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pricingShowsBackendPlansAndSelectsBusiness() {
        var selected = ""
        val plans = listOf(
            PlanDto("starter", "Personal Starter", "personal", 4900, 10, false, false),
            PlanDto("plus", "Personal Plus", "personal", 19900, 50, false, false),
            PlanDto("business", "Business", "business", 69900, 200, true, true))
        compose.setContent { CreditPackScreen(PaymentState(plans = plans), { selected = it }, {}, {}, {}) }
        compose.onNodeWithText("Personal Starter").assertExists()
        compose.onNodeWithText("Personal Plus").assertExists()
        compose.onNodeWithText("Business").performClick()
        compose.onNodeWithText("Continue to Pay", substring = true).performClick()
        compose.runOnIdle { assertEquals("business", selected) }
    }
}
