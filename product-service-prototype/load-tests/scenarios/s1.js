import { createCart, addItemToCart, createOrder } from '../common/helpers.js';

export const options = {
    vus: 1,
    iterations: 1,
};

export default function () {

    const userId = Number(__ENV.USER_ID || 1);
    const productId = Number(__ENV.PRODUCT_ID || 1);

    console.log('=== S1 NORMAL ORDER ===');

    const cart = createCart(userId);

    if (!cart) {
        throw new Error('Could not create cart');
    }

    console.log(`Created cart: ${cart.id}`);

    const added = addItemToCart(
        cart.id,
        productId,
        1
    );

    if (!added) {
        throw new Error('Could not add product to cart');
    }

    createOrder(cart.id, 200);
}
