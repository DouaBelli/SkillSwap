import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RequestHelpPreview } from './request-help-preview';

describe('RequestHelpPreview', () => {
  let component: RequestHelpPreview;
  let fixture: ComponentFixture<RequestHelpPreview>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RequestHelpPreview],
    }).compileComponents();

    fixture = TestBed.createComponent(RequestHelpPreview);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
