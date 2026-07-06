"""Pehli baar setup: admin user + default plans banata hai.
Chalayein: python seed.py"""
from run import app
from extensions import db
from models import User, SubscriptionPlan

with app.app_context():
    if not SubscriptionPlan.query.filter_by(name="Basic").first():
        db.session.add_all([
            SubscriptionPlan(name="Basic", price=299, duration_days=30,
                             description="1 shop, sab basic features"),
            SubscriptionPlan(name="Pro", price=499, duration_days=30,
                             description="Sab features + reports"),
            SubscriptionPlan(name="Premium", price=4999, duration_days=365,
                             description="1 saal ka plan, sab kuch"),
        ])
        print("Plans ban gaye: Basic Rs.299, Pro Rs.499, Premium Rs.4999")

    mobile = input("Admin mobile number: ").strip()
    password = input("Admin password: ").strip()
    if User.query.filter_by(mobile=mobile).first():
        print("Yeh mobile already registered hai")
    else:
        admin = User(mobile=mobile, first_name="Admin", role="ADMIN")
        admin.set_password(password)
        db.session.add(admin)
        print("Admin ban gaya! Login: http://localhost:8000/admin/")
    db.session.commit()
