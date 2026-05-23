import { HttpErrorResponse } from '@angular/common/http';
import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiError } from '../../core/models/api.models';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';

@Component({
  selector: 'mm-listing-form-page',
  standalone: true,
  imports: [AsyncPipe, ReactiveFormsModule],
  templateUrl: './listing-form-page.component.html',
  styleUrl: './listing-form-page.component.css'
})
export class ListingFormPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  private readonly router = inject(Router);

  protected readonly dictionaries$ = inject(DictionaryService).getAll();
  protected loading = false;
  protected error = '';
  protected readonly serverErrors: Record<string, string> = {};

  protected readonly form = this.fb.nonNullable.group({
    itemName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(60)]],
    itemTypeId: [0, [Validators.required, Validators.min(1)]],
    level: [1, [Validators.required, Validators.min(1), Validators.max(300)]],
    rarityId: [0, [Validators.required, Validators.min(1)]],
    price: [1, [Validators.required, Validators.min(1), Validators.max(2000000000)]],
    currencyId: [0, [Validators.required, Validators.min(1)]],
    serverId: [0, [Validators.required, Validators.min(1)]],
    contact: ['', [Validators.required, Validators.maxLength(50)]]
  });

  submit(): void {
    this.error = '';
    this.clearServerErrors();

    if (this.form.invalid || this.loading) {
      this.form.markAllAsTouched();
      this.error = 'Uzupełnij wymagane pola i popraw zaznaczone błędy.';
      return;
    }

    this.loading = true;

    this.listingService.create(this.form.getRawValue()).pipe(
      finalize(() => {
        this.loading = false;
      })
    ).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: (response: unknown) => {
        this.applyServerErrors(response);
      }
    });
  }

  fieldError(name: string): string {
    if (this.serverErrors[name]) {
      return this.serverErrors[name];
    }

    const control = this.form.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return '';
    }

    if (control.errors['required']) {
      return 'To pole jest wymagane.';
    }

    if (control.errors['minlength']) {
      return `Wpisz co najmniej ${control.errors['minlength'].requiredLength} znaki.`;
    }

    if (control.errors['maxlength']) {
      return `Maksymalnie ${control.errors['maxlength'].requiredLength} znaków.`;
    }

    if (control.errors['min']) {
      return `Wartość musi być co najmniej ${control.errors['min'].min}.`;
    }

    if (control.errors['max']) {
      return `Wartość nie może przekraczać ${control.errors['max'].max}.`;
    }

    return 'Nieprawidłowa wartość.';
  }

  hasFieldError(name: string): boolean {
    return Boolean(this.fieldError(name));
  }

  private applyServerErrors(response: unknown): void {
    const apiError = response instanceof HttpErrorResponse ? response.error as ApiError | null : null;

    if (apiError?.errors) {
      Object.assign(this.serverErrors, apiError.errors);
      Object.keys(apiError.errors).forEach((field) => this.form.get(field)?.markAsTouched());
      this.error = 'Backend odrzucił formularz. Popraw zaznaczone pola.';
      return;
    }

    this.error = apiError?.message || 'Nie udało się dodać ogłoszenia. Sprawdź dane i spróbuj ponownie.';
  }

  private clearServerErrors(): void {
    Object.keys(this.serverErrors).forEach((key) => {
      delete this.serverErrors[key];
    });
  }
}
