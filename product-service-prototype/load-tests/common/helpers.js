import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://order-service:8085';

const SHOPPING_URL = __ENV.SHOPPING_URL || 'http://shopping-service:8083';
const PRODUCT_URL = __ENV.PRODUCT_URL || 'http://product-service:8082';

export function createCart(userId = 1) {
    const response = http.post(
        `${SHOPPING_URL}/carts`,
        JSON.stringify({
            userId: userId
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    check(response, {
        'cart created': (r) => r.status === 200 || r.status === 201,
    });

    if (response.status !== 200 && response.status !== 201) {
        console.error(`Create cart failed: ${response.status} ${response.body}`);
        return null;
    }

    return response.json();
}

export function addItemToCart(cartId, productId, quantity = 1) {
    const response = http.post(
        `${SHOPPING_URL}/carts/${cartId}/items`,
        JSON.stringify({
            productId: productId,
            quantity: quantity
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    check(response, {
        'item added to cart': (r) => r.status === 200 || r.status === 201,
    });

    if (response.status !== 200 && response.status !== 201) {
        console.error(`Add item failed: ${response.status} ${response.body}`);
        return false;
    }

    return true;
}

export function createOrder(cartId, expectedStatus = 200) {
    const response = http.post(
        `${BASE_URL}/orders`,
        JSON.stringify({
            cartId: cartId
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    check(response, {
        [`order status is ${expectedStatus}`]: (r) =>
            r.status === expectedStatus
    });

    console.log(
        `Order cart=${cartId}: HTTP ${response.status}, duration=${response.timings.duration.toFixed(2)} ms`
    );

    return response;
}

export function getProduct(productId) {
    return http.get(`${PRODUCT_URL}/products/${productId}`);
}
