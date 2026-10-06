import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ApiError } from '../../core/models/api.models';

const EMAIL_ADDRESS_PATTERN = /^[^\s@]+@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,63}$/;

@Component({
  selector: 'mm-auth-page', standalone: true, imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.component.html', styleUrl: './auth-page.component.css'
})
export class AuthPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly pendingKey = 'margomarket.pendingRegistration';
  private readonly pending = this.readPendingRegistration();
  protected mode: 'login' | 'register' | 'verify' = this.initialMode();
  protected loading = false;
  protected error = '';
  protected notice = '';
  private cooldownErrorTimer?: ReturnType<typeof setTimeout>;
  private registrationNoticeTimer?: ReturnType<typeof setTimeout>;
  private readonly passwordsMatch: ValidatorFn = (group) =>
    this.mode === 'register' && group.get('password')?.value !== group.get('confirmPassword')?.value
      ? { passwordMismatch: true } : null;
  protected readonly form = this.fb.nonNullable.group({
    username: [this.pending?.username ?? '', [Validators.required]],
    email: [this.pending?.email ?? ''],
    password: ['', [Validators.required]],
    confirmPassword: [''],
    code: ['']
  }, { validators: this.passwordsMatch });

  constructor() { this.setValidators(); }

  submit(): void {
    if (this.loading) return;
    this.setValidators();
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    this.error = '';
    const { username, email, password, code } = this.form.getRawValue();
    if (this.mode === 'register') {
      this.authService.register({ username, email, password }).pipe(finalize(() => this.loading = false)).subscribe({
        next: () => {
          this.rememberPendingRegistration(username, email);
          this.form.controls.code.reset();
          this.switchMode('verify');
        },
        error: (response: HttpErrorResponse) => this.error = this.errorMessage(response, 'Nie udało się wysłać kodu.')
      });
      return;
    }
    if (this.mode === 'verify') {
      this.authService.verifyEmail({ email, code }).pipe(finalize(() => this.loading = false)).subscribe({
        next: () => {
          this.clearPendingRegistration();
          this.switchMode('login');
          this.notice = 'Konto zostało zarejestrowane poprawnie. Możesz się zalogować.';
          this.registrationNoticeTimer = setTimeout(() => {
            this.notice = '';
            this.registrationNoticeTimer = undefined;
          }, 5000);
          this.form.controls.password.reset();
          this.form.controls.confirmPassword.reset();
          this.form.controls.code.reset();
        },
        error: (response: HttpErrorResponse) => this.error = this.errorMessage(response, 'Nie udało się potwierdzić adresu. Sprawdź kod.')
      });
      return;
    }
    const request$ = this.authService.login({ username, password });
    request$.pipe(finalize(() => this.loading = false)).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(returnUrl?.startsWith('/') && !returnUrl.startsWith('//')
          ? returnUrl : '/');
      },
      error: () => this.error = 'Nie udało się zalogować. Sprawdź nazwę użytkownika i hasło.'
    });
  }

  switchMode(mode: 'login' | 'register' | 'verify'): void {
    clearTimeout(this.cooldownErrorTimer);
    clearTimeout(this.registrationNoticeTimer);
    this.mode = mode;
    this.error = '';
    this.notice = '';
    this.setValidators();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { mode: mode === 'login' ? null : mode },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  resend(): void {
    if (this.loading) return;
    const { username, email, password } = this.form.getRawValue();
    if (!username || !email || !password) {
      this.switchMode('register');
      this.error = 'Aby otrzymać nowy kod, wypełnij ponownie formularz rejestracji.';
      return;
    }
    this.loading = true;
    this.authService.register({ username, email, password }).pipe(finalize(() => this.loading = false)).subscribe({
      next: () => { this.error = ''; this.notice = 'Wysłaliśmy nowy kod. Poprzedni kod już nie działa.'; },
      error: (response: HttpErrorResponse) => this.showResendError(response)
    });
  }

  private showResendError(response: HttpErrorResponse): void {
    this.error = this.errorMessage(response, 'Nie udało się wysłać kodu.');
    clearTimeout(this.cooldownErrorTimer);
    if (!this.error.startsWith('Nowy kod można wysłać za ')) return;
    const message = this.error;
    this.cooldownErrorTimer = setTimeout(() => {
      if (this.error === message) this.error = '';
      this.cooldownErrorTimer = undefined;
    }, 5000);
  }

  private setValidators(): void {
    this.form.controls.username.setValidators(this.mode === 'register'
      ? [Validators.required, Validators.pattern(/^[a-zA-Z0-9_.-]{3,32}$/)]
      : this.mode === 'login' ? [Validators.required] : []);
    this.form.controls.email.setValidators(this.mode === 'login'
      ? [] : [Validators.required, Validators.email, Validators.pattern(EMAIL_ADDRESS_PATTERN)]);
    this.form.controls.password.setValidators(this.mode === 'register'
      ? [Validators.required, Validators.minLength(6), Validators.maxLength(72)]
      : this.mode === 'login' ? [Validators.required] : []);
    this.form.controls.confirmPassword.setValidators(this.mode === 'register' ? [Validators.required] : []);
    this.form.controls.code.setValidators(this.mode === 'verify'
      ? [Validators.required, Validators.pattern(/^[0-9]{6}$/)] : []);
    Object.values(this.form.controls).forEach(control => control.updateValueAndValidity());
    this.form.updateValueAndValidity();
  }

  private initialMode(): 'login' | 'register' | 'verify' {
    const mode = this.route.snapshot.queryParamMap.get('mode');
    if (mode === 'verify') return this.pending ? 'verify' : 'register';
    return mode === 'register' ? 'register' : 'login';
  }

  private readPendingRegistration(): { username: string; email: string } | null {
    try {
      const raw = sessionStorage.getItem(this.pendingKey);
      if (!raw) return null;
      const pending = JSON.parse(raw) as { username?: string; email?: string };
      return typeof pending.username === 'string' && typeof pending.email === 'string'
        ? { username: pending.username, email: pending.email } : null;
    } catch {
      return null;
    }
  }

  private rememberPendingRegistration(username: string, email: string): void {
    try {
      sessionStorage.setItem(this.pendingKey, JSON.stringify({ username, email }));
    } catch {
      // Weryfikacja działa dalej w otwartej karcie, nawet bez pamięci przeglądarki.
    }
  }

  private clearPendingRegistration(): void {
    try {
      sessionStorage.removeItem(this.pendingKey);
    } catch {
      // Brak dostępu do pamięci nie blokuje zakończenia rejestracji.
    }
  }

  private errorMessage(response: HttpErrorResponse, fallback: string): string {
    const body = response.error as ApiError | null;
    if (body?.errors) return Object.values(body.errors).join(' ');
    return body?.message || fallback;
  }
}
