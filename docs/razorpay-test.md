# Razorpay test checkout

1. Enable **Test Mode** in Razorpay Dashboard and create test API keys.
2. Put `RAZORPAY_KEY_ID=rzp_test_...` and `RAZORPAY_KEY_SECRET=...` in `backend/.env`; restart Flask. Live keys are rejected. The secret stays on Flask.
3. Sign up or sign in on Android, choose Personal Starter, Personal Plus or Business, and complete Razorpay's test checkout. The backend verifies the signature, owned order, amount, currency, and captured payment before adding test credits or changing tier.
4. If verification loses connectivity after the payment callback, tap **Retry verification**. Repeated verification cannot add credits twice. Returning users with a verified plan go straight to their dashboard.

Use [Razorpay's official Android test payment details](https://razorpay.com/docs/payments/payment-gateway/android-integration/standard/test-integration/). This wallet contains test credits only. If Android loses the checkout callback entirely, check the Razorpay dashboard before repeating payment.

Pricing, Business capabilities, credit reservation/refunds, database tables, and run instructions are in [Voxie plans and Business MVP](voxie-plans-business.md).
