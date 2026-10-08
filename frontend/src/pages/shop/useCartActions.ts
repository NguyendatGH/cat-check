import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { toast } from "@/shared/ui";
import { getShopCart, shopKeys, useRemoveCartLine, useSetCartLine, type ShopProductApi } from "@/features/shop";
import { maxOrderQuantity } from "./shopFormat";

/**
 * Thao tác trên dòng giỏ (tăng/giảm/xoá). Không cập nhật lạc quan: nút của dòng đang gửi bị
 * khoá cho tới khi máy chủ trả `CartResponse` mới — nhờ vậy số lượng và tổng tiền hiển thị
 * luôn là con số của máy chủ. Lỗi ⇒ toast + tải lại giỏ (hook mutation tự invalidate).
 */
export function useCartLineActions() {
  const { t } = useTranslation("shop");
  const setLine = useSetCartLine();
  const removeLine = useRemoveCartLine();

  const pendingProductId = setLine.isPending
    ? setLine.variables.productId
    : removeLine.isPending
      ? removeLine.variables
      : null;

  return {
    pendingProductId,
    isUpdating: setLine.isPending || removeLine.isPending,
    setQuantity: (productId: string, quantity: number) => {
      setLine.mutate(
        { productId, quantity },
        {
          onError: () => {
            toast.error(t("toast.updateError"));
          },
        },
      );
    },
    remove: (productId: string) => {
      removeLine.mutate(productId, {
        onError: () => {
          toast.error(t("toast.updateError"));
        },
      });
    },
  };
}

/**
 * Thêm sản phẩm vào giỏ: đọc số lượng đang có trong giỏ THẬT (cache `GET /cart`, tải nếu
 * chưa có), cộng thêm, chặn ở `min(tồn kho, 99)` rồi `PUT /cart/{productId}`.
 * `goToCheckout` (nút "Mua ngay") chỉ chuyển trang SAU KHI máy chủ đã ghi dòng giỏ.
 */
export function useAddToCart() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setLine = useSetCartLine();

  const add = async (product: ShopProductApi, quantity: number, options: { goToCheckout?: boolean } = {}) => {
    let existing: number;
    try {
      // Dùng cache giỏ nếu còn mới (mutation luôn ghi phản hồi máy chủ vào đó), không thì tải lại.
      const cart = await queryClient.query({ queryKey: shopKeys.cart(), queryFn: getShopCart, staleTime: 5_000 });
      existing = cart.lines.find((line) => line.product.id === product.id)?.quantity ?? 0;
    } catch {
      toast.error(t("toast.addError"));
      return;
    }
    const max = maxOrderQuantity(product.stockQuantity);
    const target = Math.min(existing + quantity, max);
    if (target <= existing) {
      if (options.goToCheckout && existing > 0) {
        void navigate("/checkout");
        return;
      }
      toast.info(t("toast.maxReached", { count: max }));
      return;
    }
    try {
      await setLine.mutateAsync({ productId: product.id, quantity: target });
    } catch {
      toast.error(t("toast.addError"));
      return;
    }
    if (options.goToCheckout) {
      void navigate("/checkout");
      return;
    }
    toast.success(t("toast.added", { name: product.name }), {
      description: target < existing + quantity ? t("toast.cappedToStock", { count: target }) : undefined,
      action: {
        label: t("toast.viewCart"),
        onClick: () => {
          void navigate("/cart");
        },
      },
    });
  };

  return {
    add,
    pendingProductId: setLine.isPending ? setLine.variables.productId : null,
  };
}
