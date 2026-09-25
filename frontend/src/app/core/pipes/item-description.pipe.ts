import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'itemDescription', standalone: true })
export class ItemDescriptionPipe implements PipeTransform {
  transform(description: string | null | undefined, stats: string | null | undefined): string {
    if (!description) {
      return '';
    }

    const created = /(?:^|;)created=(\d+)(?:;|$)/.exec(stats || '');
    if (!created) {
      return description;
    }

    const createdDate = new Date(Number(created[1]) * 1000);
    if (Number.isNaN(createdDate.getTime())) {
      return description;
    }

    const year = createdDate.getUTCFullYear();
    const christmasYear = createdDate.getUTCMonth() === 0 ? year - 1 : year;
    return description
      .replaceAll('#YEAR,-1,M#', String(christmasYear))
      .replaceAll('#YEAR#', String(year));
  }
}
