import { Component, EventEmitter, Input, Output } from '@angular/core';

import { PageResponse } from '../../core/models/api.models';

@Component({
  selector: 'mm-pagination',
  standalone: true,
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.css'
})
export class PaginationComponent {
  @Input({ required: true }) page!: PageResponse<unknown>;
  @Input() loading = false;
  @Output() pageChange = new EventEmitter<number>();
}
