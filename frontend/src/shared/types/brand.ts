/** Branded type helper — tránh lẫn id giữa các domain (CatId vs ScanId...). */
export type Brand<T, B extends string> = T & { readonly __brand: B };

export type CatId = Brand<string, "CatId">;
export type ScanId = Brand<string, "ScanId">;
export type UserId = Brand<string, "UserId">;
