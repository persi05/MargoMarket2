import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiError, UserResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'mm-profile-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './profile-page.component.html',
  styleUrl: './profile-page.component.css'
})
export class ProfilePageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected user: UserResponse | null = null;
  protected displayNameSaving = false;
  protected passwordSaving = false;
  protected displayNameError = '';
  protected displayNameNotice = '';
  protected passwordError = '';
  protected passwordNotice = '';

  protected readonly displayNameForm = this.fb.nonNullable.group({
    displayName: ['', [Validators.required, Validators.pattern(/^[\p{L}\p{N}_. -]{3,32}$/u)]],
    currentPassword: ['', Validators.required]
  });
  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
    confirmPassword: ['', Validators.required]
  });

  constructor() {
    this.auth.user$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
      this.user = user;
      if (user && !this.displayNameForm.controls.displayName.dirty) {
        this.displayNameForm.controls.displayName.setValue(user.displayName || user.username);
      }
    });
    this.auth.refreshMe().subscribe({ error: () => { /* Zachowaj dane zapisane w sesji. */ } });
  }

  get initial(): string {
    return (this.user?.displayName || this.user?.username || '?').charAt(0).toLocaleUpperCase('pl-PL');
  }

  saveDisplayName(): void {
    if (this.displayNameSaving) return;
    if (this.displayNameForm.invalid) { this.displayNameForm.markAllAsTouched(); return; }
    this.displayNameSaving = true;
    this.displayNameError = '';
    this.displayNameNotice = '';
    this.auth.changeDisplayName(this.displayNameForm.getRawValue())
      .pipe(finalize(() => this.displayNameSaving = false))
      .subscribe({
        next: user => {
          this.displayNameForm.reset({ displayName: user.displayName, currentPassword: '' });
          this.displayNameNotice = 'Pseudonim został zmieniony.';
        },
        error: (error: HttpErrorResponse) => this.displayNameError = this.errorMessage(error)
      });
  }

  savePassword(): void {
    if (this.passwordSaving) return;
    if (this.passwordForm.invalid) { this.passwordForm.markAllAsTouched(); return; }
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.getRawValue();
    if (newPassword !== confirmPassword) {
      this.passwordForm.controls.confirmPassword.setErrors({ mismatch: true });
      this.passwordForm.controls.confirmPassword.markAsTouched();
      return;
    }
    this.passwordSaving = true;
    this.passwordError = '';
    this.passwordNotice = '';
    this.auth.changePassword({ currentPassword, newPassword })
      .pipe(finalize(() => this.passwordSaving = false))
      .subscribe({
        next: () => {
          this.passwordForm.reset();
          this.passwordNotice = 'Hasło zostało zmienione.';
        },
        error: (error: HttpErrorResponse) => this.passwordError = this.errorMessage(error)
      });
  }

  private errorMessage(response: HttpErrorResponse): string {
    const body = response.error as ApiError | null;
    return body?.errors ? Object.values(body.errors).join(' ') : body?.message || 'Nie udało się zapisać zmian.';
  }
}
