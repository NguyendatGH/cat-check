import { cva, type VariantProps } from "class-variance-authority";

export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-lg font-semibold whitespace-nowrap " +
    "transition-[background-color,border-color,color,box-shadow] duration-[var(--duration-base)] ease-[var(--ease-standard)] " +
    "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)] " +
    "disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        primary: "bg-primary-dark !text-white shadow-brand-lg hover:bg-primary",
        secondary: "bg-secondary text-secondary-text-on shadow-sm hover:bg-secondary-light",
        tertiary: "bg-transparent text-primary border border-border hover:bg-background-alt",
      },
      size: {
        sm: "min-h-[var(--touch-target-min)] px-3 text-caption gap-1.5",
        md: "min-h-[var(--touch-target-min)] px-4 text-body",
        lg: "min-h-13 px-6 text-body",
      },
      loading: {
        true: "relative text-transparent",
        false: "",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
      loading: false,
    },
  },
);

export type ButtonVariantProps = VariantProps<typeof buttonVariants>;
