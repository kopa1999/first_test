#!/bin/bash

set -e

echo "=== Reset test database ==="

PG=$(sudo docker compose ps -q postgres)

if [ -z "$PG" ]; then
    echo "ERROR: PostgreSQL container not found."
    exit 1
fi

echo "PostgreSQL container: $PG"

echo
echo "1. Resetting order database..."

sudo docker exec "$PG" \
  psql -U product_user -d orderdb \
  -c "TRUNCATE TABLE order_items, orders RESTART IDENTITY CASCADE;"

echo
echo "2. Resetting payment database..."

sudo docker exec "$PG" \
  psql -U product_user -d paymentdb \
  -c "TRUNCATE TABLE payment RESTART IDENTITY CASCADE;"

echo
echo "3. Resetting shopping database..."

sudo docker exec "$PG" \
  psql -U product_user -d shoppingdb \
  -c "TRUNCATE TABLE cart_items, carts RESTART IDENTITY CASCADE;"

echo
echo "4. Restoring product stock..."

sudo docker exec "$PG" \
  psql -U product_user -d productdb \
  -c "UPDATE products SET quantity = 100000 WHERE id = 1;"

echo
echo "5. Verifying test state..."

echo
echo "--- Product ---"
sudo docker exec "$PG" \
  psql -U product_user -d productdb \
  -c "SELECT id, name, quantity FROM products WHERE id = 1;"

echo
echo "--- User ---"
sudo docker exec "$PG" \
  psql -U product_user -d userdb \
  -c "SELECT id, email, first_name, last_name FROM users WHERE id = 1;"

echo
echo "--- Orders ---"
sudo docker exec "$PG" \
  psql -U product_user -d orderdb \
  -c "SELECT COUNT(*) AS orders FROM orders;"

echo
echo "--- Payments ---"
sudo docker exec "$PG" \
  psql -U product_user -d paymentdb \
  -c "SELECT COUNT(*) AS payments FROM payment;"

echo
echo "--- Carts ---"
sudo docker exec "$PG" \
  psql -U product_user -d shoppingdb \
  -c "SELECT COUNT(*) AS carts FROM carts;"

echo
echo "=== Database reset completed ==="
