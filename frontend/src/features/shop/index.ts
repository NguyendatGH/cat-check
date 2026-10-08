// features/shop — Cửa hàng: catalogue, giỏ hàng, đơn hàng (Shop API `/api/v1/shop/**`,
// `/api/v1/cart/**`, `/api/v1/orders/{id}`). Mọi import từ bên ngoài PHẢI qua file này
// (boundaries/entry-point).
export {
  clearShopCart,
  createShopOrder,
  getShopCart,
  getShopOrder,
  getShopProduct,
  isShopId,
  listShopProducts,
  removeShopCartLine,
  setShopCartLine,
  type CartApi,
  type CartLineApi,
  type OrderApi,
  type ShopProductApi,
} from "./api";
export {
  shopKeys,
  useClearCart,
  useCreateOrder,
  useRemoveCartLine,
  useSetCartLine,
  useShopCart,
  useShopOrder,
  useShopProduct,
  useShopProducts,
  type SetCartLineVars,
} from "./hooks";
