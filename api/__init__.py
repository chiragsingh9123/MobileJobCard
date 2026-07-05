from api.auth_routes import auth_bp
from api.customer_routes import customer_bp
from api.job_routes import job_bp
from api.inventory_routes import inventory_bp
from api.payment_routes import payment_bp
from api.report_routes import report_bp
from api.staff_routes import staff_bp
from api.subscription_routes import subscription_bp
from api.shop_routes import shop_bp

ALL_BLUEPRINTS = [auth_bp, customer_bp, job_bp, inventory_bp, payment_bp, report_bp,
 staff_bp, subscription_bp, shop_bp]
