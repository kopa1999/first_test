import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const SHOPPING_URL = 'http://shopping-service:8083';
const ORDER_URL = 'http://order-service:8085';

const USER_ID = Number(__ENV.USER_ID || 1);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);

const RATE = Number(__ENV.RATE || 10);
const DURATION = __ENV.DURATION || '30s';

const orderDuration = new Trend('order_duration', true);
const orderFailed = new Rate('order_failed');
const completedFlows = new Counter('completed_order_flows');

export const options = {
    scenarios: {
        s1_normal: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,

            preAllocatedVUs: 20,
            maxVUs: 100,
        },
    },

    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max'
    ],
};

export default function () {

    // 1. Create cart
    const cartResponse = http.post(
        `${SHOPPING_URL}/carts`,
        JSON.stringify({
            userId: USER_ID
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    if (cartResponse.status !== 200 && cartResponse.status !== 201) {
        return;
    }

    const cartId = cartResponse.json().id;

    // 2. Add product
    const addResponse = http.post(
        `${SHOPPING_URL}/carts/${cartId}/items`,
        JSON.stringify({
            productId: PRODUCT_ID,
            quantity: 1
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    if (addResponse.status !== 200 && addResponse.status !== 201) {
        return;
    }

    // 3. Create order
    const orderResponse = http.post(
        `${ORDER_URL}/orders`,
        JSON.stringify({
            cartId: cartId
        }),
        {
            headers: {
                'Content-Type': 'application/json'
            }
        }
    );

    orderDuration.add(orderResponse.timings.duration);

    const failed = orderResponse.status !== 200;

    orderFailed.add(failed);

    check(orderResponse, {
        'order status 200': (r) => r.status === 200,
    });

    if (!failed) {
        completedFlows.add(1);
    }
}
