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
  protected emailErrorVisible = false;
  protected passwordErrorVisible = false;
  protected retryBlocked = false;
  protected error = '';

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  submit(): void {
    if (this.loading || (this.mode === 'register' && this.retryBlocked)) {
      return;
    }

    if (this.mode === 'register') {
      this.emailErrorVisible = this.form.controls.email.invalid;
      this.passwordErrorVisible = this.form.controls.password.invalid;
    }
    if (this.form.invalid) {
      if (this.mode === 'login') {
        this.form.markAllAsTouched();
        this.error = 'Nie udało się zalogować. Sprawdź e-mail i hasło.';
      }
      return;
    }

    this.loading = true;
    this.error = '';
    const submittedMode = this.mode;
    const credentials = this.form.getRawValue();
    const request$ = submittedMode === 'login'
      ? this.authService.login(credentials)
      : this.authService.register(credentials).pipe(switchMap(() => this.authService.login(credentials)));

    request$.pipe(finalize(() => {
      this.loading = false;
    })).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: () => {
        this.error = submittedMode === 'login'
          ? 'Nie udało się zalogować. Sprawdź e-mail i hasło.'
          : 'Nie udało się utworzyć konta. E-mail może być już zajęty.';
        if (submittedMode === 'register') {
          this.retryBlocked = true;
          setTimeout(() => { this.retryBlocked = false; }, 3000);
        }
      }
    });
  }

  switchMode(mode: 'login' | 'register'): void {
    this.mode = mode;
    this.error = '';
    this.emailErrorVisible = false;
    this.passwordErrorVisible = false;
    this.form.controls.password.setValidators([
      Validators.required,
      Validators.minLength(6),
      ...(mode === 'register' ? [Validators.maxLength(72)] : [])
    ]);
    this.form.controls.password.updateValueAndValidity();
  }

  onFieldInput(field: 'email' | 'password'): void {
    if (field === 'email') {
      this.emailErrorVisible = false;
    } else {
      this.passwordErrorVisible = false;
    }
    this.error = '';
  }
}
