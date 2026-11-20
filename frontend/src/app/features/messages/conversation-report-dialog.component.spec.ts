import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConversationReportDialogComponent } from './conversation-report-dialog.component';

describe('shared reporting dialog',()=>{
  afterEach(()=>TestBed.resetTestingModule());
  it('requires a written explanation for an auction report',()=>{
    TestBed.configureTestingModule({imports:[ConversationReportDialogComponent]});
    const fixture=TestBed.createComponent(ConversationReportDialogComponent);
    fixture.componentRef.setInput('showReasons',false);fixture.componentRef.setInput('detailsRequired',true);fixture.componentRef.setInput('title','Zgłoś ofertę');fixture.detectChanges();
    const component=fixture.componentInstance as any;const submitted=vi.fn();component.submitReport.subscribe(submitted);
    component.details=' ';component.submit();expect(submitted).not.toHaveBeenCalled();
    component.details='  Opis problemu  ';component.submit();expect(submitted).toHaveBeenCalledWith({reason:'SCAM',details:'Opis problemu'});
    expect(fixture.nativeElement.querySelector('fieldset')).toBeNull();fixture.destroy();
  });
  it('keeps the existing user reporting reasons and prevents dismissal while saving',()=>{
    TestBed.configureTestingModule({imports:[ConversationReportDialogComponent]});
    const fixture=TestBed.createComponent(ConversationReportDialogComponent);fixture.detectChanges();
    const component=fixture.componentInstance as any;const cancel=vi.fn();component.cancel.subscribe(cancel);
    expect(fixture.nativeElement.querySelectorAll('input[type="radio"]').length).toBe(2);
    component.busy=true;document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));expect(cancel).not.toHaveBeenCalled();
    component.busy=false;document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}));expect(cancel).toHaveBeenCalledOnce();fixture.destroy();
  });
});
