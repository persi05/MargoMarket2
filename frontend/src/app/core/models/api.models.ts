export interface LookupResponse {
  id: number;
  name: string;
}

export interface DictionariesResponse {
  servers: LookupResponse[];
  itemTypes: LookupResponse[];
  rarities: LookupResponse[];
  currencies: LookupResponse[];
}

export interface ItemResponse {
  id: number;
  externalId: number;
  name: string;
  iconUrl: string;
  level: number;
  itemType: LookupResponse;
  rarity: LookupResponse;
  description: string | null;
  stats: string | null;
}

export interface UserResponse {
  id: number;
  email: string;
  role: string;
  createdAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
  user: UserResponse;
}

export interface ListingResponse {
  id: number;
  itemId: number | null;
  itemName: string;
  iconUrl: string | null;
  itemDescription: string | null;
  itemStats: string | null;
  itemType: LookupResponse;
  level: number;
  enhancementLevel: number;
  rarity: LookupResponse;
  price: number;
  currency: LookupResponse;
  server: LookupResponse;
  contact: string;
  status: string;
  sellerId: number;
  sellerEmail: string;
  createdAt: string;
  soldAt: string | null;
}

export interface ListingRequest {
  itemId: number;
  itemName: string;
  itemTypeId: number;
  level: number;
  enhancementLevel: number;
  rarityId: number;
  price: number;
  currencyId: number;
  serverId: number;
  contact: string;
}

export interface ListingFilter {
  search?: string;
  serverId?: number | string;
  itemTypeId?: number | string;
  rarityId?: number | string;
  currencyId?: number | string;
  minLevel?: number | string;
  maxLevel?: number | string;
  status?: string;
  page?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  status: number;
  message: string;
  errors?: Record<string, string>;
  timestamp?: string;
}

export interface UserStats {
  totalListings: number;
  activeListings: number;
  soldListings: number;
}

export interface NotificationResponse {
  id: number;
  type: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface UnreadNotificationsResponse {
  count: number;
}
