import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ToastService]
    });
  });

  it('adds a toast and auto-dismisses it', fakeAsync(() => {
    const service = TestBed.inject(ToastService);

    service.success('Saved');

    let items: unknown[] = [];
    service.toasts.subscribe((toasts) => {
      items = toasts;
    });

    expect(items.length).toBe(1);
    expect(items[0]).toEqual(jasmine.objectContaining({
      type: 'success',
      message: 'Saved'
    }));

    tick(4000);
    expect(items.length).toBe(0);
  }));
});
