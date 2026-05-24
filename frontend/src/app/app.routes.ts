import { Routes } from '@angular/router';

import { authGuard } from './core/services/auth.guard';

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
    path: 'favorites',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard-page.component').then((m) => m.DashboardPageComponent),
    title: 'Obserwowane | MargoMarket'
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
