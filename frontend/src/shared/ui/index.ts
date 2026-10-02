// shared/ui — 12 component nền bắt buộc (p10). Mọi import từ feature/page/entity PHẢI
// qua barrel này (boundaries/entry-point áp dụng cho feature/entity; shared tự do nhưng
// giữ quy ước 1 cửa cho gọn).
export { Button, buttonVariants } from "./Button";
export type { ButtonProps, ButtonVariantProps } from "./Button";

export { Input } from "./Input";
export type { InputProps } from "./Input";

export { Card } from "./Card";
export type { CardProps } from "./Card";

export { Badge, badgeVariants } from "./Badge";
export type { BadgeProps, BadgeVariantProps } from "./Badge";

export { Toast, toast } from "./Toast";
export type { ToastProps } from "./Toast";

export {
  Dialog,
  DialogTrigger,
  DialogClose,
  DialogOverlay,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "./Dialog";

export { Sheet, SheetTrigger, SheetClose, SheetContent, SheetTitle, SheetDescription } from "./Sheet";

export { SkeletonLoader } from "./SkeletonLoader";
export type { SkeletonLoaderProps } from "./SkeletonLoader";

export { EmptyState } from "./EmptyState";
export type { EmptyStateProps } from "./EmptyState";

export { ErrorState } from "./ErrorState";
export type { ErrorStateProps } from "./ErrorState";

export { ListItem } from "./ListItem";
export type { ListItemProps } from "./ListItem";

export { Tabs, TabsList, TabsTrigger, TabsContent } from "./Tabs";

export { TopBar } from "./TopBar";
export type { TopBarProps } from "./TopBar";

export { Switch } from "./Switch";
export type { SwitchProps } from "./Switch";

export { Stepper } from "./Stepper";
export type { StepperProps } from "./Stepper";
