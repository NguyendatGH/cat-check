export interface Page<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface PageRequest {
  page: number;
  pageSize: number;
}
