import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        order_flow: {
            executor: 'constant-arrival-rate',

            rate: 100,
            timeUnit: '1s',

            duration: '30s',

            preAllocatedVUs: 20,
            maxVUs: 100,
        },
    },
};

const SHOPPING_URL =
    __ENV.SHOPPING_URL || 'http://shopping-service:8083';

const ORDER_URL =
    __ENV.ORDER_URL || 'http://order-service:8085';

const USER_ID = Number(__ENV.USER_ID || 3);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 4);

const params = {
    headers: {
        'Content-Type': 'application/json',
    },
};

export default function () {

    // 1. Create cart
    const cartResponse = http.post(
        `${SHOPPING_URL}/carts`,
        JSON.stringify({
            userId: USER_ID,
        }),
        params
    );

    check(cartResponse, {
        'cart created': (r) => r.status === 200,
    });

    if (cartResponse.status !== 200) {
        return;
    }

    const cartId = cartResponse.json('id');

    // 2. Add product
    const addItemResponse = http.post(
        `${SHOPPING_URL}/carts/${cartId}/items`,
        JSON.stringify({
            productId: PRODUCT_ID,
            quantity: 1,
        }),
        params
    );

    check(addItemResponse, {
        'product added': (r) => r.status === 200,
    });

    if (addItemResponse.status !== 200) {
        return;
    }

    // 3. Create order
    const orderResponse = http.post(
        `${ORDER_URL}/orders`,
        JSON.stringify({
            cartId: cartId,
        }),
        params
    );

    check(orderResponse, {
        'order completed': (r) => r.status === 200,
    });
}