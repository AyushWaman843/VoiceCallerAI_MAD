import hashlib
import hmac
import unittest
from unittest.mock import patch

from flask import Flask
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database import Base
from app.routes.auth import auth_bp
from app.routes.payments import CreditPurchase, payments_bp


class PaymentTests(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.app.config.update(TESTING=True, RAZORPAY_KEY_ID="rzp_test_demo", RAZORPAY_KEY_SECRET="secret")
        self.app.register_blueprint(auth_bp)
        self.app.register_blueprint(payments_bp)
        self.client = self.app.test_client()
        self.engine = create_engine("sqlite://")
        Base.metadata.create_all(self.engine)
        self.sessions = sessionmaker(bind=self.engine)
        patch("app.routes.payments.get_db", self.sessions).start()
        self.auth = patch("app.services.auth_service.supabase_request", return_value={"id": "a"}).start()
        self.provider = patch("app.routes.payments.razorpay").start()
        self.provider.return_value = {"id": "order_test", "amount": 19900, "currency": "INR"}
        self.headers = {"Authorization": "Bearer test"}
        self.proof = {"order_id": "order_test", "payment_id": "pay_test",
                      "signature": hmac.new(b"secret", b"order_test|pay_test", hashlib.sha256).hexdigest()}

    def tearDown(self):
        patch.stopall()
        self.engine.dispose()

    def order(self):
        return self.client.post("/payments/orders", json={"pack": "plus", "amount": 1}, headers=self.headers)

    def verify(self):
        return self.client.post("/payments/verify", json=self.proof, headers=self.headers)

    def paid(self, **overrides):
        self.provider.return_value = {"order_id": "order_test", "amount": 19900,
                                      "currency": "INR", "status": "captured", **overrides}

    def test_auth_required(self):
        self.assertEqual(self.client.post("/payments/orders", json={"pack": "plus"}).status_code, 401)
        self.provider.assert_not_called()

    def test_server_owns_price_and_secret_stays_private(self):
        result = self.order()
        self.assertEqual(result.status_code, 201)
        self.assertEqual(result.json["amount"], 19900)
        self.assertNotIn("secret", result.get_data(as_text=True))
        self.assertEqual(self.provider.call_args.args[2]["amount"], 19900)

    def test_live_or_missing_keys_rejected(self):
        for key in ("", "rzp_live_example"):
            self.app.config["RAZORPAY_KEY_ID"] = key
            self.assertEqual(self.order().status_code, 503)
        self.provider.assert_not_called()

    def test_invalid_pack_rejected(self):
        for pack in ("free", [], None):
            self.assertEqual(self.client.post("/payments/orders", json={"pack": pack}, headers=self.headers).status_code, 400)

    def test_signature_cannot_grant_credits(self):
        self.order()
        self.provider.reset_mock()
        for signature in ("bad", "é" * 64, "0" * 64):
            self.proof["signature"] = signature
            self.assertEqual(self.verify().status_code, 400)
        self.provider.assert_not_called()

    def test_other_account_cannot_verify(self):
        self.order()
        self.auth.return_value = {"id": "b"}
        self.assertEqual(self.verify().status_code, 404)

    def test_payment_amount_and_order_checked(self):
        self.order()
        for mismatch in ({"amount": 1}, {"order_id": "order_other"}, {"currency": "USD"}, {"status": "failed"}):
            self.paid(**mismatch)
            self.assertIn(self.verify().status_code, (400, 409))
        with self.sessions() as db:
            self.assertIsNone(db.get(CreditPurchase, "order_test").payment_id)

    def test_duplicate_verification_credits_once(self):
        self.order()
        self.paid()
        for _ in range(2):
            result = self.verify()
            self.assertEqual(result.status_code, 200)
            self.assertEqual(result.json["balance"], 50)

    def test_onboarding_requires_verified_purchase_for_this_user(self):
        def status():
            return self.client.get("/payments/status", headers=self.headers).json["has_purchase"]
        self.assertFalse(status())
        self.order()
        self.assertFalse(status())
        self.paid()
        self.verify()
        self.assertTrue(status())
        wallet = self.client.get("/payments/status", headers=self.headers).json
        self.assertEqual(wallet["balance"], 50)
        self.assertEqual(wallet["purchases"][0]["pack"], "plus")
        self.assertEqual(len(wallet["purchases"]), 1)
        self.auth.return_value = {"id": "b"}
        self.assertFalse(status())
        self.assertEqual(self.client.get("/payments/status", headers=self.headers).json["balance"], 0)

    def test_purchase_status_requires_auth(self):
        self.assertEqual(self.client.get("/payments/status").status_code, 401)

    def test_authorized_payment_captured_before_credit(self):
        self.order()
        self.provider.side_effect = [
            {"order_id": "order_test", "amount": 19900, "currency": "INR", "status": "authorized"},
            {"status": "captured"}]
        self.assertEqual(self.verify().status_code, 200)
        self.assertEqual(self.provider.call_args.args, ("POST", "payments/pay_test/capture", {"amount": 19900, "currency": "INR"}))


if __name__ == "__main__":
    unittest.main()
