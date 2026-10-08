import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
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
  type OrderApi,
} from "./api";

/**
 * Server state của Cửa hàng qua TanStack Query.
 *
 * Giỏ hàng là MỘT cache duy nhất (`shopKeys.cart()`), luôn là phản hồi nguyên văn của máy chủ:
 * `PUT/DELETE /cart/{productId}` trả lại `CartResponse` đầy đủ nên ta ghi thẳng vào cache —
 * tổng tiền, phí vận chuyển, số lượng đều là con số của máy chủ, frontend không tự cộng.
 */
export const shopKeys = {
  all: ["shop"] as const,
  products: () => ["shop", "products"] as const,
  product: (productId: string) => ["shop", "product", productId] as const,
  cart: () => ["shop", "cart"] as const,
  order: (orderId: string) => ["shop", "order", orderId] as const,
};

export function useShopProducts() {
  return useQuery({ queryKey: shopKeys.products(), queryFn: listShopProducts, staleTime: 60_000 });
}

export function useShopProduct(productId: string | undefined) {
  return useQuery({
    queryKey: shopKeys.product(productId ?? ""),
    queryFn: () => getShopProduct(productId ?? ""),
    enabled: isShopId(productId),
    staleTime: 60_000,
  });
}

export function useShopCart() {
  return useQuery({ queryKey: shopKeys.cart(), queryFn: getShopCart, staleTime: 0 });
}

export function useShopOrder(orderId: string | undefined) {
  return useQuery({
    queryKey: shopKeys.order(orderId ?? ""),
    queryFn: () => getShopOrder(orderId ?? ""),
    enabled: isShopId(orderId),
    // Trạng thái đơn do cửa hàng cập nhật phía máy chủ — làm mới định kỳ khi trang đang mở.
    refetchInterval: 30_000,
  });
}

export interface SetCartLineVars {
  productId: string;
  quantity: number;
}

export function useSetCartLine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, quantity }: SetCartLineVars) => setShopCartLine(productId, quantity),
    onSuccess: (cart) => {
      queryClient.setQueryData<CartApi>(shopKeys.cart(), cart);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: shopKeys.cart() });
    },
  });
}

export function useRemoveCartLine() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (productId: string) => removeShopCartLine(productId),
    onSuccess: (cart) => {
      queryClient.setQueryData<CartApi>(shopKeys.cart(), cart);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: shopKeys.cart() });
    },
  });
}

export function useClearCart() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: clearShopCart,
    onSettled: () => queryClient.invalidateQueries({ queryKey: shopKeys.cart() }),
  });
}

export function useCreateOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createShopOrder,
    onSuccess: (order: OrderApi) => {
      queryClient.setQueryData<OrderApi>(shopKeys.order(order.id), order);
      // Máy chủ đã xoá giỏ và trừ tồn kho trong cùng giao dịch tạo đơn.
      void queryClient.invalidateQueries({ queryKey: shopKeys.cart() });
      void queryClient.invalidateQueries({ queryKey: shopKeys.products() });
      void queryClient.invalidateQueries({ queryKey: ["shop", "product"] });
    },
    onError: () => {
      // CART_EMPTY / STOCK_UNAVAILABLE: giỏ hoặc tồn kho ở máy chủ đã khác cái đang hiển thị.
      void queryClient.invalidateQueries({ queryKey: shopKeys.cart() });
    },
  });
}
