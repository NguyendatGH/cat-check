import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  cancelShopOrder,
  clearShopCart,
  createShopOrder,
  getShopCart,
  getShopOrder,
  getShopProduct,
  isShopId,
  listMyOrders,
  listShopPaymentMethods,
  listShopProducts,
  listShopPromos,
  removeShopCartLine,
  setShopCartLine,
  type CartApi,
  type OrderApi,
} from "./api";

export const shopKeys = {
  all: ["shop"] as const,
  products: () => ["shop", "products"] as const,
  product: (productId: string) => ["shop", "product", productId] as const,
  cart: () => ["shop", "cart"] as const,
  paymentMethods: () => ["shop", "payment-methods"] as const,
  promos: () => ["shop", "promos"] as const,
  orders: () => ["shop", "orders"] as const,
  order: (orderId: string) => ["shop", "order", orderId] as const,
};

export function useShopPromos() {
  return useQuery({ queryKey: shopKeys.promos(), queryFn: listShopPromos, staleTime: 60_000 });
}

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

export function useShopPaymentMethods() {
  return useQuery({ queryKey: shopKeys.paymentMethods(), queryFn: listShopPaymentMethods, staleTime: 60_000 });
}

export function useShopCart() {
  return useQuery({ queryKey: shopKeys.cart(), queryFn: getShopCart, staleTime: 0 });
}

// Poll fast only while an unpaid PayOS order can still flip to PAID.
export function orderRefetchMs(order: OrderApi | undefined): number {
  const waitingForPayos =
    order?.status === "PENDING_PAYMENT" &&
    order.paymentMethod === "PAYOS" &&
    order.paymentExpiresAt !== null &&
    Date.parse(order.paymentExpiresAt) > Date.now();
  return waitingForPayos ? 3000 : 30_000;
}

export function useMyOrders() {
  return useQuery({ queryKey: shopKeys.orders(), queryFn: listMyOrders });
}

export function useShopOrder(orderId: string | undefined) {
  return useQuery({
    queryKey: shopKeys.order(orderId ?? ""),
    queryFn: () => getShopOrder(orderId ?? ""),
    enabled: isShopId(orderId),
    refetchInterval: (query) => orderRefetchMs(query.state.data),
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
      void queryClient.invalidateQueries({ queryKey: shopKeys.orders() });
      void queryClient.invalidateQueries({ queryKey: shopKeys.cart() });
      void queryClient.invalidateQueries({ queryKey: shopKeys.products() });
      void queryClient.invalidateQueries({ queryKey: ["shop", "product"] });
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: shopKeys.cart() });
    },
  });
}

export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cancelShopOrder,
    onSuccess: (order: OrderApi) => {
      queryClient.setQueryData<OrderApi>(shopKeys.order(order.id), order);
      void queryClient.invalidateQueries({ queryKey: shopKeys.orders() });
    },
    onError: (_error, orderId) => {
      void queryClient.invalidateQueries({ queryKey: shopKeys.order(orderId) });
    },
  });
}
