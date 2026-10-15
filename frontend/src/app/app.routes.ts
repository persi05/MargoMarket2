import { Routes, UrlMatcher, UrlMatchResult, UrlSegment } from '@angular/router';

import { authGuard } from './core/services/auth.guard';

const messagesMatcher: UrlMatcher = (segments: UrlSegment[]): UrlMatchResult | null => {
  if (segments[0]?.path !== 'messages') return null;
  if (segments.length === 1) return { consumed: segments };
  if (segments.length === 2 && segments[1].path !== 'new') {
    return { consumed: segments, posParams: { conversationId: segments[1] } };
  }
  if (segments.length === 3 && segments[1].path === 'new') {
    return { consumed: segments, posParams: { listingId: segments[2] } };
  }
  return null;
};

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/market/market-page.component').then((m) => m.MarketPageComponent),
    title: 'MargoMarket'
  },
  {
    path: 'auth',
    loadComponent: () => import('./features/auth/auth-page.component').then((m) => m.AuthPageComponent),
    title: 'Logowanie | MargoMarket'
  },
  {
    path: 'new',
    canActivate: [authGuard],
    loadComponent: () => import('./features/listing-form/listing-form-page.component').then((m) => m.ListingFormPageComponent),
    title: 'Nowe ogłoszenie | MargoMarket'
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent),
    title: 'Moje ogłoszenia | MargoMarket'
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile-page.component').then((m) => m.ProfilePageComponent),
    title: 'Profil | MargoMarket'
  },
  {
    path: 'favorites',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent),
    title: 'Obserwowane | MargoMarket'
  },
  {
    matcher: messagesMatcher,
    canActivate: [authGuard],
    loadComponent: () => import('./features/messages/messages-page.component').then((m) => m.MessagesPageComponent),
    title: route => route.paramMap.has('listingId') ? 'Napisz do sprzedającego | MargoMarket'
      : route.paramMap.has('conversationId') ? 'Rozmowa | MargoMarket' : 'Wiadomości | MargoMarket'
  },
  {
    path: 'notifications',
    canActivate: [authGuard],
    loadComponent: () => import('./features/notifications/notifications-page.component').then((m) => m.NotificationsPageComponent),
    title: 'Powiadomienia | MargoMarket'
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    loadComponent: () => import('./features/admin/admin-page.component').then((m) => m.AdminPageComponent),
    title: 'Administracja | MargoMarket'
  },
  {
    path: '**',
    redirectTo: ''
  }
];
