import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'mm-auth-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.component.html',
  styleUrl: './auth-page.component.css'
})
export class AuthPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected mode: 'login' | 'register' = 'login';
  protected loading = false;
  protected error = '';

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  submit(): void {
    if (this.form.invalid || this.loading) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.error = '';
    const credentials = this.form.getRawValue();
    const request$ = this.mode === 'login'
      ? this.authService.login(credentials)
      : this.authService.register(credentials).pipe(switchMap(() => this.authService.login(credentials)));

    request$.pipe(finalize(() => {
      this.loading = false;
    })).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: () => {
        this.error = this.mode === 'login'
          ? 'Nie udało się zalogować. Sprawdź e-mail i hasło.'
          : 'Nie udało się utworzyć konta. E-mail może być już zajęty.';
      }
    });
  }

  switchMode(mode: 'login' | 'register'): void {
    this.mode = mode;
    this.error = '';
  }
}
