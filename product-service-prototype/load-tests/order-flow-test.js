import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 1,
    iterations: 1,
};

const SHOPPING_URL = 'http://shopping-service:8083';
const ORDER_URL = 'http://order-service:8085';

const USER_ID = 2;
const PRODUCT_ID = 4;

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
            userId: USER_ID
        }),
        params
    );

    check(cartResponse, {
        'cart created': (response) => response.status === 200,
    });

    if (cartResponse.status !== 200) {
        return;
    }

    const cartId = cartResponse.json('id');

    console.log(`Created cart: ${cartId}`);


    // 2. Add item
    const addItemResponse = http.post(
        `${SHOPPING_URL}/carts/${cartId}/items`,
        JSON.stringify({
            productId: PRODUCT_ID,
            quantity: 1
        }),
        params
    );

    check(addItemResponse, {
        'product added to cart': (response) => response.status === 200,
    });

    if (addItemResponse.status !== 200) {
        return;
    }


    // 3. Create order
    const orderResponse = http.post(
        `${ORDER_URL}/orders`,
        JSON.stringify({
            cartId: cartId
        }),
        params
    );

    check(orderResponse, {
        'order created': (response) => response.status === 200,
    });

    if (orderResponse.status === 200) {
        const orderId = orderResponse.json('id');
        console.log(`Created order: ${orderId}`);
    }
}